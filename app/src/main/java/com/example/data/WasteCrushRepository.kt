package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.engine.Match3Engine
import com.example.engine.MatchTelemetry
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import android.os.SystemClock
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class MatchRewardOutcome(
    val trophyDelta: Int,
    val coinsEarned: Int,
    val capturedBossId: String?,
    val updatedQuestProgress: Map<String, Int>,
    val newlyCompletedQuestIds: List<String>,
    val newlyPurifiedPlaceId: String? = null
)

data class QuestClaimOutcome(
    val coinsAwarded: Int,
    val rewardedBossId: String,
    val cardsAwarded: Int
)

data class JoinedRoomResult(
    val matchId: String,
    val liveMatch: LiveMatch,
    val edgeServerUrl: String
)

object RoomCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun generate(): String = buildString(6) {
        append("TN")
        repeat(4) { append(ALPHABET.random()) }
    }

    fun normalize(raw: String): String =
        raw.trim().uppercase().filter { it.isLetterOrDigit() }.take(6)

    fun isValid(code: String): Boolean =
        code.length == 6 && code.all { it in 'A'..'Z' || it in '0'..'9' }
}

class WasteCrushRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        context.applicationContext
    )

    companion object {
        private val guestProfileState = MutableStateFlow<UserProfile?>(null)
        private val clansFlow = MutableStateFlow(ClanCatalog.defaultClans)
        private val joinedClanIdFlow = MutableStateFlow<String?>(null)
        private var socialPrefsInitialized = false
        private const val SOCIAL_PREFS_NAME = "wastecrush_social"
        private const val KEY_JOINED_CLAN = "joined_clan_id"
        private const val KEY_CUSTOM_CLANS = "custom_clans_json"
        private const val KEY_CLAN_CHATS = "clan_chats_json"
        private const val GUEST_PREFS_NAME = "wastecrush_guest"
        private const val KEY_GUEST_PROFILE = "guest_profile_json"
        const val GUEST_UID = "guest_local"
        /** Replays recorded in offline guest mode (kept for this app session). */
        private val localReplays = MutableStateFlow<List<MatchReplay>>(emptyList())
        private var localReplaysLoaded = false
        private const val LOCAL_REPLAYS_FILE = "guest_replays.json"
        private const val MAX_LOCAL_REPLAYS = 20
    }

    init {
        initSocialPrefsIfNeeded()
        loadLocalReplaysIfNeeded()
    }

    // --- Offline guest replays: kept in app storage (newest first, last 20) ---

    private fun loadLocalReplaysIfNeeded() {
        val ctx = appContext ?: return
        if (localReplaysLoaded) return
        localReplaysLoaded = true
        try {
            val file = java.io.File(ctx.filesDir, LOCAL_REPLAYS_FILE)
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            localReplays.value = List(arr.length()) { decodeReplay(arr.getJSONObject(it)) }
        } catch (_: Exception) {
        }
    }

    private fun persistLocalReplays() {
        val ctx = appContext ?: return
        try {
            val arr = JSONArray()
            localReplays.value.forEach { arr.put(encodeReplay(it)) }
            java.io.File(ctx.filesDir, LOCAL_REPLAYS_FILE).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun ints(list: List<Int>) = JSONArray(list)
    private fun JSONArray?.toIntList(): List<Int> = if (this == null) emptyList() else List(length()) { optInt(it) }
    private fun JSONArray?.toStrList(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

    private fun encodeSummary(s: ReplaySummary) = JSONObject().apply {
        put("uid", s.uid); put("name", s.name); put("trophies", s.trophies)
        put("deckBossIds", JSONArray(s.deckBossIds)); put("deckBossLevels", ints(s.deckBossLevels))
        put("totalDamage", s.totalDamage); put("restorationPct", s.restorationPct)
    }

    private fun decodeSummary(o: JSONObject?) = if (o == null) ReplaySummary() else ReplaySummary(
        uid = o.optString("uid"), name = o.optString("name"), trophies = o.optInt("trophies"),
        deckBossIds = o.optJSONArray("deckBossIds").toStrList(), deckBossLevels = o.optJSONArray("deckBossLevels").toIntList(),
        totalDamage = o.optInt("totalDamage"), restorationPct = o.optInt("restorationPct")
    )

    private fun encodeReplay(r: MatchReplay) = JSONObject().apply {
        put("id", r.id); put("creatorUid", r.creatorUid); put("participantUids", JSONArray(r.participantUids))
        put("arenaId", r.arenaId); put("placeId", r.placeId); put("bossId", r.bossId)
        put("trophyBracket", r.trophyBracket); put("winnerUid", r.winnerUid); put("durationSec", r.durationSec)
        put("player1Summary", encodeSummary(r.player1Summary)); put("player2Summary", encodeSummary(r.player2Summary))
        put("frames", JSONArray().apply {
            r.frames.forEach { f ->
                put(JSONObject().apply {
                    put("elapsedSec", f.elapsedSec); put("p1Board", ints(f.p1Board)); put("p2Board", ints(f.p2Board))
                    put("p1BossHp", f.p1BossHp); put("p2BossHp", f.p2BossHp); put("p1Wave", f.p1Wave); put("p2Wave", f.p2Wave)
                    put("p1Damage", f.p1Damage); put("p2Damage", f.p2Damage)
                    put("p1Restoration", f.p1Restoration); put("p2Restoration", f.p2Restoration)
                    put("p1Charges", ints(f.p1Charges)); put("p2Charges", ints(f.p2Charges)); put("eventText", f.eventText)
                })
            }
        })
    }

    private fun decodeReplay(o: JSONObject): MatchReplay {
        val fr = o.optJSONArray("frames") ?: JSONArray()
        return MatchReplay(
            id = o.optString("id"), creatorUid = o.optString("creatorUid"),
            participantUids = o.optJSONArray("participantUids").toStrList(),
            arenaId = o.optInt("arenaId"), placeId = o.optString("placeId"), bossId = o.optString("bossId"),
            trophyBracket = o.optInt("trophyBracket"), winnerUid = o.optString("winnerUid"), durationSec = o.optInt("durationSec", 90),
            player1Summary = decodeSummary(o.optJSONObject("player1Summary")),
            player2Summary = decodeSummary(o.optJSONObject("player2Summary")),
            frames = List(fr.length()) { i ->
                val f = fr.getJSONObject(i)
                ReplayFrame(
                    elapsedSec = f.optInt("elapsedSec"), p1Board = f.optJSONArray("p1Board").toIntList(), p2Board = f.optJSONArray("p2Board").toIntList(),
                    p1BossHp = f.optInt("p1BossHp"), p2BossHp = f.optInt("p2BossHp"), p1Wave = f.optInt("p1Wave", 1), p2Wave = f.optInt("p2Wave", 1),
                    p1Damage = f.optInt("p1Damage"), p2Damage = f.optInt("p2Damage"),
                    p1Restoration = f.optInt("p1Restoration"), p2Restoration = f.optInt("p2Restoration"),
                    p1Charges = f.optJSONArray("p1Charges").toIntList(), p2Charges = f.optJSONArray("p2Charges").toIntList(),
                    eventText = f.optString("eventText")
                )
            }
        )
    }

    private fun socialPrefs() =
        appContext?.getSharedPreferences(SOCIAL_PREFS_NAME, Context.MODE_PRIVATE)

    private fun initSocialPrefsIfNeeded() {
        val prefs = socialPrefs() ?: return
        if (socialPrefsInitialized) return
        socialPrefsInitialized = true

        val savedJoinedId = prefs.getString(KEY_JOINED_CLAN, null)
        val customClansJson = prefs.getString(KEY_CUSTOM_CLANS, null)
        val chatsJson = prefs.getString(KEY_CLAN_CHATS, null)

        var mergedClans = ClanCatalog.defaultClans.toMutableList()
        if (!customClansJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(customClansJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val custom = EcoClan(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        tag = obj.getString("tag"),
                        region = obj.getString("region"),
                        motto = obj.getString("motto"),
                        badgeColorHex = obj.optLong("badgeColorHex", 0xFF3CB4FF),
                        minTrophies = obj.optInt("minTrophies", 0),
                        weeklyCleanPoints = obj.optInt("weeklyCleanPoints", 120),
                        weeklyGoalPoints = obj.optInt("weeklyGoalPoints", 5000),
                        members = emptyList(),
                        chatMessages = emptyList()
                    )
                    if (mergedClans.none { it.id == custom.id }) {
                        mergedClans.add(0, custom)
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (!chatsJson.isNullOrBlank()) {
            try {
                val chatObj = JSONObject(chatsJson)
                mergedClans = mergedClans.map { clan ->
                    val msgArr = chatObj.optJSONArray(clan.id) ?: return@map clan
                    val extraMessages = buildList {
                        for (i in 0 until msgArr.length()) {
                            val m = msgArr.getJSONObject(i)
                            add(
                                ClanMessage(
                                    id = m.getString("id"),
                                    senderName = m.getString("senderName"),
                                    senderRole = m.optString("senderRole", "Member"),
                                    text = m.getString("text"),
                                    timestampLabel = m.optString("timestampLabel", "Saved"),
                                    isMe = true
                                )
                            )
                        }
                    }
                    clan.copy(chatMessages = clan.chatMessages + extraMessages)
                }.toMutableList()
            } catch (_: Exception) {
            }
        }

        clansFlow.value = mergedClans
        joinedClanIdFlow.value = savedJoinedId
    }

    fun observeClans(): StateFlow<List<EcoClan>> = clansFlow.asStateFlow()

    fun observeJoinedClanId(): StateFlow<String?> = joinedClanIdFlow.asStateFlow()

    fun joinClan(clanId: String, profile: UserProfile) {
        val currentMember = ClanMember(
            name = profile.displayName,
            role = "Member",
            trophies = profile.trophies,
            friendCode = profile.friendCode,
            leadBossId = profile.equippedDeckIds.firstOrNull() ?: "boss_b1",
            isCurrentUser = true
        )
        clansFlow.value = clansFlow.value.map { clan ->
            val withoutMe = clan.members.filterNot { it.isCurrentUser || it.friendCode == profile.friendCode }
            if (clan.id == clanId) {
                clan.copy(members = listOf(currentMember) + withoutMe)
            } else {
                clan.copy(members = withoutMe)
            }
        }
        joinedClanIdFlow.value = clanId
        socialPrefs()?.edit()?.putString(KEY_JOINED_CLAN, clanId)?.apply()
    }

    fun leaveClan(profile: UserProfile) {
        clansFlow.value = clansFlow.value.map { clan ->
            clan.copy(members = clan.members.filterNot { it.isCurrentUser || it.friendCode == profile.friendCode })
        }
        joinedClanIdFlow.value = null
        socialPrefs()?.edit()?.remove(KEY_JOINED_CLAN)?.apply()
    }

    fun createClan(
        name: String,
        tag: String,
        region: String,
        motto: String,
        profile: UserProfile
    ): EcoClan {
        val cleanName = name.trim().take(28).ifBlank { "${profile.displayName}'s Clan" }
        val cleanTag = tag.trim().uppercase().filter { it.isLetterOrDigit() }.take(4).ifBlank { "ECO" }
        val cleanRegion = region.trim().ifBlank { "Tunisia" }
        val cleanMotto = motto.trim().take(80).ifBlank { "Purifying Tunisia together, one Match-3 combo at a time." }
        val clanId = "clan_custom_${System.currentTimeMillis()}"

        val founder = ClanMember(
            name = profile.displayName,
            role = "Leader",
            trophies = profile.trophies,
            friendCode = profile.friendCode,
            leadBossId = profile.equippedDeckIds.firstOrNull() ?: "boss_b1",
            isCurrentUser = true
        )
        val newClan = EcoClan(
            id = clanId,
            name = cleanName,
            tag = cleanTag,
            region = cleanRegion,
            motto = cleanMotto,
            badgeColorHex = 0xFF18B85B,
            minTrophies = 0,
            weeklyCleanPoints = 150,
            weeklyGoalPoints = 5000,
            members = listOf(founder),
            chatMessages = listOf(
                ClanMessage(
                    id = "welcome_$clanId",
                    senderName = profile.displayName,
                    senderRole = "Leader",
                    text = "Welcome to $cleanName [$cleanTag]! Let's purify $cleanRegion!",
                    timestampLabel = "Just now",
                    isMe = true
                )
            )
        )

        clansFlow.value = listOf(newClan) + clansFlow.value.map { clan ->
            clan.copy(members = clan.members.filterNot { it.isCurrentUser || it.friendCode == profile.friendCode })
        }
        joinedClanIdFlow.value = clanId
        persistCustomClansAndJoined(clanId)
        return newClan
    }

    fun sendClanMessage(text: String, profile: UserProfile) {
        val clean = text.trim()
        val activeId = joinedClanIdFlow.value ?: return
        if (clean.isBlank()) return
        val activeClan = clansFlow.value.firstOrNull { it.id == activeId }
        val myRole = activeClan?.members?.firstOrNull { it.isCurrentUser || it.friendCode == profile.friendCode }?.role ?: "Member"
        val newMsg = ClanMessage(
            id = "msg_${System.currentTimeMillis()}",
            senderName = profile.displayName,
            senderRole = myRole,
            text = clean.take(180),
            timestampLabel = "Now",
            isMe = true
        )
        clansFlow.value = clansFlow.value.map { clan ->
            if (clan.id == activeId) clan.copy(chatMessages = clan.chatMessages + newMsg) else clan
        }
        persistUserMessage(activeId, newMsg)
    }

    private fun persistCustomClansAndJoined(joinedId: String) {
        val prefs = socialPrefs() ?: return
        val customArr = JSONArray()
        clansFlow.value.filter { it.id.startsWith("clan_custom_") }.forEach { clan ->
            customArr.put(
                JSONObject().apply {
                    put("id", clan.id)
                    put("name", clan.name)
                    put("tag", clan.tag)
                    put("region", clan.region)
                    put("motto", clan.motto)
                    put("badgeColorHex", clan.badgeColorHex)
                    put("minTrophies", clan.minTrophies)
                    put("weeklyCleanPoints", clan.weeklyCleanPoints)
                    put("weeklyGoalPoints", clan.weeklyGoalPoints)
                }
            )
        }
        prefs.edit()
            .putString(KEY_JOINED_CLAN, joinedId)
            .putString(KEY_CUSTOM_CLANS, customArr.toString())
            .apply()
    }

    private fun persistUserMessage(clanId: String, message: ClanMessage) {
        val prefs = socialPrefs() ?: return
        val existingJson = prefs.getString(KEY_CLAN_CHATS, null)
        val root = if (existingJson.isNullOrBlank()) JSONObject() else try {
            JSONObject(existingJson)
        } catch (_: Exception) {
            JSONObject()
        }
        val arr = root.optJSONArray(clanId) ?: JSONArray()
        arr.put(
            JSONObject().apply {
                put("id", message.id)
                put("senderName", message.senderName)
                put("senderRole", message.senderRole)
                put("text", message.text)
                put("timestampLabel", message.timestampLabel)
            }
        )
        root.put(clanId, arr)
        prefs.edit().putString(KEY_CLAN_CHATS, root.toString()).apply()
    }

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    /**
     * Local guest mode: no sign-in, no network. Always on when BuildConfig.LOCAL_TEST_MODE is true
     * (debug builds), otherwise used as a fallback when Firebase auth is unavailable.
     */
    fun isGuestMode(): Boolean = BuildConfig.LOCAL_TEST_MODE || auth.currentUser == null

    private fun buildStarterProfile(uid: String, displayName: String, today: String): UserProfile {
        return UserProfile(
            id = uid,
            userId = uid,
            displayName = displayName,
            friendCode = generateFriendCode(uid),
            trophies = 0,
            ecoCoins = 250,
            wins = 0,
            losses = 0,
            equippedDeckIds = listOf("boss_b1", "boss_b2", "boss_b3"),
            bossLevels = mapOf(
                "boss_b1" to 1,
                "boss_b2" to 1,
                "boss_b3" to 1,
                "boss_b4" to 1
            ),
            bossCopies = mapOf(
                "boss_b1" to 3,
                "boss_b2" to 2,
                "boss_b3" to 1,
                "boss_b4" to 0
            ),
            friendUids = emptyList(),
            dailyQuestSeedDate = today,
            questProgress = emptyMap(),
            questClaimed = emptyMap()
        )
    }

    // --- Guest profile (persisted in SharedPreferences so local testing keeps progress) ---

    private fun guestPrefs() = appContext?.getSharedPreferences(GUEST_PREFS_NAME, Context.MODE_PRIVATE)

    private fun ensureGuestProfile(): UserProfile {
        val existing = guestProfileState.value
        if (existing != null) return existing
        val today = todayDateKey()
        val loaded = guestPrefs()?.getString(KEY_GUEST_PROFILE, null)?.let { decodeGuestProfile(it) }
        val created = loaded ?: buildStarterProfile(GUEST_UID, "Tunisia Guest", today)
        val refreshed = resetDailyQuestsIfNeeded(created, today)
        saveGuest(refreshed)
        return refreshed
    }

    private fun saveGuest(profile: UserProfile) {
        guestProfileState.value = profile
        guestPrefs()?.edit()?.putString(KEY_GUEST_PROFILE, encodeGuestProfile(profile))?.apply()
    }

    /** Wipes local guest progress (debug/testing helper). */
    fun resetGuestProfile() {
        localReplays.value = emptyList()
        persistLocalReplays()
        guestPrefs()?.edit()?.remove(KEY_GUEST_PROFILE)?.apply()
        guestProfileState.value = null
        ensureGuestProfile()
    }

    private fun encodeGuestProfile(p: UserProfile): String = JSONObject().apply {
        put("userId", p.userId)
        put("displayName", p.displayName)
        put("friendCode", p.friendCode)
        put("trophies", p.trophies)
        put("ecoCoins", p.ecoCoins)
        put("wins", p.wins)
        put("losses", p.losses)
        put("equippedDeckIds", JSONArray(p.equippedDeckIds))
        put("bossLevels", JSONObject(p.bossLevels as Map<*, *>))
        put("bossCopies", JSONObject(p.bossCopies as Map<*, *>))
        put("friendUids", JSONArray(p.friendUids))
        put("dailyQuestSeedDate", p.dailyQuestSeedDate)
        put("questProgress", JSONObject(p.questProgress as Map<*, *>))
        put("questClaimed", JSONObject(p.questClaimed as Map<*, *>))
        put("avatarId", p.avatarId)
        put("purifiedPlaceIds", JSONArray(p.purifiedPlaceIds))
    }.toString()

    private fun decodeGuestProfile(json: String): UserProfile? = try {
        val o = JSONObject(json)
        fun strList(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return List(a.length()) { a.getString(it) }
        }
        fun intMap(key: String): Map<String, Int> {
            val m = o.optJSONObject(key) ?: return emptyMap()
            return m.keys().asSequence().associateWith { m.optInt(it) }
        }
        fun boolMap(key: String): Map<String, Boolean> {
            val m = o.optJSONObject(key) ?: return emptyMap()
            return m.keys().asSequence().associateWith { m.optBoolean(it) }
        }
        UserProfile(
            id = GUEST_UID,
            userId = GUEST_UID,
            displayName = o.optString("displayName", "Tunisia Guest"),
            friendCode = o.optString("friendCode", generateFriendCode(GUEST_UID)),
            trophies = o.optInt("trophies"),
            ecoCoins = o.optInt("ecoCoins", 250),
            wins = o.optInt("wins"),
            losses = o.optInt("losses"),
            equippedDeckIds = strList("equippedDeckIds").ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
            bossLevels = intMap("bossLevels"),
            bossCopies = intMap("bossCopies"),
            friendUids = strList("friendUids"),
            dailyQuestSeedDate = o.optString("dailyQuestSeedDate", todayDateKey()),
            questProgress = intMap("questProgress"),
            questClaimed = boolMap("questClaimed"),
            avatarId = o.optString("avatarId", AvatarCatalog.DEFAULT_ID),
            purifiedPlaceIds = strList("purifiedPlaceIds")
        )
    } catch (_: Exception) {
        null
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun resetDailyQuestsIfNeeded(profile: UserProfile, today: String): UserProfile {
        if (profile.dailyQuestSeedDate == today) return profile
        val dailyIds = GameCatalog.quests.filter { it.category == QuestCategory.DAILY }.map { it.id }.toSet()
        return profile.copy(
            dailyQuestSeedDate = today,
            questProgress = profile.questProgress.filterKeys { it !in dailyIds },
            questClaimed = profile.questClaimed.filterKeys { it !in dailyIds }
        )
    }

    private fun generateFriendCode(uid: String): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val hash = abs(uid.hashCode())
        val sb = StringBuilder("TN")
        var v = hash
        repeat(4) {
            sb.append(alphabet[v % alphabet.length])
            v = v / alphabet.length + 7
        }
        return sb.toString()
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        if (isGuestMode()) {
            return Result.success(ensureGuestProfile())
        }
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val user = auth.currentUser
            path = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val today = todayDateKey()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid)
                val refreshed = resetDailyQuestsIfNeeded(existing, today)
                if (refreshed !== existing) {
                    docRef.update(
                        mapOf(
                            "dailyQuestSeedDate" to refreshed.dailyQuestSeedDate,
                            "questProgress" to refreshed.questProgress,
                            "questClaimed" to refreshed.questClaimed,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                syncPublicProfile(refreshed)
                Result.success(refreshed)
            } else {
                val displayName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Tunisia Player").take(40)
                val starter = buildStarterProfile(uid, displayName, today)
                docRef.set(starter.toCreateMap()).await()
                syncPublicProfile(starter)
                Result.success(starter)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun syncPublicProfile(profile: UserProfile) {
        val path = "publicProfiles/${profile.userId}"
        try {
            val arena = GameCatalog.arenaForTrophies(profile.trophies)
            val pub = PublicProfile(
                userId = profile.userId,
                displayName = profile.displayName,
                friendCode = profile.friendCode,
                trophies = profile.trophies,
                arenaId = arena.id,
                wins = profile.wins,
                equippedDeckIds = profile.equippedDeckIds,
                publicVisible = true
            )
            db.collection("publicProfiles").document(profile.userId).set(pub.toWriteMap()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        if (isGuestMode()) {
            ensureGuestProfile()
            emitAll(guestProfileState)
            return@flow
        }
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Runs [compute] on a fresh copy of the profile: guest profile in memory, or the Firestore
     * document inside a transaction (so concurrent writes can't overwrite each other).
     * [compute] returns the updated profile, the changed field names, and a result value.
     */
    private suspend fun <T> mutateProfile(
        operation: OperationType,
        compute: (UserProfile) -> Triple<UserProfile, List<String>, T>
    ): Result<T> {
        if (isGuestMode()) {
            return try {
                val (updated, _, value) = compute(ensureGuestProfile())
                saveGuest(updated)
                Result.success(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val ref = db.collection("users").document(uid)
            var updatedProfile: UserProfile? = null
            val value = db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Profile not found.")
                val (updated, fields, v) = compute(current)
                val changes = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                for (f in fields) {
                    changes[f] = when (f) {
                        "trophies" -> updated.trophies
                        "ecoCoins" -> updated.ecoCoins
                        "wins" -> updated.wins
                        "losses" -> updated.losses
                        "bossLevels" -> updated.bossLevels
                        "bossCopies" -> updated.bossCopies
                        "questProgress" -> updated.questProgress
                        "questClaimed" -> updated.questClaimed
                        "equippedDeckIds" -> updated.equippedDeckIds
                        "friendUids" -> updated.friendUids
                        "avatarId" -> updated.avatarId
                        "displayName" -> updated.displayName
                        "purifiedPlaceIds" -> updated.purifiedPlaceIds
                        else -> throw IllegalArgumentException("Unknown field $f")
                    }
                }
                tx.update(ref, changes)
                updatedProfile = updated
                v
            }.await()
            updatedProfile?.let { syncPublicProfile(it) }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operation, path)
            Result.failure(e.cause?.takeIf { it is IllegalArgumentException || it is IllegalStateException } ?: e)
        }
    }

    suspend fun updateAvatar(avatarId: String): Result<Unit> =
        mutateProfile(OperationType.UPDATE) { p ->
            val avatar = AvatarCatalog.all.firstOrNull { it.id == avatarId }
                ?: throw IllegalArgumentException("Unknown avatar.")
            require(avatar.isUnlocked(p)) { "Locked: ${avatar.requirementText()}." }
            Triple(p.copy(avatarId = avatarId), listOf("avatarId"), Unit)
        }

    suspend fun updateDisplayName(rawName: String): Result<String> =
        mutateProfile(OperationType.UPDATE) { p ->
            val name = rawName.trim().replace(Regex("\\s+"), " ")
            require(name.length in 3..20) { "Name must be 3 to 20 characters." }
            require(name.all { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }) {
                "Use letters, digits, spaces, _ or -."
            }
            Triple(p.copy(displayName = name), listOf("displayName"), name)
        }

    suspend fun updateEquippedDeck(newDeckIds: List<String>): Result<Unit> =
        mutateProfile(OperationType.UPDATE) { p ->
            require(newDeckIds.size == 3 && newDeckIds.distinct().size == 3) { "Eco-Deck must contain 3 distinct Bosses." }
            require(newDeckIds.all { (p.bossLevels[it] ?: 0) >= 1 }) { "Capture this Boss first to unlock it." }
            Triple(p.copy(equippedDeckIds = newDeckIds), listOf("equippedDeckIds"), Unit)
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun upgradeBoss(profile: UserProfile, bossId: String): Result<Int> =
        mutateProfile(OperationType.UPDATE) { p ->
            val (updated, newLevel) = RewardRules.upgradeBoss(p, bossId)
            Triple(updated, listOf("bossLevels", "bossCopies", "ecoCoins"), newLevel)
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun applyMatchResult(profile: UserProfile, telemetry: MatchTelemetry): Result<MatchRewardOutcome> =
        mutateProfile(OperationType.UPDATE) { p ->
            val (updated, outcome) = RewardRules.matchResult(p, telemetry)
            Triple(updated, listOf("trophies", "ecoCoins", "wins", "losses", "bossLevels", "bossCopies", "questProgress", "purifiedPlaceIds"), outcome)
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun claimQuestReward(profile: UserProfile, quest: QuestDefinition): Result<QuestClaimOutcome> =
        mutateProfile(OperationType.UPDATE) { p ->
            val (updated, outcome) = RewardRules.claimQuest(p, quest)
            Triple(updated, listOf("ecoCoins", "bossLevels", "bossCopies", "questClaimed"), outcome)
        }

    // --- Friends & Public Profiles ---
    suspend fun addFriendByCode(profile: UserProfile, rawCode: String): Result<PublicProfile> {
        val code = rawCode.trim().uppercase()
        if (isGuestMode()) {
            return try {
                require(code.length in 6..8) { "Enter a valid 6-character Friend Code (e.g. TN7A2K)." }
                val found = SocialSeedCatalog.communityProfiles.firstOrNull { it.friendCode == code }
                    ?: return Result.failure(IllegalArgumentException("No player found with Friend Code $code."))
                val current = ensureGuestProfile()
                require(found.userId != current.userId) { "You cannot add your own Friend Code." }
                if (found.userId !in current.friendUids) {
                    saveGuest(current.copy(friendUids = (current.friendUids + found.userId).take(50)))
                }
                Result.success(found)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "publicProfiles"
        return try {
            require(code.length in 6..8) { "Enter a valid 6-character Friend Code (e.g. TN4K9P)." }
            val snap = db.collection("publicProfiles")
                .whereEqualTo("publicVisible", true)
                .whereEqualTo("friendCode", code)
                .limit(1)
                .get()
                .await()
            val found = snap.documents.firstOrNull()
                ?.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: SocialSeedCatalog.communityProfiles.firstOrNull { it.friendCode == code }
                ?: return Result.failure(IllegalArgumentException("No player found with Friend Code $code."))
            require(found.userId != uid) { "You cannot add your own Friend Code." }
            require(profile.friendUids.size < 50 || found.userId in profile.friendUids) { "Friend list is full (50)." }
            // arrayUnion is atomic: no lost update if two adds race.
            db.collection("users").document(uid).update(
                mapOf(
                    "friendUids" to FieldValue.arrayUnion(found.userId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(found)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(profile: UserProfile, friendUid: String): Result<Unit> {
        if (isGuestMode()) {
            val current = ensureGuestProfile()
            saveGuest(current.copy(friendUids = current.friendUids.filterNot { it == friendUid }))
            return Result.success(Unit)
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "friendUids" to FieldValue.arrayRemove(friendUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = flow {
        if (isGuestMode()) {
            emit(SocialSeedCatalog.communityProfiles)
            return@flow
        }
        val uid = requireUserId()
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("publicVisible", true)
                .limit(80)
                .snapshots()
                .map { snap ->
                    val remote = snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    val remoteIds = remote.map { it.userId }.toSet()
                    remote + SocialSeedCatalog.communityProfiles.filter { it.userId !in remoteIds }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Matchmaking & Live Matches ---
    suspend fun enqueueForMatchmaking(profile: UserProfile, arenaId: Int): Result<MatchmakingTicket?> {
        if (isGuestMode()) return Result.failure(IllegalStateException("Offline guest mode: online matchmaking disabled."))
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "matchmakingQueue"
        return try {
            // Look for an existing searching player near our Trophy count
            val waitingSnap = db.collection("matchmakingQueue")
                .whereEqualTo("status", "SEARCHING")
                .limit(20)
                .get()
                .await()
            val candidate = waitingSnap.toObjects(MatchmakingTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.userId != uid && it.arenaId == arenaId && abs(it.trophies - profile.trophies) <= 300 }
                .minByOrNull { abs(it.trophies - profile.trophies) }

            if (candidate != null) {
                return Result.success(candidate)
            }

            // Otherwise publish our own searching ticket
            val levels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }
            val myTicket = MatchmakingTicket(
                userId = uid,
                displayName = profile.displayName,
                trophies = profile.trophies,
                arenaId = arenaId,
                deckBossIds = profile.equippedDeckIds,
                deckBossLevels = levels,
                status = "SEARCHING",
                matchId = ""
            )
            db.collection("matchmakingQueue").document(uid).set(myTicket.toCreateMap()).await()
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMyQueueTicket(): Flow<MatchmakingTicket?> = flow {
        if (isGuestMode()) {
            emit(null)
            return@flow
        }
        val uid = requireUserId()
        val path = "matchmakingQueue/$uid"
        emitAll(
            db.collection("matchmakingQueue").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(MatchmakingTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Atomically checks that the opponent is still SEARCHING, creates the live match and claims
     * their ticket. Fails (and writes nothing) if someone else claimed it or they left the queue.
     */
    suspend fun createMatchAndClaimTicket(match: LiveMatch, opponentUid: String): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "matchmakingQueue/$opponentUid"
        return try {
            val matchRef = db.collection("liveMatches").document()
            val ticketRef = db.collection("matchmakingQueue").document(opponentUid)
            db.runTransaction { tx ->
                val ticket = tx.get(ticketRef)
                if (!ticket.exists() || ticket.getString("status") != "SEARCHING") {
                    throw IllegalStateException("Opponent is no longer searching.")
                }
                tx.set(matchRef, match.copy(creatorUid = uid).toCreateMap())
                tx.update(
                    ticketRef,
                    mapOf(
                        "status" to "MATCHED",
                        "matchId" to matchRef.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(matchRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveMatchmakingQueue(): Result<Unit> {
        if (isGuestMode()) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "matchmakingQueue/$uid"
        return try {
            db.collection("matchmakingQueue").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createLiveMatch(match: LiveMatch): Result<String> {
        if (isGuestMode()) return Result.failure(IllegalStateException("Offline guest mode"))
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "liveMatches"
        return try {
            val docRef = db.collection("liveMatches").document()
            path = docRef.path
            val toWrite = match.copy(creatorUid = uid)
            docRef.set(toWrite.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeLiveMatch(matchId: String): Flow<LiveMatch?> = flow {
        if (isGuestMode() || matchId.startsWith("local_")) {
            emit(null)
            return@flow
        }
        val uid = requireUserId()
        val path = "liveMatches/$matchId"
        emitAll(
            db.collection("liveMatches").document(matchId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(LiveMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateLiveMatchPlayers(
        matchId: String,
        status: String,
        winnerUid: String,
        player1: PlayerLiveState?,
        player2: PlayerLiveState?
    ): Result<Unit> {
        if (isGuestMode() || matchId.startsWith("local_")) return Result.success(Unit)
        val path = "liveMatches/$matchId"
        return try {
            requireUserId()
            val map = mutableMapOf<String, Any>(
                "status" to status,
                "winnerUid" to winnerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (player1 != null) map["player1"] = player1.toMap()
            if (player2 != null) map["player2"] = player2.toMap()
            db.collection("liveMatches").document(matchId).update(map).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Measures genuine round-trip latency against Firebase Firestore using a server-only get().
     * Returns null in guest mode or when offline; never fabricates latency values.
     */
    suspend fun measureFirestoreRttMs(matchId: String): Int? {
        if (isGuestMode() || matchId.isBlank() || matchId.startsWith("local_")) return null
        return try {
            val startNanos = SystemClock.elapsedRealtimeNanos()
            db.collection("liveMatches").document(matchId).get(Source.SERVER).await()
            ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // --- Private 1v1 Room Code Lobbies ---

    suspend fun createPrivateRoom(
        profile: UserProfile,
        arenaId: Int,
        edgeServerUrl: String
    ): Result<PrivateRoom> {
        if (isGuestMode()) {
            return Result.failure(IllegalStateException("Sign in with Google to host an online Private Room."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val levels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }
        val cleanEdge = edgeServerUrl.trim().take(200)

        repeat(4) {
            val code = RoomCodeGenerator.generate()
            val path = "privateRooms/$code"
            try {
                val docRef = db.collection("privateRooms").document(code)
                val existing = docRef.get().await()
                if (existing.exists() && existing.getString("status") == "WAITING") {
                    return@repeat
                }
                val room = PrivateRoom(
                    id = code,
                    roomCode = code,
                    hostUid = uid,
                    hostName = profile.displayName,
                    hostTrophies = profile.trophies,
                    hostDeckBossIds = profile.equippedDeckIds,
                    hostDeckBossLevels = levels,
                    arenaId = arenaId.coerceIn(0, 2),
                    edgeServerUrl = cleanEdge,
                    status = "WAITING",
                    guestUid = "",
                    matchId = ""
                )
                docRef.set(room.toCreateMap()).await()
                return Result.success(room)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, path)
                return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("Unable to allocate a room code. Please try again."))
    }

    fun observePrivateRoom(roomCode: String): Flow<PrivateRoom?> = flow {
        val code = RoomCodeGenerator.normalize(roomCode)
        if (isGuestMode() || !RoomCodeGenerator.isValid(code)) {
            emit(null)
            return@flow
        }
        val uid = requireUserId()
        val path = "privateRooms/$code"
        emitAll(
            db.collection("privateRooms").document(code)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(PrivateRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun joinPrivateRoom(
        profile: UserProfile,
        rawRoomCode: String
    ): Result<JoinedRoomResult> {
        if (isGuestMode()) {
            return Result.failure(IllegalStateException("Sign in with Google to join an online Private Room."))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val code = RoomCodeGenerator.normalize(rawRoomCode)
        if (!RoomCodeGenerator.isValid(code)) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-character Room Code (e.g. TN4K9P)."))
        }
        val roomRef = db.collection("privateRooms").document(code)
        val matchRef = db.collection("liveMatches").document()
        val myLevels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }
        val p1Board = Match3Engine.createInitialBoard()
        val p2Board = Match3Engine.createInitialBoard()

        return try {
            var createdMatch: LiveMatch? = null
            var sharedEdgeUrl = ""
            db.runTransaction { tx ->
                val snap = tx.get(roomRef)
                if (!snap.exists()) {
                    throw IllegalArgumentException("Room $code not found. Check the code and try again.")
                }
                val room = snap.toObject(PrivateRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Invalid room data.")
                if (room.status != "WAITING") {
                    throw IllegalStateException("Room $code is already matched or closed.")
                }
                if (room.hostUid == uid) {
                    throw IllegalArgumentException("You cannot join your own room from the same account.")
                }

                val arena = GameCatalog.arenaById(room.arenaId)
                val chosenPlace = arena.places.random()
                val chosenBoss = GameCatalog.cumulativeBossPoolForArena(arena.id).random()
                val scaledBossHp = com.example.engine.BattleRules.scaledBossHp(chosenBoss.bossBattleHp)

                val p1 = PlayerLiveState(
                    uid = room.hostUid,
                    name = room.hostName,
                    trophies = room.hostTrophies,
                    deckBossIds = room.hostDeckBossIds,
                    deckBossLevels = room.hostDeckBossLevels,
                    waveIndex = 1,
                    bossHp = scaledBossHp,
                    bossMaxHp = scaledBossHp,
                    totalDamage = 0,
                    restorationPct = 0,
                    boardFlat = Match3Engine.encodeBoardFlat(p1Board),
                    skillCharges = listOf(0, 0, 0),
                    lastAbility = ""
                )
                val p2 = PlayerLiveState(
                    uid = uid,
                    name = profile.displayName,
                    trophies = profile.trophies,
                    deckBossIds = profile.equippedDeckIds,
                    deckBossLevels = myLevels,
                    waveIndex = 1,
                    bossHp = scaledBossHp,
                    bossMaxHp = scaledBossHp,
                    totalDamage = 0,
                    restorationPct = 0,
                    boardFlat = Match3Engine.encodeBoardFlat(p2Board),
                    skillCharges = listOf(0, 0, 0),
                    lastAbility = ""
                )
                val liveMatch = LiveMatch(
                    id = matchRef.id,
                    creatorUid = uid,
                    participantUids = listOf(room.hostUid, uid),
                    arenaId = arena.id,
                    placeId = chosenPlace.id,
                    bossId = chosenBoss.id,
                    trophyBracket = arena.id,
                    status = "ACTIVE",
                    durationSec = com.example.engine.BattleRules.MATCH_DURATION_SEC,
                    spectatorCount = 0,
                    botMatch = false,
                    winnerUid = "",
                    player1 = p1,
                    player2 = p2
                )
                tx.set(matchRef, liveMatch.toCreateMap())
                tx.update(
                    roomRef,
                    mapOf(
                        "status" to "MATCHED",
                        "guestUid" to uid,
                        "matchId" to matchRef.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                createdMatch = liveMatch
                sharedEdgeUrl = room.edgeServerUrl
                null
            }.await()

            val match = createdMatch ?: throw IllegalStateException("Failed to initialize live match.")
            Result.success(JoinedRoomResult(matchId = matchRef.id, liveMatch = match, edgeServerUrl = sharedEdgeUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "privateRooms/$code")
            Result.failure(e.cause?.takeIf { it is IllegalArgumentException || it is IllegalStateException } ?: e)
        }
    }

    suspend fun cancelPrivateRoom(roomCode: String): Result<Unit> {
        val code = RoomCodeGenerator.normalize(roomCode)
        if (isGuestMode() || !RoomCodeGenerator.isValid(code)) return Result.success(Unit)
        return try {
            db.collection("privateRooms").document(code).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "privateRooms/$code")
            Result.failure(e)
        }
    }

    // --- Replays ---
    suspend fun saveReplay(replay: MatchReplay): Result<String> {
        if (isGuestMode()) {
            val id = "local_replay_${System.currentTimeMillis()}"
            localReplays.value = (listOf(replay.copy(id = id, creatorUid = GUEST_UID)) + localReplays.value).take(MAX_LOCAL_REPLAYS)
            persistLocalReplays()
            return Result.success(id)
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "replays"
        return try {
            val docRef = db.collection("replays").document()
            path = docRef.path
            val toWrite = replay.copy(creatorUid = uid, replayPublic = true)
            docRef.set(toWrite.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observePublicReplays(): Flow<List<MatchReplay>> = flow {
        if (isGuestMode()) {
            emitAll(localReplays)
            return@flow
        }
        val uid = requireUserId()
        val path = "replays"
        emitAll(
            db.collection("replays")
                .whereEqualTo("replayPublic", true)
                .limit(45)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MatchReplay::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getReplayById(replayId: String): Result<MatchReplay?> {
        localReplays.value.firstOrNull { it.id == replayId }?.let { return Result.success(it) }
        if (isGuestMode()) return Result.success(null)
        val path = "replays/$replayId"
        return try {
            requireUserId()
            val snap = db.collection("replays").document(replayId).get().await()
            val replay = snap.toObject(MatchReplay::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(replay)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}
