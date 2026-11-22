package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsStore
import com.example.data.WasteCrushRepository
import com.example.engine.*
import com.example.model.*
import com.example.network.FiveGNetworkManager
import com.example.network.LatencyTelemetryState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class MatchmakingPhase {
    IDLE,
    SEARCHING,
    ROOM_HOSTING,
    ROULETTE,
    IN_BATTLE,
    FINISHED
}

data class MatchmakingUiState(
    val phase: MatchmakingPhase = MatchmakingPhase.IDLE,
    val arenaId: Int = 0,
    val searchElapsedSec: Int = 0,
    val searchTimeoutSec: Int = 25,
    val trophyRangeLabel: String = "±150 Trophies",
    val roomCode: String = "",
    val edgeServerUrl: String = "",
    val rouletteHighlightIndex: Int = 0,
    val rouletteSpinToken: Long = 0L,
    val rouletteLocked: Boolean = false,
    val lockedPlaceId: String = "place_hammamet",
    val sharedBossId: String = "boss_b1",
    val isBotMatch: Boolean = false,
    val myName: String = "You",
    val myTrophies: Int = 0,
    val myDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val opponentName: String = "Searching...",
    val opponentTrophies: Int = 0,
    val opponentDeckIds: List<String> = listOf("boss_b1", "boss_b2", "boss_b3"),
    val errorMessage: String? = null
)

/**
 * Coordinates online 1v1 matchmaking (Trophy Quick Match + 6-character Private Room Code Lobby)
 * via Firebase Firestore, spins the 5-place Tunisian Arena Roulette, and launches [GameStateManager]
 * with live 5G SA telemetry and optional slice-bound Edge WebSocket transport.
 */
class MatchmakingViewModel(
    private val repository: WasteCrushRepository,
    private val fiveGNetworkManager: FiveGNetworkManager? = null
) : ViewModel() {

    val gameStateManager = GameStateManager(
        repository = repository,
        scope = viewModelScope,
        fiveGNetworkManager = fiveGNetworkManager
    )

    private val _matchmakingState = MutableStateFlow(MatchmakingUiState())
    val matchmakingState: StateFlow<MatchmakingUiState> = _matchmakingState.asStateFlow()

    val sessionState: StateFlow<GameSessionState> = gameStateManager.state
    val telemetryState: StateFlow<LatencyTelemetryState> = gameStateManager.telemetry

    private var matchmakingJob: Job? = null
    private var queueObserverJob: Job? = null
    private var roomObserverJob: Job? = null
    private var activeHostedRoomCode: String? = null

    private val tunisianRivalNames = listOf(
        "Amine_TN", "Yasmine_Eco", "Sami_Carthage", "Nour_Djerba",
        "Mehdi_Sfax", "Ines_Hammamet", "Karim_Tozeur", "Salma_Bizerte"
    )

    /** Online Trophy Quick Match (waits up to 25s for a real player; user can also tap Practice Bot). */
    fun startMatchmaking(
        profile: UserProfile,
        edgeServerUrl: String = SettingsStore.current.edgeServerUrl
    ) {
        cancelAndReset()
        val arena = GameCatalog.arenaForTrophies(profile.trophies)

        _matchmakingState.value = MatchmakingUiState(
            phase = MatchmakingPhase.SEARCHING,
            arenaId = arena.id,
            searchElapsedSec = 0,
            searchTimeoutSec = 25,
            trophyRangeLabel = "±150 Trophies",
            edgeServerUrl = edgeServerUrl.trim(),
            myName = profile.displayName,
            myTrophies = profile.trophies,
            myDeckIds = profile.equippedDeckIds,
            opponentName = "Searching ${arena.name}..."
        )

        matchmakingJob = viewModelScope.launch {
            if (repository.isGuestMode()) {
                for (sec in 1..2) {
                    delay(800L)
                    _matchmakingState.value = _matchmakingState.value.copy(
                        searchElapsedSec = sec,
                        trophyRangeLabel = "Offline Practice • AI Rival"
                    )
                }
                launchTrophyScaledRivalMatch(profile, arena)
                return@launch
            }

            val enqueueRes = repository.enqueueForMatchmaking(profile, arena.id)
            if (enqueueRes.isFailure) {
                _matchmakingState.value = _matchmakingState.value.copy(
                    errorMessage = enqueueRes.exceptionOrNull()?.localizedMessage ?: "Unable to reach matchmaking queue."
                )
                delay(1500L)
                launchTrophyScaledRivalMatch(profile, arena)
                return@launch
            }
            val foundTicket = enqueueRes.getOrNull()

            if (foundTicket != null) {
                launchHumanMatchAsCreator(profile, foundTicket, arena, edgeServerUrl.trim())
                return@launch
            }

            var pairedExternally = false
            queueObserverJob = launch {
                repository.observeMyQueueTicket()
                    .catch { err ->
                        val fsErr = generateSequence(err) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && err is CancellationException) throw err
                    }
                    .collect { ticket ->
                        if (ticket != null && ticket.status == "MATCHED" && ticket.matchId.isNotBlank() && !pairedExternally) {
                            pairedExternally = true
                            joinExistingHumanMatch(profile, ticket.matchId, amIPlayer1 = false, edgeServerUrl = edgeServerUrl.trim())
                        }
                    }
            }

            // Wait up to 25 seconds for a live online player before offering / launching Practice Bot
            var waited = 0
            while (waited < 25 && !pairedExternally) {
                delay(1000L)
                waited++
                val rangeLabel = when {
                    waited <= 6 -> "±150 Trophies"
                    waited <= 14 -> "±250 Trophies"
                    else -> "±300 Trophies (Arena Pool)"
                }
                _matchmakingState.value = _matchmakingState.value.copy(
                    searchElapsedSec = waited,
                    trophyRangeLabel = rangeLabel
                )
            }

            if (!pairedExternally) {
                queueObserverJob?.cancel()
                repository.leaveMatchmakingQueue()
                launchTrophyScaledRivalMatch(profile, arena)
            }
        }
    }

    /** Explicit solo Practice vs AI Bot match (or skip Quick Match wait immediately). */
    fun launchPracticeBotNow(profile: UserProfile) {
        val arena = GameCatalog.arenaForTrophies(profile.trophies)
        matchmakingJob?.cancel()
        queueObserverJob?.cancel()
        roomObserverJob?.cancel()
        viewModelScope.launch {
            repository.leaveMatchmakingQueue()
            activeHostedRoomCode?.let { code ->
                activeHostedRoomCode = null
                repository.cancelPrivateRoom(code)
            }
            _matchmakingState.value = MatchmakingUiState(
                phase = MatchmakingPhase.SEARCHING,
                arenaId = arena.id,
                trophyRangeLabel = "Practice Bot",
                myName = profile.displayName,
                myTrophies = profile.trophies,
                myDeckIds = profile.equippedDeckIds,
                opponentName = "Launching Practice Bot..."
            )
            launchTrophyScaledRivalMatch(profile, arena)
        }
    }

    /** Creates a 6-character Private Room Code in Firestore and waits for a second player to join. */
    fun createPrivateRoom(
        profile: UserProfile,
        edgeServerUrl: String = SettingsStore.current.edgeServerUrl,
        onError: (String) -> Unit = {}
    ) {
        cancelAndReset()
        val arena = GameCatalog.arenaForTrophies(profile.trophies)
        val cleanEdge = edgeServerUrl.trim()

        _matchmakingState.value = MatchmakingUiState(
            phase = MatchmakingPhase.ROOM_HOSTING,
            arenaId = arena.id,
            roomCode = "...",
            edgeServerUrl = cleanEdge,
            trophyRangeLabel = "Private 1v1 Room",
            myName = profile.displayName,
            myTrophies = profile.trophies,
            myDeckIds = profile.equippedDeckIds,
            opponentName = "Waiting for Friend..."
        )

        matchmakingJob = viewModelScope.launch {
            val created = repository.createPrivateRoom(profile, arena.id, cleanEdge)
            val room = created.getOrNull()
            if (room == null) {
                val msg = created.exceptionOrNull()?.localizedMessage ?: "Failed to create Private Room."
                _matchmakingState.value = MatchmakingUiState(errorMessage = msg)
                onError(msg)
                return@launch
            }

            activeHostedRoomCode = room.roomCode
            _matchmakingState.value = _matchmakingState.value.copy(
                roomCode = room.roomCode,
                edgeServerUrl = room.edgeServerUrl
            )

            var paired = false
            roomObserverJob = launch {
                repository.observePrivateRoom(room.roomCode)
                    .catch { err ->
                        val fsErr = generateSequence(err) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && err is CancellationException) throw err
                    }
                    .collect { updated ->
                        if (updated != null && updated.status == "MATCHED" && updated.matchId.isNotBlank() && !paired) {
                            paired = true
                            activeHostedRoomCode = null
                            joinExistingHumanMatch(
                                profile = profile,
                                matchId = updated.matchId,
                                amIPlayer1 = true,
                                edgeServerUrl = updated.edgeServerUrl.ifBlank { cleanEdge }
                            )
                        }
                    }
            }

            // Live elapsed counter while hosting
            var elapsed = 0
            while (!paired && _matchmakingState.value.phase == MatchmakingPhase.ROOM_HOSTING) {
                delay(1000L)
                elapsed++
                _matchmakingState.value = _matchmakingState.value.copy(searchElapsedSec = elapsed)
            }
        }
    }

    /** Joins an existing 6-character Private Room Code via atomic Firestore transaction. */
    fun joinPrivateRoom(
        profile: UserProfile,
        rawCode: String,
        onError: (String) -> Unit
    ) {
        cancelAndReset()
        val arena = GameCatalog.arenaForTrophies(profile.trophies)
        _matchmakingState.value = MatchmakingUiState(
            phase = MatchmakingPhase.SEARCHING,
            arenaId = arena.id,
            trophyRangeLabel = "Joining Room ${rawCode.trim().uppercase()}...",
            myName = profile.displayName,
            myTrophies = profile.trophies,
            myDeckIds = profile.equippedDeckIds,
            opponentName = "Connecting to Host..."
        )

        matchmakingJob = viewModelScope.launch {
            val res = repository.joinPrivateRoom(profile, rawCode)
            val joined = res.getOrNull()
            if (joined == null) {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "Unable to join room."
                _matchmakingState.value = MatchmakingUiState(errorMessage = msg)
                onError(msg)
                return@launch
            }

            val match = joined.liveMatch
            val matchArena = GameCatalog.arenaById(match.arenaId)
            val place = GameCatalog.placeById(match.placeId)
            val boss = GameCatalog.bossById(match.bossId)
            val myBoard = if (match.player2.boardFlat.size == 49) {
                Match3Engine.decodeBoardFlat(match.player2.boardFlat)
            } else Match3Engine.createInitialBoard()
            val oppBoard = if (match.player1.boardFlat.size == 49) {
                Match3Engine.decodeBoardFlat(match.player1.boardFlat)
            } else Match3Engine.createInitialBoard()

            val effectiveEdge = joined.edgeServerUrl.ifBlank { SettingsStore.current.edgeServerUrl }
            spinPlaceRouletteAndLaunchSession(
                GameSessionConfig(
                    matchId = joined.matchId,
                    profile = profile,
                    arena = matchArena,
                    place = place,
                    sharedBoss = boss,
                    amIPlayer1 = false,
                    isBotMatch = false,
                    initialLocalBoard = myBoard,
                    initialOpponentBoard = oppBoard,
                    opponentUid = match.player1.uid,
                    opponentName = match.player1.name,
                    opponentTrophies = match.player1.trophies,
                    opponentDeckIds = match.player1.deckBossIds,
                    opponentDeckLevels = match.player1.deckBossLevels,
                    edgeServerUrl = effectiveEdge
                )
            )
        }
    }

    private suspend fun launchHumanMatchAsCreator(
        profile: UserProfile,
        opponent: MatchmakingTicket,
        arena: ArenaDefinition,
        edgeServerUrl: String
    ) {
        val chosenPlace = arena.places.random()
        val chosenBoss = GameCatalog.cumulativeBossPoolForArena(arena.id).random()
        val myBoard = Match3Engine.createInitialBoard()
        val oppBoard = Match3Engine.createInitialBoard()
        val myLevels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }

        val scaledBossHp = BattleRules.scaledBossHp(chosenBoss.bossBattleHp)

        val p1 = PlayerLiveState(
            uid = profile.userId,
            name = profile.displayName,
            trophies = profile.trophies,
            deckBossIds = profile.equippedDeckIds,
            deckBossLevels = myLevels,
            waveIndex = 1,
            bossHp = scaledBossHp,
            bossMaxHp = scaledBossHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(myBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )
        val p2 = PlayerLiveState(
            uid = opponent.userId,
            name = opponent.displayName,
            trophies = opponent.trophies,
            deckBossIds = opponent.deckBossIds,
            deckBossLevels = opponent.deckBossLevels,
            waveIndex = 1,
            bossHp = scaledBossHp,
            bossMaxHp = scaledBossHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )

        val liveMatch = LiveMatch(
            creatorUid = profile.userId,
            participantUids = listOf(profile.userId, opponent.userId),
            arenaId = arena.id,
            placeId = chosenPlace.id,
            bossId = chosenBoss.id,
            trophyBracket = arena.id,
            status = "ACTIVE",
            durationSec = BattleRules.MATCH_DURATION_SEC,
            spectatorCount = 0,
            botMatch = false,
            winnerUid = "",
            player1 = p1,
            player2 = p2
        )

        val matchId = repository.createMatchAndClaimTicket(liveMatch, opponent.userId).getOrNull()
        if (matchId == null) {
            launchTrophyScaledRivalMatch(profile, arena)
            return
        }

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = chosenPlace,
                sharedBoss = chosenBoss,
                amIPlayer1 = true,
                isBotMatch = false,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = opponent.userId,
                opponentName = opponent.displayName,
                opponentTrophies = opponent.trophies,
                opponentDeckIds = opponent.deckBossIds,
                opponentDeckLevels = opponent.deckBossLevels,
                edgeServerUrl = edgeServerUrl
            )
        )
    }

    private suspend fun joinExistingHumanMatch(
        profile: UserProfile,
        matchId: String,
        amIPlayer1: Boolean,
        edgeServerUrl: String
    ) {
        repository.leaveMatchmakingQueue()
        val match = repository.observeLiveMatch(matchId)
            .catch { err ->
                val fsErr = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && err is CancellationException) throw err
            }
            .firstOrNull { it != null } ?: return

        val arena = GameCatalog.arenaById(match.arenaId)
        val place = GameCatalog.placeById(match.placeId)
        val boss = GameCatalog.bossById(match.bossId)
        val myState = if (amIPlayer1) match.player1 else match.player2
        val oppState = if (amIPlayer1) match.player2 else match.player1
        val myBoard = if (myState.boardFlat.size == 49) {
            Match3Engine.decodeBoardFlat(myState.boardFlat)
        } else Match3Engine.createInitialBoard()
        val oppBoard = if (oppState.boardFlat.size == 49) {
            Match3Engine.decodeBoardFlat(oppState.boardFlat)
        } else Match3Engine.createInitialBoard()

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = place,
                sharedBoss = boss,
                amIPlayer1 = amIPlayer1,
                isBotMatch = false,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = oppState.uid,
                opponentName = oppState.name,
                opponentTrophies = oppState.trophies,
                opponentDeckIds = oppState.deckBossIds,
                opponentDeckLevels = oppState.deckBossLevels,
                edgeServerUrl = edgeServerUrl
            )
        )
    }

    private suspend fun launchTrophyScaledRivalMatch(profile: UserProfile, arena: ArenaDefinition) {
        val chosenPlace = arena.places.random()
        val bossPool = GameCatalog.cumulativeBossPoolForArena(arena.id)
        val chosenBoss = bossPool.random()
        val myBoard = Match3Engine.createInitialBoard()
        val oppBoard = Match3Engine.createInitialBoard()
        val myLevels = profile.equippedDeckIds.map { profile.bossLevels[it] ?: 1 }

        val rivalUid = "rival_${arena.id}_${Random.nextInt(1000, 9999)}"
        val rivalName = tunisianRivalNames.random()
        val rivalTrophies = (profile.trophies + Random.nextInt(-45, 55)).coerceAtLeast(0)
        val rivalDeck = bossPool.shuffled().take(3).map { it.id }
        val rivalLevels = rivalDeck.map { (1 + arena.id + Random.nextInt(0, 2)).coerceAtMost(5) }

        val scaledBossHp = BattleRules.scaledBossHp(chosenBoss.bossBattleHp)

        val p1 = PlayerLiveState(
            uid = profile.userId,
            name = profile.displayName,
            trophies = profile.trophies,
            deckBossIds = profile.equippedDeckIds,
            deckBossLevels = myLevels,
            waveIndex = 1,
            bossHp = scaledBossHp,
            bossMaxHp = scaledBossHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(myBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )
        val p2 = PlayerLiveState(
            uid = rivalUid,
            name = rivalName,
            trophies = rivalTrophies,
            deckBossIds = rivalDeck,
            deckBossLevels = rivalLevels,
            waveIndex = 1,
            bossHp = scaledBossHp,
            bossMaxHp = scaledBossHp,
            totalDamage = 0,
            restorationPct = 0,
            boardFlat = Match3Engine.encodeBoardFlat(oppBoard),
            skillCharges = listOf(0, 0, 0),
            lastAbility = ""
        )

        val liveMatch = LiveMatch(
            creatorUid = profile.userId,
            participantUids = listOf(profile.userId, rivalUid),
            arenaId = arena.id,
            placeId = chosenPlace.id,
            bossId = chosenBoss.id,
            trophyBracket = arena.id,
            status = "ACTIVE",
            durationSec = BattleRules.MATCH_DURATION_SEC,
            spectatorCount = 0,
            botMatch = true,
            winnerUid = "",
            player1 = p1,
            player2 = p2
        )

        val matchId = repository.createLiveMatch(liveMatch).getOrNull() ?: "local_${System.currentTimeMillis()}"

        spinPlaceRouletteAndLaunchSession(
            GameSessionConfig(
                matchId = matchId,
                profile = profile,
                arena = arena,
                place = chosenPlace,
                sharedBoss = chosenBoss,
                amIPlayer1 = true,
                isBotMatch = true,
                initialLocalBoard = myBoard,
                initialOpponentBoard = oppBoard,
                opponentUid = rivalUid,
                opponentName = rivalName,
                opponentTrophies = rivalTrophies,
                opponentDeckIds = rivalDeck,
                opponentDeckLevels = rivalLevels
            )
        )
    }

    private suspend fun spinPlaceRouletteAndLaunchSession(config: GameSessionConfig) {
        val targetIndex = config.arena.places.indexOfFirst { it.id == config.place.id }.coerceAtLeast(0)
        val spinToken = System.currentTimeMillis()

        _matchmakingState.value = _matchmakingState.value.copy(
            phase = MatchmakingPhase.ROULETTE,
            arenaId = config.arena.id,
            rouletteHighlightIndex = targetIndex,
            rouletteSpinToken = spinToken,
            rouletteLocked = false,
            lockedPlaceId = config.place.id,
            sharedBossId = config.sharedBoss.id,
            isBotMatch = config.isBotMatch,
            opponentName = config.opponentName,
            opponentTrophies = config.opponentTrophies,
            opponentDeckIds = config.opponentDeckIds
        )

        // Synchronized with PlaceRouletteWheel's 3100ms physics deceleration curve
        delay(3150L)
        _matchmakingState.value = _matchmakingState.value.copy(rouletteLocked = true)
        gameStateManager.soundPlayer.playSkillReady()
        delay(950L)

        _matchmakingState.value = _matchmakingState.value.copy(phase = MatchmakingPhase.IN_BATTLE)
        gameStateManager.startSession(config) {
            _matchmakingState.value = _matchmakingState.value.copy(phase = MatchmakingPhase.FINISHED)
        }
    }

    fun playRouletteTick() = gameStateManager.soundPlayer.playRouletteTick()

    fun playRouletteLocked() = gameStateManager.soundPlayer.playSkillReady()

    fun onTileTapped(pos: GridPos) = gameStateManager.onTileTapped(pos)

    fun onTileSwiped(from: GridPos, to: GridPos) = gameStateManager.onTileSwiped(from, to)

    fun activateEcoDeckBoss(slotIndex: Int) = gameStateManager.activateEcoDeckSlot(slotIndex)

    fun toggleSound() = gameStateManager.toggleSound()

    fun cancelAndReset() {
        matchmakingJob?.cancel()
        queueObserverJob?.cancel()
        roomObserverJob?.cancel()
        gameStateManager.stopSession()
        val hostedCode = activeHostedRoomCode
        activeHostedRoomCode = null
        viewModelScope.launch {
            repository.leaveMatchmakingQueue()
            if (hostedCode != null) {
                repository.cancelPrivateRoom(hostedCode)
            }
        }
        _matchmakingState.value = MatchmakingUiState()
    }

    override fun onCleared() {
        super.onCleared()
        gameStateManager.release()
    }

    companion object {
        fun factory(
            repository: WasteCrushRepository,
            fiveGNetworkManager: FiveGNetworkManager? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MatchmakingViewModel(repository, fiveGNetworkManager) as T
                }
            }
    }
}
