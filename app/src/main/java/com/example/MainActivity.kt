package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuthManager
import com.example.data.SettingsStore
import com.example.model.AvatarCatalog
import com.example.data.WasteCrushRepository
import com.example.model.GameCatalog
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.network.EdgeWebSocketClient
import com.example.network.FiveGNetworkManager
import com.example.ui.components.MultiplayerLobbySheet
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            WasteCrushTheme {
                WasteCrushRoot()
            }
        }
    }
}

private enum class HubTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    BATTLE("Battle", Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_battle"),
    DECK("Decks", Icons.Filled.Style, Icons.Outlined.Style, "nav_decks"),
    ARENAS("Arenas", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_arenas"),
    ALBUM("Album", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_album"),
    SOCIAL("Social", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_social")
}

private sealed interface FullScreenOverlay {
    data object None : FullScreenOverlay
    data object BattleFlow : FullScreenOverlay
    data class TunisiaMap(val initialPlaceId: String? = null) : FullScreenOverlay
    data object Quests : FullScreenOverlay
    data class SpectatorReplay(val replayId: String) : FullScreenOverlay
}

@Composable
private fun WasteCrushRoot() {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var offlinePracticeMode by remember { mutableStateOf(false) }

    val fiveGNetworkManager = remember(context) { FiveGNetworkManager(context) }
    DisposableEffect(fiveGNetworkManager) {
        fiveGNetworkManager.start()
        onDispose { fiveGNetworkManager.stop() }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                offlinePracticeMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val signedInUid = currentUser?.uid
    if (signedInUid == null && !offlinePracticeMode) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                offlinePracticeMode = false
            },
            onContinueOfflinePractice = {
                offlinePracticeMode = true
            }
        )
    } else {
        val activeUid = signedInUid ?: WasteCrushRepository.GUEST_UID
        key(activeUid) {
            AuthenticatedWasteCrushApp(
                currentUserId = activeUid,
                fiveGNetworkManager = fiveGNetworkManager,
                onRequestOnlineSignIn = { offlinePracticeMode = false }
            )
        }
    }
}

@Composable
private fun AuthenticatedWasteCrushApp(
    currentUserId: String,
    fiveGNetworkManager: FiveGNetworkManager,
    onRequestOnlineSignIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val repository = remember(currentUserId) { WasteCrushRepository(context) }

    val mainVm: MainViewModel = viewModel(
        key = "main_$currentUserId",
        factory = MainViewModel.factory(repository)
    )
    val matchmakingVm: MatchmakingViewModel = viewModel(
        key = "matchmaking_$currentUserId",
        factory = MatchmakingViewModel.factory(repository, fiveGNetworkManager)
    )
    val spectatorVm: SpectatorViewModel = viewModel(
        key = "spectator_$currentUserId",
        factory = SpectatorViewModel.factory(repository)
    )

    val profileState by mainVm.profileState.collectAsStateWithLifecycle()
    val publicProfilesState by mainVm.publicProfilesState.collectAsStateWithLifecycle()
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val linkState by fiveGNetworkManager.linkState.collectAsStateWithLifecycle()
    val activeSliceNetwork by fiveGNetworkManager.activeSliceNetwork.collectAsStateWithLifecycle()
    var profilePanelTab by remember { mutableStateOf<ProfileTab?>(null) }
    var showLobbySheet by remember { mutableStateOf(false) }
    val replaysState by mainVm.replaysState.collectAsStateWithLifecycle()
    val clansState by mainVm.clansState.collectAsStateWithLifecycle()
    val joinedClanId by mainVm.joinedClanId.collectAsStateWithLifecycle()
    val statusBanner by mainVm.statusBanner.collectAsStateWithLifecycle()
    val claimedOutcome by mainVm.claimedOutcome.collectAsStateWithLifecycle()

    val matchmakingState by matchmakingVm.matchmakingState.collectAsStateWithLifecycle()
    val sessionState by matchmakingVm.sessionState.collectAsStateWithLifecycle()
    val telemetryState by matchmakingVm.telemetryState.collectAsStateWithLifecycle()
    val spectatorState by spectatorVm.state.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(HubTab.BATTLE) }
    var overlay by remember { mutableStateOf<FullScreenOverlay>(FullScreenOverlay.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainVm.clearBanner()
        }
    }

    // Handle full-screen immersive flows (Matchmaking, Battle, Post-Match, Spectator Theatre)
    when (val activeOverlay = overlay) {
        is FullScreenOverlay.BattleFlow -> {
            val currentProfile = (profileState as? UiState.Success)?.data
            when (matchmakingState.phase) {
                MatchmakingPhase.SEARCHING, MatchmakingPhase.ROOM_HOSTING, MatchmakingPhase.ROULETTE -> {
                    MatchmakingRouletteScreen(
                        state = matchmakingState,
                        linkState = linkState,
                        onPlayPracticeBot = {
                            if (currentProfile != null) {
                                matchmakingVm.launchPracticeBotNow(currentProfile)
                            }
                        },
                        onRouletteTick = matchmakingVm::playRouletteTick,
                        onCancel = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.IN_BATTLE -> {
                    BattleScreen(
                        state = sessionState,
                        telemetry = telemetryState,
                        onTileTap = matchmakingVm::onTileTapped,
                        onTileSwipe = matchmakingVm::onTileSwiped,
                        onActivateBossSlot = matchmakingVm::activateEcoDeckBoss,
                        onToggleSound = matchmakingVm::toggleSound,
                        onForfeit = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.FINISHED -> {
                    PostMatchScreen(
                        state = sessionState,
                        profile = currentProfile,
                        onPlayAgain = {
                            if (currentProfile != null) {
                                matchmakingVm.startMatchmaking(currentProfile)
                            }
                        },
                        onWatchReplay = { replayId ->
                            matchmakingVm.cancelAndReset()
                            spectatorVm.openReplay(replayId)
                            overlay = FullScreenOverlay.SpectatorReplay(replayId)
                        },
                        onOpenAlbum = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                            currentTab = HubTab.ALBUM
                        },
                        onAlbumCollectChime = matchmakingVm::playRouletteLocked,
                        onReturnHome = {
                            matchmakingVm.cancelAndReset()
                            overlay = FullScreenOverlay.None
                        }
                    )
                }
                MatchmakingPhase.IDLE -> {
                    overlay = FullScreenOverlay.None
                }
            }
            return
        }

        is FullScreenOverlay.TunisiaMap -> {
            val currentProfile = (profileState as? UiState.Success)?.data
            if (currentProfile != null) {
                TunisiaMapScreen(
                    profile = currentProfile,
                    initialPlaceId = activeOverlay.initialPlaceId,
                    onRouletteTick = matchmakingVm::playRouletteTick,
                    onRouletteLocked = matchmakingVm::playRouletteLocked,
                    onBack = { overlay = FullScreenOverlay.None },
                    onStartBattle = {
                        matchmakingVm.startMatchmaking(currentProfile)
                        overlay = FullScreenOverlay.BattleFlow
                    }
                )
            } else {
                overlay = FullScreenOverlay.None
            }
            return
        }

        is FullScreenOverlay.Quests -> {
            val currentProfile = (profileState as? UiState.Success)?.data
            if (currentProfile != null) {
                QuestsScreen(
                    profile = currentProfile,
                    claimedOutcome = claimedOutcome,
                    onClaimQuest = { quest -> mainVm.claimQuest(currentProfile, quest) },
                    onDismissClaimOutcome = mainVm::clearClaimedOutcome,
                    onBack = { overlay = FullScreenOverlay.None }
                )
            } else {
                overlay = FullScreenOverlay.None
            }
            return
        }

        is FullScreenOverlay.SpectatorReplay -> {
            SpectatorTheatreScreen(
                state = spectatorState,
                onSetPerspective = spectatorVm::setPerspective,
                onToggleDeckInspector = spectatorVm::toggleDeckInspector,
                onTogglePlayPause = spectatorVm::toggleReplayPlayPause,
                onCycleSpeed = spectatorVm::cycleReplaySpeed,
                onScrubFrame = spectatorVm::scrubToFrame,
                onSendCheer = spectatorVm::sendCheer,
                onBack = {
                    spectatorVm.stopWatching()
                    overlay = FullScreenOverlay.None
                }
            )
            return
        }

        FullScreenOverlay.None -> {
            // Render adaptive 5-tab Hub Shell
        }
    }

    val currentProfile = (profileState as? UiState.Success)?.data
    val unlockedPlacesCount = remember(currentProfile) {
        currentProfile?.purifiedPlaceIds?.distinct()?.size ?: 0
    }
    val claimableQuestsCount = remember(currentProfile) {
        if (currentProfile == null) 0
        else GameCatalog.quests.count { q ->
            (currentProfile.questProgress[q.id] ?: 0) >= q.target && currentProfile.questClaimed[q.id] != true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LimestoneBg)
    ) {
        val useNavRail = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = DarkSurfaceCard,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                ) {
                    HubTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                TabIconWithBadge(
                                    tab = tab,
                                    selected = selected
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = SaffronGold,
                                selectedTextColor = SaffronGold,
                                unselectedIconColor = TextMutedOnDark,
                                unselectedTextColor = TextMutedOnDark,
                                indicatorColor = DarkSurfaceElevated
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = DarkBgPrimary,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = DarkSurfaceCard,
                            tonalElevation = 8.dp
                        ) {
                            HubTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronGold,
                                        selectedTextColor = SaffronGold,
                                        unselectedIconColor = TextMutedOnDark,
                                        unselectedTextColor = TextMutedOnDark,
                                        indicatorColor = DarkSurfaceElevated
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (val pState = profileState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = SaffronGold)
                                Spacer(Modifier.height(10.dp))
                                Text("Loading Tunisia Eco-Arena...", style = MaterialTheme.typography.titleMedium, color = TextOnDark)
                            }
                        }
                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = CarthageCoral, modifier = Modifier.size(48.dp))
                                Text("Connection Error", style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
                                Text(pState.message, style = MaterialTheme.typography.bodyMedium, color = TextMutedOnDark)
                            }
                        }
                        is UiState.Success -> {
                            val profile: UserProfile = pState.data
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 600.dp)
                                    .align(Alignment.TopCenter)
                            ) {
                                when (currentTab) {
                                    HubTab.BATTLE -> HomeScreen(
                                        profile = profile,
                                        unlockedPlacesCount = unlockedPlacesCount,
                                        claimableQuestsCount = claimableQuestsCount,
                                        linkState = linkState,
                                        hasEdgeUrl = EdgeWebSocketClient.isValidWebSocketUrl(settings.edgeServerUrl),
                                        onBattleClicked = { showLobbySheet = true },
                                        onOpenTunisiaMap = {
                                            overlay = FullScreenOverlay.TunisiaMap()
                                        },
                                        onOpenDeck = { currentTab = HubTab.DECK },
                                        onOpenArenas = { currentTab = HubTab.ARENAS },
                                        onOpenAlbum = { currentTab = HubTab.ALBUM },
                                        onOpenQuests = {
                                            overlay = FullScreenOverlay.Quests
                                        },
                                        onOpenProfile = { profilePanelTab = ProfileTab.AVATAR },
                                        onOpenSettings = { profilePanelTab = ProfileTab.SETTINGS },
                                        hasNewAvatars = AvatarCatalog.unlockedFor(profile).any { it.id !in settings.seenAvatarIds }
                                    )

                                    HubTab.DECK -> BossCollectionScreen(
                                        profile = profile,
                                        onEquipBoss = { slot, bossId -> mainVm.equipBossInSlot(profile, slot, bossId) },
                                        onUpgradeBoss = { bossId -> mainVm.upgradeBoss(profile, bossId) }
                                    )

                                    HubTab.ARENAS -> ArenasTrophyRoadScreen(
                                        profile = profile,
                                        onOpenPlaceOnMap = { placeId ->
                                            overlay = FullScreenOverlay.TunisiaMap(initialPlaceId = placeId)
                                        }
                                    )

                                    HubTab.ALBUM -> PlacesAlbumScreen(
                                        profile = profile,
                                        onViewOnMap = { placeId ->
                                            overlay = FullScreenOverlay.TunisiaMap(initialPlaceId = placeId)
                                        }
                                    )

                                    HubTab.SOCIAL -> SocialScreen(
                                        profile = profile,
                                        publicProfilesState = publicProfilesState,
                                        clans = clansState,
                                        joinedClanId = joinedClanId,
                                        onAddFriend = { code -> mainVm.addFriend(profile, code) },
                                        onRemoveFriend = { fUid, fName -> mainVm.removeFriend(profile, fUid, fName) },
                                        onJoinClan = { clanId -> mainVm.joinClan(clanId, profile) },
                                        onLeaveClan = { mainVm.leaveClan(profile) },
                                        onCreateClan = { name, tag, region, motto ->
                                            mainVm.createClan(name, tag, region, motto, profile)
                                        },
                                        onSendClanMessage = { msg -> mainVm.sendClanMessage(msg, profile) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Profile side panel: avatar customisation & unlocks, 5G SA & settings, own replays
        ProfilePanel(
            visible = profilePanelTab != null,
            profile = currentProfile,
            initialTab = profilePanelTab ?: ProfileTab.AVATAR,
            settings = settings,
            replaysState = replaysState,
            isGuest = mainVm.isGuest,
            linkState = linkState,
            activeSliceNetwork = activeSliceNetwork,
            onClose = { profilePanelTab = null },
            onSelectAvatar = mainVm::selectAvatar,
            onRename = mainVm::rename,
            onSoundChange = SettingsStore::setSound,
            onHapticsChange = SettingsStore::setHaptics,
            onHintsChange = SettingsStore::setHints,
            onPrefer5gSliceChange = { enabled ->
                SettingsStore.setPrefer5gSlice(enabled)
                fiveGNetworkManager.syncSlicePreference(enabled)
            },
            onEdgeServerUrlChange = { url ->
                SettingsStore.setEdgeServerUrl(url)
                mainVm.showBanner(if (url.isBlank()) "Switched to Firebase Cloud default sync." else "Saved Edge WebSocket URL.")
            },
            onBoostCarrierSlice = {
                fiveGNetworkManager.requestCarrierSliceBoost { msg ->
                    mainVm.showBanner(msg)
                }
            },
            onSignInGoogle = {
                profilePanelTab = null
                onRequestOnlineSignIn()
            },
            onResetProgress = mainVm::resetGuestProgress,
            onSignOut = {
                val user = Firebase.auth.currentUser
                if (repository.isGuestMode() || user == null) {
                    mainVm.showBanner("Offline practice profile: sign in with Google for cloud sync.")
                } else {
                    profilePanelTab = null
                    AuthManager.signOut(credentialManager, scope)
                }
            },
            onWatchReplay = { replayId ->
                profilePanelTab = null
                spectatorVm.openReplay(replayId)
                overlay = FullScreenOverlay.SpectatorReplay(replayId)
            },
            onAvatarsSeen = SettingsStore::markAvatarsSeen
        )

        if (showLobbySheet && currentProfile != null) {
            MultiplayerLobbySheet(
                isGuest = mainVm.isGuest,
                linkState = linkState,
                initialEdgeUrl = settings.edgeServerUrl,
                activeSliceNetwork = activeSliceNetwork,
                onDismiss = { showLobbySheet = false },
                onQuickMatch = { edgeUrl ->
                    showLobbySheet = false
                    matchmakingVm.startMatchmaking(currentProfile, edgeUrl)
                    overlay = FullScreenOverlay.BattleFlow
                },
                onCreatePrivateRoom = { edgeUrl ->
                    showLobbySheet = false
                    overlay = FullScreenOverlay.BattleFlow
                    matchmakingVm.createPrivateRoom(
                        profile = currentProfile,
                        edgeServerUrl = edgeUrl,
                        onError = { msg ->
                            overlay = FullScreenOverlay.None
                            mainVm.showBanner(msg)
                        }
                    )
                },
                onJoinPrivateRoom = { code ->
                    showLobbySheet = false
                    overlay = FullScreenOverlay.BattleFlow
                    matchmakingVm.joinPrivateRoom(
                        profile = currentProfile,
                        rawCode = code,
                        onError = { msg ->
                            overlay = FullScreenOverlay.None
                            mainVm.showBanner(msg)
                        }
                    )
                },
                onPracticeBot = {
                    showLobbySheet = false
                    matchmakingVm.launchPracticeBotNow(currentProfile)
                    overlay = FullScreenOverlay.BattleFlow
                },
                onRequestGoogleSignIn = {
                    showLobbySheet = false
                    onRequestOnlineSignIn()
                },
                onSaveEdgeUrl = { url -> SettingsStore.setEdgeServerUrl(url) }
            )
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: HubTab,
    selected: Boolean
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    Icon(icon, contentDescription = tab.label)
}
