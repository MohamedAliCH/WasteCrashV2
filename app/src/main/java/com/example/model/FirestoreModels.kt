package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Eco Challenger",
    val friendCode: String = "TN0000",
    val trophies: Int = 0,
    val ecoCoins: Int = 250,
    val wins: Int = 0,
    val losses: Int = 0,
    val equippedDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val bossLevels: Map<String, Int> = mapOf("boss_b1" to 1, "boss_b2" to 1, "boss_b3" to 1, "boss_b4" to 1),
    val bossCopies: Map<String, Int> = mapOf("boss_b1" to 2, "boss_b2" to 1, "boss_b3" to 1, "boss_b4" to 0),
    val friendUids: List<String> = emptyList(),
    val dailyQuestSeedDate: String = "2026-11-21",
    val questProgress: Map<String, Int> = emptyMap(),
    val questClaimed: Map<String, Boolean> = emptyMap(),
    /** Chosen avatar (see AvatarCatalog); unlocks are derived from wins, trophies and captured bosses. */
    val avatarId: String = "av_sachet",
    /** Tunisian landmark IDs purified by winning a match at that place (unlocks map & album cards). */
    val purifiedPlaceIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "avatarId" to avatarId.take(40).ifBlank { "av_sachet" },
        "userId" to userId,
        "displayName" to displayName.take(40).ifBlank { "Eco Challenger" },
        "friendCode" to friendCode,
        "trophies" to trophies.coerceAtLeast(0),
        "ecoCoins" to ecoCoins.coerceAtLeast(0),
        "wins" to wins.coerceAtLeast(0),
        "losses" to losses.coerceAtLeast(0),
        "equippedDeckIds" to equippedDeckIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "bossLevels" to bossLevels,
        "bossCopies" to bossCopies,
        "friendUids" to friendUids.take(50),
        "dailyQuestSeedDate" to dailyQuestSeedDate,
        "questProgress" to questProgress,
        "questClaimed" to questClaimed,
        "purifiedPlaceIds" to purifiedPlaceIds.distinct().take(20),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Eco Challenger",
    val friendCode: String = "TN0000",
    val trophies: Int = 0,
    val arenaId: Int = 0,
    val wins: Int = 0,
    val equippedDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val publicVisible: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWriteMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(40).ifBlank { "Eco Challenger" },
        "friendCode" to friendCode,
        "trophies" to trophies.coerceAtLeast(0),
        "arenaId" to arenaId.coerceIn(0, 2),
        "wins" to wins.coerceAtLeast(0),
        "equippedDeckIds" to equippedDeckIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "publicVisible" to true,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class MatchmakingTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val trophies: Int = 0,
    val arenaId: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val status: String = "SEARCHING",
    val matchId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(40).ifBlank { "Eco Challenger" },
        "trophies" to trophies.coerceAtLeast(0),
        "arenaId" to arenaId.coerceIn(0, 2),
        "deckBossIds" to deckBossIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "deckBossLevels" to deckBossLevels.take(3).ifEmpty { listOf(1, 1, 1) },
        "status" to status,
        "matchId" to matchId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PlayerLiveState(
    val uid: String = "",
    val name: String = "",
    val trophies: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val waveIndex: Int = 1,
    val bossHp: Int = 1500,
    val bossMaxHp: Int = 1500,
    val totalDamage: Int = 0,
    val restorationPct: Int = 0,
    val boardFlat: List<Int> = emptyList(),
    val skillCharges: List<Int> = listOf(0, 0, 0),
    val lastAbility: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid.ifBlank { "rival_uid" },
        "name" to name.take(40).ifBlank { "Challenger" },
        "trophies" to trophies.coerceAtLeast(0),
        "deckBossIds" to deckBossIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "deckBossLevels" to deckBossLevels.take(3).ifEmpty { listOf(1, 1, 1) },
        "waveIndex" to waveIndex.coerceIn(0, 10),
        "bossHp" to bossHp.coerceAtLeast(0),
        "bossMaxHp" to bossMaxHp.coerceAtLeast(1),
        "totalDamage" to totalDamage.coerceAtLeast(0),
        "restorationPct" to restorationPct.coerceIn(0, 100),
        "boardFlat" to boardFlat.take(49),
        "skillCharges" to skillCharges.take(3),
        "lastAbility" to lastAbility.take(80)
    )
}

@Keep
@IgnoreExtraProperties
data class LiveMatch(
    @DocumentId val id: String = "",
    val creatorUid: String = "",
    val participantUids: List<String> = emptyList(),
    val arenaId: Int = 0,
    val placeId: String = "place_hammamet",
    val bossId: String = "boss_b1",
    val trophyBracket: Int = 0,
    val status: String = "ACTIVE",
    val durationSec: Int = 90,
    val spectatorCount: Int = 0,
    val botMatch: Boolean = false,
    val winnerUid: String = "",
    val player1: PlayerLiveState = PlayerLiveState(),
    val player2: PlayerLiveState = PlayerLiveState(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "creatorUid" to creatorUid,
        "participantUids" to participantUids.take(2),
        "arenaId" to arenaId.coerceIn(0, 2),
        "placeId" to placeId,
        "bossId" to bossId,
        "trophyBracket" to trophyBracket.coerceIn(0, 2),
        "status" to status,
        "durationSec" to durationSec.coerceIn(30, 300),
        "spectatorCount" to spectatorCount.coerceAtLeast(0),
        "botMatch" to botMatch,
        "winnerUid" to winnerUid,
        "player1" to player1.toMap(),
        "player2" to player2.toMap(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ReplaySummary(
    val uid: String = "",
    val name: String = "",
    val trophies: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val totalDamage: Int = 0,
    val restorationPct: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid.ifBlank { "rival_uid" },
        "name" to name.take(40).ifBlank { "Challenger" },
        "trophies" to trophies.coerceAtLeast(0),
        "deckBossIds" to deckBossIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "deckBossLevels" to deckBossLevels.take(3).ifEmpty { listOf(1, 1, 1) },
        "totalDamage" to totalDamage.coerceAtLeast(0),
        "restorationPct" to restorationPct.coerceIn(0, 100)
    )
}

@Keep
@IgnoreExtraProperties
data class ReplayFrame(
    val elapsedSec: Int = 0,
    val p1Board: List<Int> = emptyList(),
    val p2Board: List<Int> = emptyList(),
    val p1BossHp: Int = 1500,
    val p2BossHp: Int = 1500,
    val p1Wave: Int = 1,
    val p2Wave: Int = 1,
    val p1Damage: Int = 0,
    val p2Damage: Int = 0,
    val p1Restoration: Int = 0,
    val p2Restoration: Int = 0,
    val p1Charges: List<Int> = listOf(0, 0, 0),
    val p2Charges: List<Int> = listOf(0, 0, 0),
    val eventText: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "elapsedSec" to elapsedSec,
        "p1Board" to p1Board.take(49),
        "p2Board" to p2Board.take(49),
        "p1BossHp" to p1BossHp.coerceAtLeast(0),
        "p2BossHp" to p2BossHp.coerceAtLeast(0),
        "p1Wave" to p1Wave.coerceIn(0, 10),
        "p2Wave" to p2Wave.coerceIn(0, 10),
        "p1Damage" to p1Damage.coerceAtLeast(0),
        "p2Damage" to p2Damage.coerceAtLeast(0),
        "p1Restoration" to p1Restoration.coerceIn(0, 100),
        "p2Restoration" to p2Restoration.coerceIn(0, 100),
        "p1Charges" to p1Charges.take(3),
        "p2Charges" to p2Charges.take(3),
        "eventText" to eventText.take(100)
    )
}

@Keep
@IgnoreExtraProperties
data class MatchReplay(
    @DocumentId val id: String = "",
    val creatorUid: String = "",
    val participantUids: List<String> = emptyList(),
    val replayPublic: Boolean = true,
    val arenaId: Int = 0,
    val placeId: String = "place_hammamet",
    val bossId: String = "boss_b1",
    val trophyBracket: Int = 0,
    val winnerUid: String = "",
    val durationSec: Int = 90,
    val player1Summary: ReplaySummary = ReplaySummary(),
    val player2Summary: ReplaySummary = ReplaySummary(),
    val frames: List<ReplayFrame> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "creatorUid" to creatorUid,
        "participantUids" to participantUids.take(2),
        "replayPublic" to true,
        "arenaId" to arenaId.coerceIn(0, 2),
        "placeId" to placeId,
        "bossId" to bossId,
        "trophyBracket" to trophyBracket.coerceIn(0, 2),
        "winnerUid" to winnerUid.ifBlank { creatorUid },
        "durationSec" to durationSec.coerceIn(1, 300),
        "player1Summary" to player1Summary.toMap(),
        "player2Summary" to player2Summary.toMap(),
        "frames" to frames.take(45).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PrivateRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val hostTrophies: Int = 0,
    val hostDeckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val hostDeckBossLevels: List<Int> = listOf(1, 1, 1),
    val arenaId: Int = 0,
    val edgeServerUrl: String = "",
    val status: String = "WAITING",
    val guestUid: String = "",
    val matchId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomCode" to roomCode.uppercase().take(8),
        "hostUid" to hostUid,
        "hostName" to hostName.take(40).ifBlank { "Eco Challenger" },
        "hostTrophies" to hostTrophies.coerceAtLeast(0),
        "hostDeckBossIds" to hostDeckBossIds.take(3).ifEmpty { listOf("boss_b1", "boss_b2", "boss_b3") },
        "hostDeckBossLevels" to hostDeckBossLevels.take(3).ifEmpty { listOf(1, 1, 1) },
        "arenaId" to arenaId.coerceIn(0, 2),
        "edgeServerUrl" to edgeServerUrl.trim().take(200),
        "status" to "WAITING",
        "guestUid" to "",
        "matchId" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
