package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.QuestClaimOutcome
import com.example.data.WasteCrushRepository
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: WasteCrushRepository
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _claimedOutcome = MutableStateFlow<QuestClaimOutcome?>(null)
    val claimedOutcome: StateFlow<QuestClaimOutcome?> = _claimedOutcome.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile().onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Unable to initialize profile."
            }
        }
    }

    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile()
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            if (profile != null) UiState.Success(profile) else UiState.Loading
        }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val publicProfilesState: StateFlow<UiState<List<PublicProfile>>> = repository.observePublicProfiles()
        .map<List<PublicProfile>, UiState<List<PublicProfile>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load players"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val replaysState: StateFlow<UiState<List<MatchReplay>>> = repository.observePublicReplays()
        .map<List<MatchReplay>, UiState<List<MatchReplay>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load replays"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val clansState: StateFlow<List<EcoClan>> = repository.observeClans()
    val joinedClanId: StateFlow<String?> = repository.observeJoinedClanId()

    fun joinClan(clanId: String, profile: UserProfile) {
        repository.joinClan(clanId, profile)
        val clan = clansState.value.firstOrNull { it.id == clanId }
        _statusBanner.value = if (clan != null) "Joined ${clan.name} [${clan.tag}]!" else "Joined Eco-Clan!"
    }

    fun leaveClan(profile: UserProfile) {
        repository.leaveClan(profile)
        _statusBanner.value = "Left Eco-Clan."
    }

    fun createClan(
        name: String,
        tag: String,
        region: String,
        motto: String,
        profile: UserProfile
    ) {
        val created = repository.createClan(name, tag, region, motto, profile)
        _statusBanner.value = "Created ${created.name} [${created.tag}]!"
    }

    fun sendClanMessage(text: String, profile: UserProfile) {
        repository.sendClanMessage(text, profile)
    }

    val isGuest: Boolean get() = repository.isGuestMode()

    fun selectAvatar(avatarId: String) {
        viewModelScope.launch {
            repository.updateAvatar(avatarId)
                .onSuccess { _statusBanner.value = "Avatar updated: ${AvatarCatalog.byId(avatarId).name}" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Avatar is locked." }
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            repository.updateDisplayName(name)
                .onSuccess { _statusBanner.value = "Name changed to $it" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not change name." }
        }
    }

    fun resetGuestProgress() {
        if (!repository.isGuestMode()) return
        repository.resetGuestProfile()
        _statusBanner.value = "Guest progress reset."
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearClaimedOutcome() {
        _claimedOutcome.value = null
    }

    fun equipBossInSlot(profile: UserProfile, slotIndex: Int, bossId: String) {
        val level = profile.bossLevels[bossId] ?: 0
        if (level <= 0) {
            _statusBanner.value = "Capture this Boss in 1v1 battles to unlock it first!"
            return
        }
        val current = profile.equippedDeckIds.toMutableList()
        while (current.size < 3) current.add("boss_b1")
        val existingIdx = current.indexOf(bossId)
        if (existingIdx == slotIndex) return
        if (existingIdx >= 0) {
            val tmp = current[slotIndex]
            current[slotIndex] = bossId
            current[existingIdx] = tmp
        } else {
            current[slotIndex] = bossId
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateEquippedDeck(current)
                .onSuccess { _statusBanner.value = "${GameCatalog.bossById(bossId).name} equipped in Slot ${slotIndex + 1}!" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not update Eco-Deck." }
            _isBusy.value = false
        }
    }

    fun upgradeBoss(profile: UserProfile, bossId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.upgradeBoss(profile, bossId)
                .onSuccess { newLevel ->
                    val boss = GameCatalog.bossById(bossId)
                    _statusBanner.value = "${boss.name} upgraded to Level $newLevel!"
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Upgrade failed."
                }
            _isBusy.value = false
        }
    }

    fun claimQuest(profile: UserProfile, quest: QuestDefinition) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.claimQuestReward(profile, quest)
                .onSuccess { outcome ->
                    _claimedOutcome.value = outcome
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Cannot claim quest reward yet."
                }
            _isBusy.value = false
        }
    }

    fun addFriend(profile: UserProfile, friendCode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.addFriendByCode(profile, friendCode)
                .onSuccess { friend ->
                    _statusBanner.value = "Added ${friend.displayName} (${friend.friendCode}) to your Friends!"
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not add friend."
                }
            _isBusy.value = false
        }
    }

    fun removeFriend(profile: UserProfile, friendUid: String, friendName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.removeFriend(profile, friendUid)
                .onSuccess { _statusBanner.value = "Removed $friendName from Friends." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to remove friend." }
            _isBusy.value = false
        }
    }

    companion object {
        fun factory(repository: WasteCrushRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
    }
}
