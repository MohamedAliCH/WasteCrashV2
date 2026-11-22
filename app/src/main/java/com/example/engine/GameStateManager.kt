package com.example.engine

import com.example.data.MatchRewardOutcome
import com.example.data.SettingsStore
import com.example.data.WasteCrushRepository
import com.example.model.*
import com.example.network.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class SessionSyncStatus {
    IDLE,
    SYNCING,
    LIVE_SYNCED,
    LOCAL_FALLBACK
}

data class DamageProjectileVolley(
    val id: Long,
    val origins: List<Pair<GridPos, WasteType>>,
    val damageAmount: Int,
    val isCritical: Boolean,
    val isWeakHit: Boolean,
    val isAbility: Boolean,
    val comboIndex: Int,
    val abilityWasteType: WasteType? = null,
    val createdAtMs: Long
)

/** One mini trash monster of the current wave. */
data class MobState(
    val id: String,
    val hp: Int,
    val maxHp: Int
) {
    val alive: Boolean get() = hp > 0
}

data class PlayerBattleSnapshot(
    val uid: String = "",
    val name: String = "Challenger",
    val trophies: Int = 0,
    val deckBossIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val deckBossLevels: List<Int> = listOf(1, 1, 1),
    val board: List<List<BoardTile>> = Match3Engine.DEFAULT_BOARD,
    val boardFlat: List<Int> = Match3Engine.DEFAULT_BOARD_FLAT,
    val waveIndex: Int = 1, // 1..4 = Mini-Mob Waves (2,3,3,4 minis), 5 = Shared Boss
    /** Mini monsters of the current wave (empty during the Boss stage). */
    val waveMobs: List<MobState> = emptyList(),
    val currentEnemyHp: Int = 620,
    val currentEnemyMaxHp: Int = 620,
    val bossHp: Int = 2325,
    val bossMaxHp: Int = 2325,
    val totalDamage: Int = 0,
    /** Environmental pollution level: 1.0f = heavily polluted, 0.0f = completely purified. */
    val pollutionLevel: Float = 1.0f,
    /** Environmental restoration percentage: 0% polluted -> 100% completely clean. */
    val restorationPct: Int = 0,
    val skillCharges: List<Int> = listOf(0, 0, 0),
    val lastAbilityText: String = "",
    val lastUpdatedMs: Long = 0L
) {
    fun toPlayerLiveState(): PlayerLiveState = PlayerLiveState(
        uid = uid.ifBlank { "player_uid" },
        name = name,
        trophies = trophies,
        deckBossIds = deckBossIds,
        deckBossLevels = deckBossLevels,
        waveIndex = waveIndex,
        bossHp = bossHp,
        bossMaxHp = bossMaxHp,
        totalDamage = totalDamage,
        restorationPct = restorationPct,
        boardFlat = boardFlat,
        skillCharges = skillCharges,
        lastAbility = lastAbilityText
    )
}

data class GameSessionConfig(
    val matchId: String,
    val profile: UserProfile,
    val arena: ArenaDefinition,
    val place: TunisianPlace,
    val sharedBoss: BossDefinition,
    val amIPlayer1: Boolean,
    val isBotMatch: Boolean,
    val initialLocalBoard: List<List<BoardTile>>,
    val initialOpponentBoard: List<List<BoardTile>>,
    val opponentUid: String,
    val opponentName: String,
    val opponentTrophies: Int,
    val opponentDeckIds: List<String>,
    val opponentDeckLevels: List<Int>,
    val durationSec: Int = BattleRules.MATCH_DURATION_SEC,
    /** Mini mob ids for waves 1..4. Empty = derived from the match id (same for both players). */
    val waves: List<List<String>> = emptyList(),
    /** Optional Edge / MEC WebSocket URL for sub-25ms 5G SA state streaming. */
    val edgeServerUrl: String = ""
)

data class GameSessionState(
    val isActive: Boolean = false,
    val isFinished: Boolean = false,
    val matchId: String = "",
    val replayId: String? = null,
    val arenaId: Int = 0,
    val placeId: String = "place_hammamet",
    val sharedBossId: String = "boss_b1",
    val amIPlayer1: Boolean = true,
    val isBotMatch: Boolean = false,
    val secondsRemaining: Int = BattleRules.MATCH_DURATION_SEC,
    val totalDurationSec: Int = BattleRules.MATCH_DURATION_SEC,
    val spectatorCount: Int = 0,
    val syncStatus: SessionSyncStatus = SessionSyncStatus.IDLE,
    val lastCloudSyncMs: Long = 0L,
    val localPlayer: PlayerBattleSnapshot = PlayerBattleSnapshot(),
    val opponentPlayer: PlayerBattleSnapshot = PlayerBattleSnapshot(name = "Rival"),
    val selectedTile: GridPos? = null,
    val isBoardAnimating: Boolean = false,
    val invalidSwapPair: Pair<GridPos, GridPos>? = null,
    val invalidSwapToken: Long = 0L,
    val popBurstTiles: List<Pair<GridPos, WasteType>> = emptyList(),
    val popBurstToken: Long = 0L,
    val hintPair: Pair<GridPos, GridPos>? = null,
    val comboFlashLevel: Int = 0,
    val hitFlashToken: Long = 0L,
    val lastDamageDealt: Int = 0,
    val comboBannerText: String = "",
    val projectileVolleys: List<DamageProjectileVolley> = emptyList(),
    val streakCount: Int = 0,
    val streakExpiresAtMs: Long = 0L,
    val resetSkillSlots: Set<Int> = emptySet(),
    val skillResetToken: Long = 0L,
    val rivalSurgeBanner: String = "",
    val rivalSurgeToken: Long = 0L,
    /** Mini mob waves of this match (waves 1..4). */
    val waveMobIds: List<List<String>> = emptyList(),
    /** Bumped every time a mini monster (or the Boss) is defeated, to drive the "purified" VFX. */
    val mobDefeatedToken: Long = 0L,
    val lastDefeatedName: String = "",
    /** PvP: timer reached 00:00 and we are waiting for player 1's authoritative result. */
    val awaitingResult: Boolean = false,
    // Quest & post-match telemetry
    val blueTilesMatched: Int = 0,
    val totalTilesMatched: Int = 0,
    val maxCombo: Int = 1,
    val abilitiesUsedCount: Int = 0,
    val wonMatch: Boolean = false,
    val rewardOutcome: MatchRewardOutcome? = null,
    val soundEnabled: Boolean = true
) {
    val isFeverActive: Boolean get() = streakCount >= 4
    val isFinalSprint: Boolean get() = secondsRemaining in 1..15
}

/** Pure wave/damage rules, kept separate from coroutines so they can be unit-tested. */
object BattleRules {
    const val MINI_WAVE_COUNT = 4
    const val BOSS_WAVE_INDEX = 5
    const val MATCH_DURATION_SEC = 120
    const val MINI_WEAKNESS_MULT = 1.85f
    const val BOSS_WEAKNESS_MULT = 1.5f
    const val BOSS_ENRAGE_HP_FRACTION = 0.35f
    const val BOSS_ENRAGE_ARMOR_MULT = 0.80f

    fun scaledBossHp(baseBossHp: Int): Int = (baseBossHp * 1.55f).toInt().coerceAtLeast(baseBossHp)

    fun isBossEnraged(waveIndex: Int, bossHp: Int, bossMaxHp: Int): Boolean =
        waveIndex >= BOSS_WAVE_INDEX && bossHp > 0 && bossHp <= (bossMaxHp * BOSS_ENRAGE_HP_FRACTION).toInt()

    fun waveTotalHp(wave: Int, arenaId: Int): Int = when (wave) {
        1 -> 620 + arenaId * 140
        2 -> 860 + arenaId * 190
        3 -> 1100 + arenaId * 240
        else -> 1380 + arenaId * 290
    }

    fun buildWave(ids: List<String>, totalHp: Int): List<MobState> {
        if (ids.isEmpty()) return emptyList()
        val weights = ids.map { GameCatalog.miniMobById(it).hpWeight }
        val sum = weights.sum().coerceAtLeast(0.01f)
        return ids.mapIndexed { i, id ->
            val hp = (totalHp * weights[i] / sum).toInt().coerceAtLeast(1)
            MobState(id, hp, hp)
        }
    }

    fun totalPool(arenaId: Int, bossMaxHp: Int): Int =
        (1..MINI_WAVE_COUNT).sumOf { waveTotalHp(it, arenaId) } + bossMaxHp

    /** The monster currently being hit: first alive mini, or the Boss. */
    fun weaknessFor(waveIndex: Int, mobs: List<MobState>, boss: BossDefinition): Pair<WasteType, Float> {
        if (waveIndex < BOSS_WAVE_INDEX) {
            val target = mobs.firstOrNull { it.alive } ?: return boss.preferredWaste to 1f
            return GameCatalog.miniMobById(target.id).weakness to MINI_WEAKNESS_MULT
        }
        return boss.preferredWaste to BOSS_WEAKNESS_MULT
    }

    /** Cleared tiles weighted by the current target's weakness (and optional off-weakness armor). */
    fun weightedCleared(
        counts: Map<WasteType, Int>,
        weakness: WasteType,
        mult: Float,
        nonWeakMult: Float = 1f
    ): Float =
        counts.entries.sumOf { (t, n) -> (n * if (t == weakness) mult else nonWeakMult).toDouble() }.toFloat()

    data class DamageOutcome(
        val waveIndex: Int,
        val mobs: List<MobState>,
        val bossHp: Int,
        val defeatedNames: List<String>,
        val waveTransitioned: Boolean
    )

    /** Applies [damage] to the minis across waves 1..4 in order (overflow carries over), then to the Wave 5 Boss. */
    fun applyDamage(
        damage: Int,
        waveIndex: Int,
        mobs: List<MobState>,
        bossHp: Int,
        waves: List<List<String>>,
        arenaId: Int,
        bossName: String
    ): DamageOutcome {
        var wave = waveIndex
        var current = mobs.toMutableList()
        var boss = bossHp
        var remaining = damage
        val defeated = mutableListOf<String>()
        var transitioned = false

        fun spawnWaveMobs(nextWave: Int): MutableList<MobState> =
            if (nextWave <= MINI_WAVE_COUNT) {
                buildWave(waves.getOrElse(nextWave - 1) { emptyList() }, waveTotalHp(nextWave, arenaId)).toMutableList()
            } else {
                mutableListOf()
            }

        while (remaining > 0 && wave < BOSS_WAVE_INDEX) {
            val idx = current.indexOfFirst { it.alive }
            if (idx < 0) {
                wave++
                transitioned = true
                current = spawnWaveMobs(wave)
                continue
            }
            val mob = current[idx]
            val dealt = remaining.coerceAtMost(mob.hp)
            current[idx] = mob.copy(hp = mob.hp - dealt)
            remaining -= dealt
            if (current[idx].hp <= 0) defeated += GameCatalog.miniMobById(mob.id).name
        }
        // A wave whose last mob just fell advances even if no damage is left over.
        if (wave < BOSS_WAVE_INDEX && current.none { it.alive }) {
            wave++
            transitioned = true
            current = spawnWaveMobs(wave)
        }
        if (wave >= BOSS_WAVE_INDEX && remaining > 0) {
            val before = boss
            boss = (boss - remaining).coerceAtLeast(0)
            if (before > 0 && boss == 0) defeated += bossName
        }
        return DamageOutcome(wave, current, boss, defeated, transitioned)
    }

    /** Skill charging tuning: ~7-10 deliberate moves to fill a Boss skill (no 2-move spam). */
    const val SKILL_CHARGE_SCALE = 2.65f
    const val SKILL_CHARGE_MIN_GAIN = 3
    const val SKILL_CHARGE_MAX_GAIN = 26
    const val STREAK_WINDOW_MS = 3400L

    data class SkillChargeResult(
        val charges: List<Int>,
        val resetSiblingSlots: Set<Int>
    )

    /**
     * Computes updated 3-slot Eco-Deck skill charges:
     * - Casting a skill resets its own slot to 0 AND resets any other slot sharing the SAME
     *   waste nature (`preferredWaste`) to 0, without granting free charge to remaining slots.
     * - Normal match moves charge skills gradually based on total tiles + preferred waste bonus,
     *   capped per move so skills take multiple tactical moves to earn.
     */
    fun computeUpdatedSkillCharges(
        currentCharges: List<Int>,
        deckBossIds: List<String>,
        countsByType: Map<WasteType, Int>,
        totalCleared: Int,
        consumedSlotIndex: Int?
    ): SkillChargeResult {
        val next = currentCharges.toMutableList()
        while (next.size < 3) next.add(0)
        val resetSiblings = mutableSetOf<Int>()

        if (consumedSlotIndex != null) {
            val usedBossId = deckBossIds.getOrNull(consumedSlotIndex)
            val usedNature = usedBossId?.let { GameCatalog.bossById(it).preferredWaste }
            if (consumedSlotIndex in next.indices) {
                next[consumedSlotIndex] = 0
            }
            if (usedNature != null) {
                for (i in 0 until 3) {
                    if (i == consumedSlotIndex) continue
                    val bId = deckBossIds.getOrNull(i) ?: continue
                    if (GameCatalog.bossById(bId).preferredWaste == usedNature) {
                        if (next[i] > 0) resetSiblings.add(i)
                        next[i] = 0
                    }
                }
            }
            return SkillChargeResult(next, resetSiblings)
        }

        for (i in 0 until 3) {
            val bId = deckBossIds.getOrNull(i) ?: continue
            val bDef = GameCatalog.bossById(bId)
            val matchedFav = countsByType[bDef.preferredWaste] ?: 0
            val rawPoints = totalCleared * 0.62f + matchedFav * 1.15f
            val threshold = bDef.chargeRequired * SKILL_CHARGE_SCALE
            val gain = ((rawPoints / threshold) * 100f).toInt().coerceIn(SKILL_CHARGE_MIN_GAIN, SKILL_CHARGE_MAX_GAIN)
            next[i] = (next[i] + gain).coerceAtMost(100)
        }
        return SkillChargeResult(next, emptySet())
    }

    /** Speed streak damage multiplier: +8% per consecutive rapid match, up to +40% during ECO FEVER! */
    fun streakDamageMultiplier(streakCount: Int): Float =
        1f + ((streakCount - 1).coerceIn(0, 5) * 0.08f)

    /** Clutch final 15 seconds comeback boost (+25% damage). */
    fun finalSprintMultiplier(secondsRemaining: Int): Float =
        if (secondsRemaining in 1..15) 1.25f else 1f

    /** Competitive AI rival pacing for 5-stage 120s matches (~90-108s across Arenas 0-2). */
    const val BOT_MOVE_CHANCE = 0.41f

    data class BotTick(val board: List<List<BoardTile>>, val charges: List<Int>, val damage: Int, val abilityLabel: String?)

    /** One second of the AI rival: competitive match pressure plus Eco-Deck abilities. */
    fun botTick(
        board: List<List<BoardTile>>,
        skillCharges: List<Int>,
        deckIds: List<String>,
        deckLevels: List<Int>,
        arenaId: Int,
        random: Random
    ): BotTick {
        var b = board
        var dmg = 0
        var charges = skillCharges.toMutableList()
        while (charges.size < 3) charges.add(0)
        var label: String? = null

        val move = Match3Engine.findAnyValidMove(b)
        if (move != null && random.nextFloat() < BOT_MOVE_CHANCE + arenaId * 0.015f) {
            val res = Match3Engine.attemptSwap(b, move.first, move.second, random)
            if (res != null) {
                b = res.newBoard
                val avgLevel = deckLevels.average().toFloat().takeIf { !it.isNaN() }?.coerceAtLeast(1f) ?: 1f
                dmg = (res.totalCleared * (23 + arenaId * 2) * (1f + (avgLevel - 1f) * 0.09f) * (1f + (res.comboCount - 1) * 0.22f)).toInt()
                charges = computeUpdatedSkillCharges(
                    currentCharges = charges,
                    deckBossIds = deckIds,
                    countsByType = res.countsByType,
                    totalCleared = res.totalCleared,
                    consumedSlotIndex = null
                ).charges.toMutableList()
            }
        }
        val readySlot = charges.indexOfFirst { it >= 100 }
        if (readySlot >= 0) {
            val bossDef = GameCatalog.bossById(deckIds.getOrElse(readySlot) { "boss_b1" })
            val lvl = deckLevels.getOrElse(readySlot) { 1 }
            b = Match3Engine.applyBossAbility(b, bossDef, random).newBoard
            dmg += (bossDef.abilityDamageAtLevel(lvl) * 0.45f).toInt()
            charges = computeUpdatedSkillCharges(
                currentCharges = charges,
                deckBossIds = deckIds,
                countsByType = emptyMap(),
                totalCleared = 0,
                consumedSlotIndex = readySlot
            ).charges.toMutableList()
            label = "${bossDef.name}: ${bossDef.abilityName}"
        }
        return BotTick(b, charges, dmg, label)
    }

    /**
     * Same answer on both clients for the same data: more damage wins; a tie goes to the
     * higher clean-up %, then to the lexicographically smaller uid. Returns the winner uid.
     */
    fun timeoutWinner(p1: PlayerBattleSnapshot, p2: PlayerBattleSnapshot): String = when {
        p1.totalDamage != p2.totalDamage -> if (p1.totalDamage > p2.totalDamage) p1.uid else p2.uid
        p1.restorationPct != p2.restorationPct -> if (p1.restorationPct > p2.restorationPct) p1.uid else p2.uid
        else -> minOf(p1.uid, p2.uid)
    }
}

/**
 * Firebase-backed 1v1 Battle Session Manager.
 * Handles Match-3 grid resolution, Eco-Deck charged abilities, mini-mob waves -> shared Boss,
 * real-time environmental pollution clearing, rival synchronization via Firestore `liveMatches/{matchId}`,
 * Replay frame capture, and post-match Trophy/Coin/Boss/Quest persistence.
 */
class GameStateManager(
    private val repository: WasteCrushRepository,
    private val scope: CoroutineScope,
    val soundPlayer: ArcadeSoundPlayer = ArcadeSoundPlayer(),
    private val fiveGNetworkManager: FiveGNetworkManager? = null
) {
    private val _state = MutableStateFlow(GameSessionState())
    val state: StateFlow<GameSessionState> = _state.asStateFlow()

    private val _telemetry = MutableStateFlow(LatencyTelemetryState())
    val telemetry: StateFlow<LatencyTelemetryState> = _telemetry.asStateFlow()

    private val rttCalculator = RttJitterCalculator()
    private val edgeWsClient = EdgeWebSocketClient(scope)

    private var activeProfile: UserProfile? = null
    private var timerLoopJob: Job? = null
    private var liveObserverJob: Job? = null
    private var throttledSyncJob: Job? = null
    private var swapAnimJob: Job? = null
    private var resultGraceJob: Job? = null
    private var linkObserverJob: Job? = null
    private var rttProbeJob: Job? = null
    private var onFinished: (Boolean) -> Unit = {}

    private var volleyCounter = 1L
    private var lastOutboundPushMs = 0L
    private var lastReplayRecordSec = -1
    private var lastPlayerMoveMs = 0L
    private var pendingReplayNote = ""
    private var edgeStatusText = "Cloud Default"
    private var edgeConnected = false
    private var edgeBoundToSlice = false
    private var queuedSwap: Pair<GridPos, GridPos>? = null
    private var queuedSkillSlot: Int? = null
    @Volatile private var firestoreWriteInFlight = false
    @Volatile private var firestoreWriteDirty = false

    private val tilesMatchedByType = mutableMapOf<WasteType, Int>()
    private val recordedFrames = mutableListOf<ReplayFrame>()

    fun toggleSound() {
        val next = !_state.value.soundEnabled
        soundPlayer.soundEnabled = next
        SettingsStore.setSound(next)
        _state.value = _state.value.copy(soundEnabled = next)
    }

    fun startSession(
        config: GameSessionConfig,
        onSessionFinished: (won: Boolean) -> Unit
    ) {
        stopSession()
        soundPlayer.soundEnabled = SettingsStore.current.soundEnabled
        activeProfile = config.profile
        onFinished = onSessionFinished
        tilesMatchedByType.clear()
        recordedFrames.clear()
        lastOutboundPushMs = 0L
        lastReplayRecordSec = -1
        lastPlayerMoveMs = System.currentTimeMillis()
        pendingReplayNote = ""

        val arenaId = config.arena.id
        val waves = config.waves.takeIf { it.size >= BattleRules.MINI_WAVE_COUNT } ?: GameCatalog.matchWaves(config.matchId, arenaId)
        val wave1Total = BattleRules.waveTotalHp(1, arenaId)
        val scaledBossMaxHp = BattleRules.scaledBossHp(config.sharedBoss.bossBattleHp)
        val myLevels = config.profile.equippedDeckIds.map { config.profile.bossLevels[it] ?: 1 }
        val now = System.currentTimeMillis()

        fun snapshot(
            uid: String, name: String, trophies: Int, deckIds: List<String>, deckLevels: List<Int>, board: List<List<BoardTile>>
        ): PlayerBattleSnapshot {
            val mobs = BattleRules.buildWave(waves[0], wave1Total)
            return PlayerBattleSnapshot(
                uid = uid,
                name = name,
                trophies = trophies,
                deckBossIds = deckIds,
                deckBossLevels = deckLevels,
                board = board,
                boardFlat = Match3Engine.encodeBoardFlat(board),
                waveIndex = 1,
                waveMobs = mobs,
                currentEnemyHp = mobs.sumOf { it.hp },
                currentEnemyMaxHp = mobs.sumOf { it.maxHp },
                bossHp = scaledBossMaxHp,
                bossMaxHp = scaledBossMaxHp,
                lastUpdatedMs = now
            )
        }

        val localSnapshot = snapshot(
            config.profile.userId, config.profile.displayName, config.profile.trophies,
            config.profile.equippedDeckIds, myLevels, config.initialLocalBoard
        )
        val oppSnapshot = snapshot(
            config.opponentUid, config.opponentName, config.opponentTrophies,
            config.opponentDeckIds, config.opponentDeckLevels, config.initialOpponentBoard
        )

        val isLocalFallback = config.matchId.startsWith("local_")
        _state.value = GameSessionState(
            isActive = true,
            isFinished = false,
            matchId = config.matchId,
            arenaId = arenaId,
            placeId = config.place.id,
            sharedBossId = config.sharedBoss.id,
            amIPlayer1 = config.amIPlayer1,
            isBotMatch = config.isBotMatch,
            secondsRemaining = config.durationSec,
            totalDurationSec = config.durationSec,
            spectatorCount = 0,
            syncStatus = if (isLocalFallback) SessionSyncStatus.LOCAL_FALLBACK else SessionSyncStatus.LIVE_SYNCED,
            lastCloudSyncMs = now,
            localPlayer = localSnapshot,
            opponentPlayer = oppSnapshot,
            waveMobIds = waves,
            soundEnabled = soundPlayer.soundEnabled
        )

        recordReplayFrame("Battle started at ${config.place.name}! Shared Boss: ${config.sharedBoss.name}")

        // 0. Live 5G SA radio/slice telemetry + optional slice-bound Edge WebSocket transport
        rttCalculator.reset()
        edgeConnected = false
        edgeBoundToSlice = false
        edgeStatusText = if (isLocalFallback) "Offline Practice" else "Cloud Default"
        publishTelemetrySnapshot(isLocalFallback = isLocalFallback)
        startTelemetryAndEdgeTransport(config, isLocalFallback)

        // 1. Real-time Firestore listener for opponent grid, Boss HP, pollution level & spectators
        observeRemoteMatchSession(config.matchId, config.amIPlayer1, config.isBotMatch)

        // 2. Battle timer, VFX cleanup, 4-wave rival simulation & periodic sync
        val oppWavesRemaining = IntArray(BattleRules.MINI_WAVE_COUNT) { idx ->
            BattleRules.waveTotalHp(idx + 1, arenaId)
        }

        timerLoopJob = scope.launch {
            while (_state.value.isActive && !_state.value.isFinished) {
                delay(1000L)
                val s = _state.value
                if (!s.isActive || s.isFinished || s.awaitingResult) break

                val nextSec = (s.secondsRemaining - 1).coerceAtLeast(0)
                val nowMs = System.currentTimeMillis()
                val prunedVolleys = s.projectileVolleys.filter { nowMs - it.createdAtMs < 900L }
                val streakAlive = s.streakCount > 0 && nowMs <= s.streakExpiresAtMs
                val nextHint = if (SettingsStore.current.hintsEnabled && s.hintPair == null && !s.isBoardAnimating && nowMs - lastPlayerMoveMs >= 4000L) {
                    Match3Engine.findAnyValidMove(s.localPlayer.board)
                } else {
                    s.hintPair
                }

                _state.value = s.copy(
                    secondsRemaining = nextSec,
                    projectileVolleys = prunedVolleys,
                    streakCount = if (streakAlive) s.streakCount else 0,
                    streakExpiresAtMs = if (streakAlive) s.streakExpiresAtMs else 0L,
                    resetSkillSlots = if (nowMs - s.skillResetToken > 1200L) emptySet() else s.resetSkillSlots,
                    rivalSurgeBanner = if (nowMs - s.rivalSurgeToken > 2200L) "" else s.rivalSurgeBanner,
                    hintPair = nextHint,
                    comboBannerText = if (nowMs - s.hitFlashToken > 1100L) "" else s.comboBannerText
                )

                if (s.isBotMatch && nextSec > 0) {
                    stepTrophyScaledRival(oppWavesRemaining, s.arenaId)
                }

                // Capture replay frame every 2 seconds & heartbeat cloud sync (coalesced off main thread)
                val elapsed = s.totalDurationSec - nextSec
                if (elapsed % 2 == 0 && elapsed != lastReplayRecordSec) {
                    lastReplayRecordSec = elapsed
                    val note = pendingReplayNote
                    pendingReplayNote = ""
                    recordReplayFrame(note)
                    requestRealtimeSync(immediate = false)
                }

                val latest = _state.value
                if (latest.localPlayer.bossHp <= 0) {
                    concludeSession(won = true)
                    break
                } else if (latest.opponentPlayer.bossHp <= 0) {
                    concludeSession(won = false)
                    break
                } else if (nextSec <= 0) {
                    onTimeUp()
                    break
                }
            }
        }
    }

    /**
     * Time is up. Bot/local matches and player 1 decide immediately with the shared tie-break.
     * Player 2 waits for player 1's FINISHED write (picked up by the observer) and only falls
     * back to the same deterministic rule if nothing arrives, so both sides never both "win".
     */
    private fun onTimeUp() {
        val s = _state.value
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer
        val authoritative = s.isBotMatch || s.amIPlayer1 || s.matchId.startsWith("local_")
        if (authoritative) {
            concludeSession(won = BattleRules.timeoutWinner(p1, p2) == s.localPlayer.uid)
            return
        }
        _state.value = s.copy(awaitingResult = true, comboBannerText = "Time! Confirming result...")
        resultGraceJob?.cancel()
        resultGraceJob = scope.launch {
            delay(5000L)
            val cur = _state.value
            if (!cur.isFinished) {
                val q1 = if (cur.amIPlayer1) cur.localPlayer else cur.opponentPlayer
                val q2 = if (cur.amIPlayer1) cur.opponentPlayer else cur.localPlayer
                concludeSession(won = BattleRules.timeoutWinner(q1, q2) == cur.localPlayer.uid)
            }
        }
    }

    private fun observeRemoteMatchSession(
        matchId: String,
        amIPlayer1: Boolean,
        isBotMatch: Boolean
    ) {
        if (matchId.startsWith("local_")) return
        liveObserverJob?.cancel()
        liveObserverJob = scope.launch {
            repository.observeLiveMatch(matchId)
                .catch { err ->
                    val fsErr = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && err is CancellationException) throw err
                }
                .collect { live ->
                    if (live == null) return@collect
                    val current = _state.value
                    if (current.isFinished) return@collect

                        val remoteOpp = if (amIPlayer1) live.player2 else live.player1
                        if (!isBotMatch) {
                            applyRemoteOpponentUpdate(remoteOpp, live.spectatorCount)

                            // Authoritative remote result (player 1 at time-up, or whoever killed the Boss)
                            if (live.status == "FINISHED" && live.winnerUid.isNotBlank()) {
                                val myUid = activeProfile?.userId.orEmpty()
                                concludeSession(won = live.winnerUid == myUid, publish = false)
                            } else if (remoteOpp.bossHp <= 0) {
                                concludeSession(won = false)
                            }
                        } else {
                            _state.value = current.copy(
                                spectatorCount = live.spectatorCount,
                                syncStatus = SessionSyncStatus.LIVE_SYNCED
                            )
                        }
                }
        }
    }

    private fun applyRemoteOpponentUpdate(remoteOpp: PlayerLiveState, spectatorCount: Int? = null) {
        val current = _state.value
        if (current.isFinished) return
        val decodedBoard = if (remoteOpp.boardFlat.size == 49) {
            Match3Engine.decodeBoardFlat(remoteOpp.boardFlat)
        } else current.opponentPlayer.board

        val prevOpp = current.opponentPlayer
        val nowMs = System.currentTimeMillis()
        val oppPollution = (1f - remoteOpp.restorationPct / 100f).coerceIn(0f, 1f)
        val updatedOpp = prevOpp.copy(
            uid = remoteOpp.uid.ifBlank { prevOpp.uid },
            name = remoteOpp.name.ifBlank { prevOpp.name },
            trophies = if (remoteOpp.trophies > 0) remoteOpp.trophies else prevOpp.trophies,
            deckBossIds = remoteOpp.deckBossIds.ifEmpty { prevOpp.deckBossIds },
            deckBossLevels = remoteOpp.deckBossLevels.ifEmpty { prevOpp.deckBossLevels },
            board = decodedBoard,
            boardFlat = if (remoteOpp.boardFlat.size == 49) remoteOpp.boardFlat else prevOpp.boardFlat,
            waveIndex = remoteOpp.waveIndex,
            bossHp = remoteOpp.bossHp,
            bossMaxHp = remoteOpp.bossMaxHp.coerceAtLeast(1),
            totalDamage = remoteOpp.totalDamage,
            restorationPct = remoteOpp.restorationPct.coerceIn(0, 100),
            pollutionLevel = oppPollution,
            skillCharges = remoteOpp.skillCharges.ifEmpty { listOf(0, 0, 0) },
            lastAbilityText = remoteOpp.lastAbility.ifBlank { prevOpp.lastAbilityText },
            lastUpdatedMs = nowMs
        )
        val surgeAlert = when {
            remoteOpp.waveIndex >= BattleRules.BOSS_WAVE_INDEX && prevOpp.waveIndex < BattleRules.BOSS_WAVE_INDEX -> "${updatedOpp.name} reached the BOSS!"
            remoteOpp.waveIndex > prevOpp.waveIndex && remoteOpp.waveIndex < BattleRules.BOSS_WAVE_INDEX -> "${updatedOpp.name} entered Wave ${remoteOpp.waveIndex}!"
            remoteOpp.lastAbility.isNotBlank() && remoteOpp.lastAbility != prevOpp.lastAbilityText ->
                "${updatedOpp.name}: ${remoteOpp.lastAbility.substringAfter(": ")}"
            else -> ""
        }
        rttCalculator.recordPacketEvent()
        _state.value = current.copy(
            spectatorCount = spectatorCount ?: current.spectatorCount,
            syncStatus = SessionSyncStatus.LIVE_SYNCED,
            lastCloudSyncMs = nowMs,
            opponentPlayer = updatedOpp,
            rivalSurgeBanner = if (surgeAlert.isNotBlank()) surgeAlert else current.rivalSurgeBanner,
            rivalSurgeToken = if (surgeAlert.isNotBlank()) nowMs else current.rivalSurgeToken
        )
        publishTelemetrySnapshot(isLocalFallback = current.matchId.startsWith("local_"))
    }

    private fun startTelemetryAndEdgeTransport(config: GameSessionConfig, isLocalFallback: Boolean) {
        linkObserverJob?.cancel()
        linkObserverJob = scope.launch {
            val linkFlow = fiveGNetworkManager?.linkState ?: return@launch
            linkFlow.collect {
                publishTelemetrySnapshot(isLocalFallback = isLocalFallback)
            }
        }

        val edgeUrl = config.edgeServerUrl.ifBlank { SettingsStore.current.edgeServerUrl }.trim()
        if (!isLocalFallback && !config.isBotMatch && EdgeWebSocketClient.isValidWebSocketUrl(edgeUrl)) {
            val sliceNet = fiveGNetworkManager?.activeSliceNetwork?.value
            edgeWsClient.connect(
                url = edgeUrl,
                matchId = config.matchId,
                localUid = config.profile.userId,
                localName = config.profile.displayName,
                sliceNetwork = sliceNet,
                onStatusChanged = { connected, bound, statusText ->
                    edgeConnected = connected
                    edgeBoundToSlice = bound
                    edgeStatusText = statusText
                    publishTelemetrySnapshot(isLocalFallback = false)
                },
                onRttMeasured = { rttMs ->
                    rttCalculator.recordRttSample(rttMs)
                    publishTelemetrySnapshot(isLocalFallback = false)
                },
                onOpponentStateReceived = { remoteOpp ->
                    applyRemoteOpponentUpdate(remoteOpp)
                    if (remoteOpp.bossHp <= 0 && !_state.value.isFinished) {
                        concludeSession(won = false)
                    }
                },
                onRemoteFinishReceived = { winnerUid ->
                    if (!_state.value.isFinished) {
                        val myUid = activeProfile?.userId.orEmpty()
                        concludeSession(won = winnerUid == myUid, publish = false)
                    }
                }
            )
        }

        // Periodic lightweight Firestore RTT probe on IO dispatcher when Edge WS is not connected
        rttProbeJob?.cancel()
        if (!isLocalFallback) {
            rttProbeJob = scope.launch(Dispatchers.IO) {
                while (_state.value.isActive && !_state.value.isFinished) {
                    if (!edgeConnected) {
                        val rtt = repository.measureFirestoreRttMs(config.matchId)
                        if (rtt != null) {
                            rttCalculator.recordRttSample(rtt)
                            publishTelemetrySnapshot(isLocalFallback = false)
                        }
                    } else {
                        publishTelemetrySnapshot(isLocalFallback = false)
                    }
                    delay(2500L)
                }
            }
        }
    }

    private fun publishTelemetrySnapshot(isLocalFallback: Boolean) {
        val link = fiveGNetworkManager?.linkState?.value ?: RadioLinkSnapshot()
        val stats = rttCalculator.snapshot()
        val mode = when {
            isLocalFallback -> SyncTransportMode.LOCAL_OFFLINE
            edgeConnected -> SyncTransportMode.EDGE_WEBSOCKET
            else -> SyncTransportMode.FIRESTORE_CLOUD
        }
        _telemetry.value = LatencyTelemetryState(
            bearer = link.bearer,
            sliceStatus = link.sliceStatus,
            transportMode = mode,
            currentRttMs = stats.currentRttMs,
            avgRttMs = stats.avgRttMs,
            minRttMs = stats.minRttMs,
            p95RttMs = stats.p95RttMs,
            jitterMs = stats.jitterMs,
            packetRateHz = stats.packetRateHz,
            downstreamKbps = link.downstreamKbps,
            upstreamKbps = link.upstreamKbps,
            isUnmetered = link.isUnmetered,
            rttHistoryMs = stats.historyMs,
            sliceUpsellAvailable = link.sliceUpsellAvailable,
            sliceBoundToTransport = edgeBoundToSlice || link.sliceStatus == SliceStatus.ACTIVE,
            carrierName = link.carrierName,
            edgeServerStatus = edgeStatusText,
            lastMeasuredAtMs = stats.lastMeasuredAtMs
        )
    }

    fun onTileTapped(pos: GridPos) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.awaitingResult) return
        val currentSel = s.selectedTile
        if (s.isBoardAnimating) {
            if (currentSel != null && Match3Engine.areAdjacent(currentSel, pos)) {
                queuedSwap = currentSel to pos
            } else {
                _state.value = s.copy(selectedTile = pos, hintPair = null)
            }
            return
        }
        when {
            currentSel == null -> _state.value = s.copy(selectedTile = pos, hintPair = null)
            currentSel == pos -> _state.value = s.copy(selectedTile = null)
            Match3Engine.areAdjacent(currentSel, pos) -> performLocalSwap(currentSel, pos)
            else -> _state.value = s.copy(selectedTile = pos, hintPair = null)
        }
    }

    fun onTileSwiped(from: GridPos, to: GridPos) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.awaitingResult) return
        if (!Match3Engine.areAdjacent(from, to)) return
        if (s.isBoardAnimating) {
            // Buffer 1 upcoming swipe so fast players never feel dropped input
            queuedSwap = from to to
            return
        }
        performLocalSwap(from, to)
    }

    private fun performLocalSwap(a: GridPos, b: GridPos) {
        val s = _state.value
        val stepped = Match3Engine.attemptSwapSteps(s.localPlayer.board, a, b)
        if (stepped == null) {
            val token = System.currentTimeMillis()
            lastPlayerMoveMs = token
            soundPlayer.playInvalidSwap()
            _state.value = s.copy(
                selectedTile = null,
                invalidSwapPair = a to b,
                invalidSwapToken = token
            )
            scope.launch {
                delay(200L)
                if (_state.value.invalidSwapToken == token) {
                    _state.value = _state.value.copy(invalidSwapPair = null)
                }
            }
            return
        }
        soundPlayer.playSwap()
        runSteppedResolution(
            stepped = stepped,
            animateInitialSwap = true,
            abilityBonusDamage = 0,
            usedAbilityLabel = null,
            usedAbilityWaste = null,
            consumedSlotIndex = null
        )
    }

    fun activateEcoDeckSlot(slotIndex: Int) {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.awaitingResult) return
        val charge = s.localPlayer.skillCharges.getOrElse(slotIndex) { 0 }
        if (charge < 100) return
        if (s.isBoardAnimating) {
            queuedSkillSlot = slotIndex
            return
        }

        val bossId = s.localPlayer.deckBossIds.getOrElse(slotIndex) { return }
        val bossLvl = s.localPlayer.deckBossLevels.getOrElse(slotIndex) { 1 }
        val bossDef = GameCatalog.bossById(bossId)
        val stepped = Match3Engine.applyBossAbilitySteps(s.localPlayer.board, bossDef)

        soundPlayer.playAbilityBlast()
        pendingReplayNote = "${s.localPlayer.name} unleashed ${bossDef.name}'s ${bossDef.abilityName}!"

        runSteppedResolution(
            stepped = stepped,
            animateInitialSwap = false,
            abilityBonusDamage = bossDef.abilityDamageAtLevel(bossLvl),
            usedAbilityLabel = "${bossDef.name}: ${bossDef.abilityName}",
            usedAbilityWaste = bossDef.preferredWaste,
            consumedSlotIndex = slotIndex
        )
    }

    private fun runSteppedResolution(
        stepped: SteppedMatchResolution,
        animateInitialSwap: Boolean,
        abilityBonusDamage: Int,
        usedAbilityLabel: String?,
        usedAbilityWaste: WasteType?,
        consumedSlotIndex: Int?
    ) {
        val startState = _state.value
        val moveNow = System.currentTimeMillis()
        lastPlayerMoveMs = moveNow

        // Update rapid-play Speed Streak / ECO FEVER
        val nextStreak = if (startState.streakCount > 0 && moveNow <= startState.streakExpiresAtMs) {
            (startState.streakCount + 1).coerceAtMost(9)
        } else {
            1
        }
        val nextStreakExpiry = moveNow + BattleRules.STREAK_WINDOW_MS

        _state.value = startState.copy(
            selectedTile = null,
            hintPair = null,
            isBoardAnimating = true,
            streakCount = nextStreak,
            streakExpiresAtMs = nextStreakExpiry
        )

        swapAnimJob?.cancel()
        swapAnimJob = scope.launch {
            var anyWaveTransitioned = false
            try {
                if (animateInitialSwap) {
                    val cur = _state.value
                    _state.value = cur.copy(
                        localPlayer = cur.localPlayer.copy(board = stepped.swappedBoard)
                    )
                    delay(58L)
                }

                stepped.steps.forEachIndexed { idx, step ->
                    if (!_state.value.isActive || _state.value.isFinished) return@launch
                    val stepAbilityDamage = if (idx == 0) abilityBonusDamage else 0
                    val stepIsAbility = idx == 0 && usedAbilityLabel != null
                    val waveChanged = applyCascadeStepImpact(
                        step = step,
                        streakCount = nextStreak,
                        stepAbilityBonusDamage = stepAbilityDamage,
                        isAbilityStep = stepIsAbility,
                        abilityWaste = usedAbilityWaste,
                        usedAbilityLabel = if (idx == 0) usedAbilityLabel else null
                    )
                    if (waveChanged) anyWaveTransitioned = true
                    if (_state.value.localPlayer.bossHp <= 0) {
                        concludeSession(won = true)
                        return@launch
                    }
                    delay(if (idx == stepped.steps.lastIndex) 48L else 112L)
                }

                if (_state.value.isActive && !_state.value.isFinished) {
                    finalizeLocalMove(
                        res = stepped.finalResolution,
                        consumedSlotIndex = consumedSlotIndex,
                        usedAbilityLabel = usedAbilityLabel,
                        waveTransitioned = anyWaveTransitioned
                    )
                }
            } finally {
                _state.value = _state.value.copy(isBoardAnimating = false)
                drainQueuedInput()
            }
        }
    }

    private fun drainQueuedInput() {
        val s = _state.value
        if (!s.isActive || s.isFinished || s.awaitingResult || s.isBoardAnimating) return
        val pendingSkill = queuedSkillSlot
        if (pendingSkill != null) {
            queuedSkillSlot = null
            queuedSwap = null
            if (s.localPlayer.skillCharges.getOrElse(pendingSkill) { 0 } >= 100) {
                activateEcoDeckSlot(pendingSkill)
                return
            }
        }
        val pendingSwap = queuedSwap
        if (pendingSwap != null) {
            queuedSwap = null
            performLocalSwap(pendingSwap.first, pendingSwap.second)
        }
    }

    /**
     * Applies one cascade wave immediately: triggers tile pops, fires animated projectiles from
     * the exact matched cells toward the active mob/Boss, deals weighted + streak + sprint damage,
     * and advances waves progressively so every cascade feels crisp and reactive.
     */
    private fun applyCascadeStepImpact(
        step: CascadeStep,
        streakCount: Int,
        stepAbilityBonusDamage: Int,
        isAbilityStep: Boolean,
        abilityWaste: WasteType?,
        usedAbilityLabel: String?
    ): Boolean {
        val s = _state.value
        val local = s.localPlayer
        val sharedBoss = GameCatalog.bossById(s.sharedBossId)
        val nowMs = System.currentTimeMillis()

        soundPlayer.playMatch(step.comboIndex, streakCount)

        for ((type, count) in step.countsByType) {
            tilesMatchedByType[type] = (tilesMatchedByType[type] ?: 0) + count
        }

        // Eco-Deck preferred recyclable synergy multiplier
        var deckBonusMult = 1f
        for (i in 0 until 3) {
            val bId = local.deckBossIds.getOrNull(i) ?: continue
            val bDef = GameCatalog.bossById(bId)
            val bLvl = local.deckBossLevels.getOrElse(i) { 1 }
            if ((step.countsByType[bDef.preferredWaste] ?: 0) > 0) {
                deckBonusMult += bDef.tileBonusPercentAtLevel(bLvl) / 100f
            }
        }

        val (weakness, weakMult) = BattleRules.weaknessFor(local.waveIndex, local.waveMobs, sharedBoss)
        val enragedBoss = BattleRules.isBossEnraged(local.waveIndex, local.bossHp, local.bossMaxHp)
        val nonWeakMult = if (enragedBoss) BattleRules.BOSS_ENRAGE_ARMOR_MULT else 1f
        val weakHits = step.countsByType[weakness] ?: 0
        val comboMult = 1f + (step.comboIndex - 1) * 0.35f
        val streakMult = BattleRules.streakDamageMultiplier(streakCount)
        val sprintMult = BattleRules.finalSprintMultiplier(s.secondsRemaining)
        val tileDamage = (BattleRules.weightedCleared(step.countsByType, weakness, weakMult, nonWeakMult) * 18f * deckBonusMult * comboMult * streakMult * sprintMult).toInt()
        val stepHit = tileDamage + stepAbilityBonusDamage

        val outcome = BattleRules.applyDamage(
            damage = stepHit,
            waveIndex = local.waveIndex,
            mobs = local.waveMobs,
            bossHp = local.bossHp,
            waves = s.waveMobIds,
            arenaId = s.arenaId,
            bossName = sharedBoss.name
        )
        val waveIdx = outcome.waveIndex
        val bossHp = outcome.bossHp
        if (outcome.defeatedNames.isNotEmpty()) {
            soundPlayer.playMobDefeated()
        }
        if (outcome.waveTransitioned) {
            pendingReplayNote = if (waveIdx < BattleRules.BOSS_WAVE_INDEX) {
                "${local.name} cleared Wave ${waveIdx - 1}!"
            } else {
                "${local.name} summoned the Shared Boss!"
            }
        }

        val curHp = if (waveIdx >= BattleRules.BOSS_WAVE_INDEX) bossHp else outcome.mobs.sumOf { it.hp }
        val curMaxHp = if (waveIdx >= BattleRules.BOSS_WAVE_INDEX) local.bossMaxHp else outcome.mobs.sumOf { it.maxHp }
        val totalPool = BattleRules.totalPool(s.arenaId, local.bossMaxHp)
        val newTotalDamage = local.totalDamage + stepHit
        val restorationPct = if (bossHp <= 0) 100 else ((newTotalDamage.toFloat() / totalPool.toFloat()) * 100f).toInt().coerceIn(0, 99)
        val pollutionLevel = (1f - restorationPct / 100f).coerceIn(0f, 1f)

        // Cap projectile origins to 5 evenly-spaced matched tiles so 60fps flight stays clean & snappy
        val cappedOrigins = if (step.clearedTiles.size <= 5) {
            step.clearedTiles
        } else {
            val stride = step.clearedTiles.size / 5f
            List(5) { idx -> step.clearedTiles[(idx * stride).toInt().coerceIn(0, step.clearedTiles.lastIndex)] }
        }

        val volley = DamageProjectileVolley(
            id = volleyCounter++,
            origins = cappedOrigins,
            damageAmount = stepHit,
            isCritical = isAbilityStep || step.comboIndex >= 2 || streakCount >= 4,
            isWeakHit = weakHits > 0,
            isAbility = isAbilityStep,
            comboIndex = step.comboIndex,
            abilityWasteType = abilityWaste,
            createdAtMs = nowMs
        )

        val comboText = when {
            outcome.defeatedNames.isNotEmpty() -> "${outcome.defeatedNames.last()} purified! -$stepHit"
            usedAbilityLabel != null -> "ECO-SKILL! -$stepHit"
            step.comboIndex >= 4 -> "${step.comboIndex}x MEGA CASCADE! -$stepHit"
            step.comboIndex >= 2 -> "${step.comboIndex}x COMBO! -$stepHit"
            streakCount >= 4 -> "ECO FEVER! -$stepHit"
            weakHits > 0 -> "WEAK SPOT x${if (local.waveIndex < BattleRules.BOSS_WAVE_INDEX) "1.85" else "1.5"}! -$stepHit"
            enragedBoss -> "BOSS SHIELDED! -$stepHit"
            step.spawnedSpecialCount > 0 -> "POWER TILE! -$stepHit"
            else -> "-$stepHit HP"
        }

        val nextLocal = local.copy(
            board = step.boardAfterGravity,
            waveIndex = waveIdx,
            waveMobs = outcome.mobs,
            currentEnemyHp = curHp,
            currentEnemyMaxHp = curMaxHp,
            bossHp = bossHp,
            totalDamage = newTotalDamage,
            restorationPct = restorationPct,
            pollutionLevel = pollutionLevel,
            lastAbilityText = usedAbilityLabel ?: local.lastAbilityText,
            lastUpdatedMs = nowMs
        )

        val recentVolleys = (s.projectileVolleys.filter { nowMs - it.createdAtMs < 900L } + volley).takeLast(5)
        _state.value = s.copy(
            localPlayer = nextLocal,
            popBurstTiles = step.clearedTiles,
            popBurstToken = nowMs,
            comboFlashLevel = step.comboIndex,
            hitFlashToken = nowMs,
            lastDamageDealt = stepHit,
            comboBannerText = comboText,
            projectileVolleys = recentVolleys,
            mobDefeatedToken = if (outcome.defeatedNames.isNotEmpty()) nowMs else s.mobDefeatedToken,
            lastDefeatedName = outcome.defeatedNames.lastOrNull() ?: s.lastDefeatedName,
            blueTilesMatched = tilesMatchedByType[WasteType.PLASTIC_BLUE] ?: 0,
            totalTilesMatched = s.totalTilesMatched + step.clearedTiles.size,
            maxCombo = maxOf(s.maxCombo, step.comboIndex)
        )
        if (outcome.defeatedNames.isNotEmpty() && pendingReplayNote.isBlank()) {
            pendingReplayNote = "${local.name} purified ${outcome.defeatedNames.joinToString()}!"
        }
        return outcome.waveTransitioned
    }

    private fun finalizeLocalMove(
        res: MatchResolution,
        consumedSlotIndex: Int?,
        usedAbilityLabel: String?,
        waveTransitioned: Boolean
    ) {
        val s = _state.value
        val local = s.localPlayer
        val nowMs = System.currentTimeMillis()
        val prevCharges = local.skillCharges
        val chargeResult = BattleRules.computeUpdatedSkillCharges(
            currentCharges = prevCharges,
            deckBossIds = local.deckBossIds,
            countsByType = res.countsByType,
            totalCleared = res.totalCleared,
            consumedSlotIndex = consumedSlotIndex
        )

        // Play bright cue if any skill just crossed 100%
        val newlyReady = chargeResult.charges.indices.any { i ->
            (prevCharges.getOrElse(i) { 0 } < 100) && (chargeResult.charges[i] >= 100)
        }
        if (newlyReady) {
            soundPlayer.playSkillReady()
        }

        val nextLocal = local.copy(
            board = res.newBoard,
            boardFlat = Match3Engine.encodeBoardFlat(res.newBoard),
            skillCharges = chargeResult.charges,
            lastAbilityText = usedAbilityLabel ?: local.lastAbilityText,
            lastUpdatedMs = nowMs
        )
        _state.value = s.copy(
            localPlayer = nextLocal,
            selectedTile = null,
            resetSkillSlots = if (chargeResult.resetSiblingSlots.isNotEmpty()) chargeResult.resetSiblingSlots else s.resetSkillSlots,
            skillResetToken = if (chargeResult.resetSiblingSlots.isNotEmpty()) nowMs else s.skillResetToken,
            abilitiesUsedCount = s.abilitiesUsedCount + if (consumedSlotIndex != null) 1 else 0
        )

        if (nextLocal.bossHp <= 0) {
            concludeSession(won = true)
        } else {
            requestRealtimeSync(immediate = waveTransitioned || consumedSlotIndex != null)
        }
    }

    private fun stepTrophyScaledRival(
        oppWavesRemaining: IntArray,
        arenaId: Int
    ) {
        val s = _state.value
        val opp = s.opponentPlayer
        val tick = BattleRules.botTick(opp.board, opp.skillCharges, opp.deckBossIds, opp.deckBossLevels, arenaId, Random.Default)
        val oppBoard = tick.board
        val dmg = tick.damage
        val charges = tick.charges
        val lastAbility = tick.abilityLabel ?: opp.lastAbilityText
        if (tick.abilityLabel != null) pendingReplayNote = "${opp.name} unleashed ${tick.abilityLabel.substringAfter(": ")}!"

        var waveIdx = opp.waveIndex
        var bossHp = opp.bossHp
        var remainingDmg = dmg

        while (waveIdx < BattleRules.BOSS_WAVE_INDEX && remainingDmg > 0) {
            val idx = (waveIdx - 1).coerceIn(0, oppWavesRemaining.lastIndex)
            val dealt = remainingDmg.coerceAtMost(oppWavesRemaining[idx])
            oppWavesRemaining[idx] -= dealt
            remainingDmg -= dealt
            if (oppWavesRemaining[idx] <= 0) {
                waveIdx++
                pendingReplayNote = if (waveIdx < BattleRules.BOSS_WAVE_INDEX) {
                    "${opp.name} cleared Wave ${waveIdx - 1}!"
                } else {
                    "${opp.name} summoned the Shared Boss!"
                }
            }
        }
        if (waveIdx >= BattleRules.BOSS_WAVE_INDEX && remainingDmg > 0) {
            bossHp = (bossHp - remainingDmg).coerceAtLeast(0)
        }

        val totalPool = BattleRules.totalPool(arenaId, opp.bossMaxHp)
        val newTotalDmg = opp.totalDamage + dmg
        val restPct = if (bossHp <= 0) 100 else ((newTotalDmg.toFloat() / totalPool.toFloat()) * 100f).toInt().coerceIn(0, 99)
        val pollution = (1f - restPct / 100f).coerceIn(0f, 1f)

        val nowMs = System.currentTimeMillis()
        val surgeAlert = when {
            waveIdx >= BattleRules.BOSS_WAVE_INDEX && opp.waveIndex < BattleRules.BOSS_WAVE_INDEX -> "${opp.name} reached the BOSS!"
            waveIdx > opp.waveIndex && waveIdx < BattleRules.BOSS_WAVE_INDEX -> "${opp.name} entered Wave $waveIdx!"
            tick.abilityLabel != null -> "${opp.name}: ${tick.abilityLabel.substringAfter(": ")}"
            else -> ""
        }

        _state.value = s.copy(
            opponentPlayer = opp.copy(
                board = oppBoard,
                boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
                waveIndex = waveIdx,
                bossHp = bossHp,
                totalDamage = newTotalDmg,
                restorationPct = restPct,
                pollutionLevel = pollution,
                skillCharges = charges,
                lastAbilityText = lastAbility,
                lastUpdatedMs = nowMs
            ),
            rivalSurgeBanner = if (surgeAlert.isNotBlank()) surgeAlert else s.rivalSurgeBanner,
            rivalSurgeToken = if (surgeAlert.isNotBlank()) nowMs else s.rivalSurgeToken
        )
    }

    /**
     * Dual-plane real-time sync (zero-contention, zero ping spikes on moves):
     * - Edge WebSocket (bound to 5G SA slice when available): immediate 0ms delta push.
     * - Firestore `liveMatches/{matchId}`: single-flight coalesced background IO writer
     *   (never fires overlapping document writes, never runs on the UI thread, and never
     *   pollutes the Ping HUD with Firestore write-lock contention).
     */
    private fun requestRealtimeSync(immediate: Boolean) {
        val s = _state.value
        if (s.matchId.isBlank() || s.matchId.startsWith("local_")) return

        if (edgeConnected && !s.isBotMatch) {
            if (edgeWsClient.sendPlayerState(s.matchId, s.localPlayer.toPlayerLiveState())) {
                rttCalculator.recordPacketEvent()
                publishTelemetrySnapshot(isLocalFallback = false)
            }
        }

        val now = System.currentTimeMillis()
        val elapsedSincePush = now - lastOutboundPushMs
        // Bot matches only need periodic cloud snapshots for live spectators; PvP uses coalesced 450ms cadence.
        val minIntervalMs = when {
            s.isBotMatch -> 2500L
            edgeConnected -> 1200L
            else -> 450L
        }

        if (immediate || elapsedSincePush >= minIntervalMs) {
            throttledSyncJob?.cancel()
            scheduleSingleFlightFirestorePush()
        } else if (throttledSyncJob?.isActive != true) {
            val waitMs = (minIntervalMs - elapsedSincePush).coerceAtLeast(100L)
            throttledSyncJob = scope.launch(Dispatchers.IO) {
                delay(waitMs)
                scheduleSingleFlightFirestorePush()
            }
        }
    }

    /** Ensures at most ONE Firestore document write is in flight at any moment; coalesces rapid moves. */
    private fun scheduleSingleFlightFirestorePush() {
        if (firestoreWriteInFlight) {
            firestoreWriteDirty = true
            return
        }
        firestoreWriteInFlight = true
        scope.launch(Dispatchers.IO) {
            try {
                do {
                    firestoreWriteDirty = false
                    lastOutboundPushMs = System.currentTimeMillis()
                    val snap = _state.value
                    if (!snap.isActive || snap.isFinished) break
                    pushSessionSnapshotToFirestore(finished = false, winnerUid = "", snapshot = snap)
                } while (firestoreWriteDirty && _state.value.isActive && !_state.value.isFinished)
            } finally {
                firestoreWriteInFlight = false
            }
        }
    }

    private suspend fun pushSessionSnapshotToFirestore(
        finished: Boolean,
        winnerUid: String,
        snapshot: GameSessionState = _state.value
    ) {
        val s = snapshot
        if (s.matchId.isBlank() || s.matchId.startsWith("local_")) return

        val myLive = s.localPlayer.toPlayerLiveState()
        val oppLive = if (s.isBotMatch) s.opponentPlayer.toPlayerLiveState() else null

        val result = repository.updateLiveMatchPlayers(
            matchId = s.matchId,
            status = if (finished) "FINISHED" else "ACTIVE",
            winnerUid = winnerUid,
            player1 = if (s.amIPlayer1) myLive else oppLive,
            player2 = if (s.amIPlayer1) oppLive else myLive
        )
        val targetStatus = if (result.isSuccess) SessionSyncStatus.LIVE_SYNCED else SessionSyncStatus.LOCAL_FALLBACK
        // Only emit a state update when syncStatus actually changes so UI doesn't recompose on every background write
        if (_state.value.syncStatus != targetStatus) {
            _state.value = _state.value.copy(
                syncStatus = targetStatus,
                lastCloudSyncMs = System.currentTimeMillis()
            )
        }
    }

    private fun recordReplayFrame(eventText: String) {
        val s = _state.value
        val elapsed = (s.totalDurationSec - s.secondsRemaining).coerceAtLeast(0)
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer

        val frame = ReplayFrame(
            elapsedSec = elapsed,
            p1Board = p1.boardFlat,
            p2Board = p2.boardFlat,
            p1BossHp = p1.bossHp,
            p2BossHp = p2.bossHp,
            p1Wave = p1.waveIndex,
            p2Wave = p2.waveIndex,
            p1Damage = p1.totalDamage,
            p2Damage = p2.totalDamage,
            p1Restoration = p1.restorationPct,
            p2Restoration = p2.restorationPct,
            p1Charges = p1.skillCharges,
            p2Charges = p2.skillCharges,
            eventText = eventText
        )
        if (recordedFrames.size < 44) {
            recordedFrames.add(frame)
        }
    }

    /**
     * Ends the match exactly once. It never suspends: the jobs that may be calling it (timer,
     * live observer, swap animation) are cancelled, and persistence runs in its own
     * non-cancellable coroutine so rewards and the replay are always written.
     */
    private fun concludeSession(won: Boolean, publish: Boolean = true) {
        val s = _state.value
        if (s.isFinished) return

        val profile = activeProfile
        val winnerUid = if (won) (profile?.userId ?: "player") else s.opponentPlayer.uid.ifBlank { "rival" }
        val winnerName = if (won) s.localPlayer.name else s.opponentPlayer.name
        if (won) soundPlayer.playVictory()

        recordReplayFrame("$winnerName purified ${GameCatalog.placeById(s.placeId).name} and won!")
        val finishedState = s.copy(isActive = false, isFinished = true, awaitingResult = false, wonMatch = won)
        _state.value = finishedState
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        swapAnimJob?.cancel()
        resultGraceJob?.cancel()
        linkObserverJob?.cancel()
        rttProbeJob?.cancel()
        if (publish && edgeConnected) {
            edgeWsClient.sendFinish(s.matchId, winnerUid)
        }
        edgeWsClient.disconnect()
        edgeConnected = false
        onFinished(won)

        val frames = recordedFrames.toList()
        val matchedByType = tilesMatchedByType.toMap()

        scope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                if (publish) pushSessionSnapshotToFirestore(finished = true, winnerUid = winnerUid, snapshot = finishedState)

                // Persist Battle Replay for Spectator & Friends playback
                var savedReplayId: String? = null
                if (profile != null && (s.amIPlayer1 || s.isBotMatch)) {
                    savedReplayId = repository.saveReplay(buildReplay(s, profile.userId, winnerUid, frames)).getOrNull()
                }

                // Persist Trophies, Eco-Coins, Boss card & Daily Quest telemetry
                if (profile != null) {
                    val wavesCleared = (s.localPlayer.waveIndex - 1).coerceAtLeast(0) + if (s.localPlayer.bossHp <= 0) 1 else 0
                    val telemetry = MatchTelemetry(
                        won = won,
                        arenaId = s.arenaId,
                        placeId = s.placeId,
                        bossId = s.sharedBossId,
                        totalDamage = s.localPlayer.totalDamage,
                        restorationPct = s.localPlayer.restorationPct,
                        wavesCleared = wavesCleared,
                        abilitiesUsed = s.abilitiesUsedCount,
                        maxCombo = s.maxCombo,
                        totalTilesMatched = s.totalTilesMatched,
                        tilesMatchedByType = matchedByType,
                        deckBossIds = s.localPlayer.deckBossIds
                    )
                    val reward = repository.applyMatchResult(profile, telemetry).getOrNull()
                    if (_state.value.matchId == s.matchId) {
                        _state.value = _state.value.copy(rewardOutcome = reward, replayId = savedReplayId)
                    }
                }
            }
        }
    }

    private fun buildReplay(s: GameSessionState, creatorUid: String, winnerUid: String, frames: List<ReplayFrame>): MatchReplay {
        val p1 = if (s.amIPlayer1) s.localPlayer else s.opponentPlayer
        val p2 = if (s.amIPlayer1) s.opponentPlayer else s.localPlayer
        fun summary(p: PlayerBattleSnapshot) = ReplaySummary(
            uid = p.uid,
            name = p.name,
            trophies = p.trophies,
            deckBossIds = p.deckBossIds,
            deckBossLevels = p.deckBossLevels,
            totalDamage = p.totalDamage,
            restorationPct = p.restorationPct
        )
        return MatchReplay(
            creatorUid = creatorUid,
            participantUids = listOf(creatorUid, s.opponentPlayer.uid.ifBlank { "rival_uid" }),
            replayPublic = true,
            arenaId = s.arenaId,
            placeId = s.placeId,
            bossId = s.sharedBossId,
            trophyBracket = s.arenaId,
            winnerUid = winnerUid,
            durationSec = (s.totalDurationSec - s.secondsRemaining).coerceIn(1, 300),
            player1Summary = summary(p1),
            player2Summary = summary(p2),
            frames = frames.ifEmpty { listOf(ReplayFrame(elapsedSec = 1, p1Board = p1.boardFlat, p2Board = p2.boardFlat)) }
        )
    }

    fun stopSession() {
        timerLoopJob?.cancel()
        liveObserverJob?.cancel()
        throttledSyncJob?.cancel()
        swapAnimJob?.cancel()
        resultGraceJob?.cancel()
        linkObserverJob?.cancel()
        rttProbeJob?.cancel()
        edgeWsClient.disconnect()
        edgeConnected = false
        edgeBoundToSlice = false
        queuedSwap = null
        queuedSkillSlot = null
        firestoreWriteInFlight = false
        firestoreWriteDirty = false
        _state.value = GameSessionState(soundEnabled = soundPlayer.soundEnabled)
    }

    fun release() {
        stopSession()
        soundPlayer.release()
    }
}
