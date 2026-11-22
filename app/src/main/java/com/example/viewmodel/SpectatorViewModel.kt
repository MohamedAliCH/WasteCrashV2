package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WasteCrushRepository
import com.example.engine.BoardTile
import com.example.engine.Match3Engine
import com.example.model.LiveMatch
import com.example.model.MatchReplay
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class SpectatorPerspective {
    PLAYER_1,
    OPPONENT,
    DUAL_SPLIT
}

data class SpectatorTheatreState(
    val isLoading: Boolean = true,
    val isLiveMode: Boolean = false,
    val errorMessage: String? = null,
    val arenaId: Int = 0,
    val placeId: String = "place_hammamet",
    val bossId: String = "boss_b1",
    val statusText: String = "LIVE",
    val spectatorCount: Int = 1,
    val perspective: SpectatorPerspective = SpectatorPerspective.PLAYER_1,
    val showDeckInspector: Boolean = false,
    val cheerBanner: String = "",
    // Player 1
    val p1Name: String = "Player 1",
    val p1Trophies: Int = 0,
    val p1DeckIds: List<String> = emptyList(),
    val p1DeckLevels: List<Int> = emptyList(),
    val p1Board: List<List<BoardTile>> = Match3Engine.createInitialBoard(),
    val p1BossHp: Int = 1500,
    val p1BossMaxHp: Int = 1500,
    val p1Wave: Int = 1,
    val p1Damage: Int = 0,
    val p1RestorationPct: Int = 0,
    val p1Charges: List<Int> = listOf(0, 0, 0),
    val p1LastAbility: String = "",
    // Player 2 (Opponent)
    val p2Name: String = "Opponent",
    val p2Trophies: Int = 0,
    val p2DeckIds: List<String> = emptyList(),
    val p2DeckLevels: List<Int> = emptyList(),
    val p2Board: List<List<BoardTile>> = Match3Engine.createInitialBoard(),
    val p2BossHp: Int = 1500,
    val p2BossMaxHp: Int = 1500,
    val p2Wave: Int = 1,
    val p2Damage: Int = 0,
    val p2RestorationPct: Int = 0,
    val p2Charges: List<Int> = listOf(0, 0, 0),
    val p2LastAbility: String = "",
    // Replay Playback
    val replayFramesCount: Int = 0,
    val currentFrameIndex: Int = 0,
    val currentElapsedSec: Int = 0,
    val totalDurationSec: Int = 90,
    val isPlayingReplay: Boolean = true,
    val playbackSpeed: Float = 1f,
    val currentEventText: String = "",
    val winnerUid: String = "",
    val p1Uid: String = "",
    val p2Uid: String = ""
)

class SpectatorViewModel(
    private val repository: WasteCrushRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SpectatorTheatreState())
    val state: StateFlow<SpectatorTheatreState> = _state.asStateFlow()

    private var liveJob: Job? = null
    private var replayJob: Job? = null
    private var loadedReplay: MatchReplay? = null

    fun openReplay(replayId: String) {
        stopWatching()
        _state.value = SpectatorTheatreState(isLoading = true, isLiveMode = false)

        replayJob = viewModelScope.launch {
            val res = repository.getReplayById(replayId)
            val replay = res.getOrNull()
            if (replay == null || replay.frames.isEmpty()) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Replay not found."
                )
                return@launch
            }
            loadedReplay = replay
            applyReplayFrame(replay, 0)
            startReplayTicker()
        }
    }

    private fun applyReplayFrame(replay: MatchReplay, index: Int) {
        val safeIdx = index.coerceIn(0, replay.frames.lastIndex)
        val frame = replay.frames[safeIdx]
        val bossMax = com.example.model.GameCatalog.bossById(replay.bossId).bossBattleHp

        _state.value = _state.value.copy(
            isLoading = false,
            isLiveMode = false,
            errorMessage = null,
            arenaId = replay.arenaId,
            placeId = replay.placeId,
            bossId = replay.bossId,
            statusText = "REPLAY",
            p1Uid = replay.player1Summary.uid,
            p1Name = replay.player1Summary.name,
            p1Trophies = replay.player1Summary.trophies,
            p1DeckIds = replay.player1Summary.deckBossIds,
            p1DeckLevels = replay.player1Summary.deckBossLevels,
            p1Board = Match3Engine.decodeBoardFlat(frame.p1Board),
            p1BossHp = frame.p1BossHp,
            p1BossMaxHp = bossMax,
            p1Wave = frame.p1Wave,
            p1Damage = frame.p1Damage,
            p1RestorationPct = frame.p1Restoration,
            p1Charges = frame.p1Charges,
            p2Uid = replay.player2Summary.uid,
            p2Name = replay.player2Summary.name,
            p2Trophies = replay.player2Summary.trophies,
            p2DeckIds = replay.player2Summary.deckBossIds,
            p2DeckLevels = replay.player2Summary.deckBossLevels,
            p2Board = Match3Engine.decodeBoardFlat(frame.p2Board),
            p2BossHp = frame.p2BossHp,
            p2BossMaxHp = bossMax,
            p2Wave = frame.p2Wave,
            p2Damage = frame.p2Damage,
            p2RestorationPct = frame.p2Restoration,
            p2Charges = frame.p2Charges,
            replayFramesCount = replay.frames.size,
            currentFrameIndex = safeIdx,
            currentElapsedSec = frame.elapsedSec,
            totalDurationSec = replay.durationSec,
            currentEventText = frame.eventText,
            winnerUid = replay.winnerUid
        )
    }

    private fun startReplayTicker() {
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            while (true) {
                val s = _state.value
                val replay = loadedReplay ?: break
                if (s.isPlayingReplay) {
                    val stepDelay = (1400L / s.playbackSpeed).toLong().coerceAtLeast(350L)
                    delay(stepDelay)
                    val cur = _state.value
                    if (!cur.isPlayingReplay) continue
                    val nextIdx = cur.currentFrameIndex + 1
                    if (nextIdx < replay.frames.size) {
                        applyReplayFrame(replay, nextIdx)
                    } else {
                        _state.value = cur.copy(isPlayingReplay = false)
                    }
                } else {
                    delay(250L)
                }
            }
        }
    }

    fun setPerspective(perspective: SpectatorPerspective) {
        _state.value = _state.value.copy(perspective = perspective)
    }

    fun toggleDeckInspector() {
        _state.value = _state.value.copy(showDeckInspector = !_state.value.showDeckInspector)
    }

    fun toggleReplayPlayPause() {
        val replay = loadedReplay ?: return
        val cur = _state.value
        if (!cur.isPlayingReplay && cur.currentFrameIndex >= replay.frames.lastIndex) {
            applyReplayFrame(replay, 0)
        }
        _state.value = _state.value.copy(isPlayingReplay = !cur.isPlayingReplay)
    }

    fun cycleReplaySpeed() {
        val next = when (_state.value.playbackSpeed) {
            1f -> 2f
            2f -> 0.5f
            else -> 1f
        }
        _state.value = _state.value.copy(playbackSpeed = next)
    }

    fun scrubToFrame(index: Int) {
        val replay = loadedReplay ?: return
        applyReplayFrame(replay, index)
    }

    fun sendCheer(label: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(cheerBanner = label)
            delay(1800L)
            _state.value = _state.value.copy(cheerBanner = "")
        }
    }

    fun stopWatching() {
        liveJob?.cancel()
        replayJob?.cancel()
        loadedReplay = null
    }

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }

    companion object {
        fun factory(repository: WasteCrushRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SpectatorViewModel(repository) as T
                }
            }
    }
}
