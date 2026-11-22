package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.model.AvatarCatalog
import com.example.model.AvatarDefinition
import com.example.model.GameCatalog
import com.example.model.MatchReplay
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.network.EdgePingTestResult
import com.example.network.EdgeWebSocketClient
import com.example.network.RadioLinkSnapshot
import com.example.network.SliceStatus
import com.example.ui.components.bearerAccentColor
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class ProfileTab(val label: String) { AVATAR("Avatar"), SETTINGS("Settings"), REPLAYS("Replays") }

/** Round avatar portrait: coloured badge + mob/boss sprite, gold ring. */
@Composable
fun AvatarPortrait(
    avatar: AvatarDefinition,
    size: Dp,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    ringColor: Color = SaffronGold
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(avatar.background.copy(alpha = if (locked) 0.25f else 0.95f), DeepNavyInk)
                )
            )
            .border((size.value / 18f).coerceIn(1.5f, 3.5f).dp, if (locked) DarkBorderSubtle else ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val grey = remember { ColorMatrix().apply { setToSaturation(0f) } }
        Image(
            painter = painterResource(avatar.spriteRes),
            contentDescription = avatar.name,
            contentScale = ContentScale.Fit,
            colorFilter = if (locked) ColorFilter.colorMatrix(grey) else null,
            modifier = Modifier
                .fillMaxSize(0.86f)
                .alpha(if (locked) 0.45f else 1f)
        )
        if (locked) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(size * 0.3f))
        }
    }
}

/**
 * Profile side panel (slides in from the left): avatar customisation with unlocks,
 * settings, and the player's own battle replays.
 */
@Composable
fun ProfilePanel(
    visible: Boolean,
    profile: UserProfile?,
    initialTab: ProfileTab,
    settings: AppSettings,
    replaysState: UiState<List<MatchReplay>>,
    isGuest: Boolean,
    linkState: RadioLinkSnapshot = RadioLinkSnapshot(),
    activeSliceNetwork: android.net.Network? = null,
    onClose: () -> Unit,
    onSelectAvatar: (String) -> Unit,
    onRename: (String) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onHintsChange: (Boolean) -> Unit,
    onPrefer5gSliceChange: (Boolean) -> Unit = {},
    onEdgeServerUrlChange: (String) -> Unit = {},
    onBoostCarrierSlice: () -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onResetProgress: () -> Unit,
    onSignOut: () -> Unit,
    onWatchReplay: (String) -> Unit,
    onAvatarsSeen: (List<String>) -> Unit
) {
    if (visible) BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            )
        }
        AnimatedVisibility(
            visible = visible && profile != null,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            if (profile == null) return@AnimatedVisibility
            var tab by remember(initialTab) { mutableStateOf(initialTab) }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 460.dp)
                    .testTag("profile_panel"),
                color = DarkBgPrimary,
                shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                border = BorderStroke(2.dp, Brush.verticalGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop))),
                shadowElevation = 18.dp
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                        .padding(14.dp)
                ) {
                    ProfileHeader(profile, isGuest, onClose)
                    Spacer(Modifier.height(12.dp))
                    // Tabs
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(DarkSurfaceCard)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProfileTab.entries.forEach { t ->
                            val sel = t == tab
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (sel) SaffronGold else Color.Transparent)
                                    .clickable { tab = t }
                                    .padding(vertical = 8.dp)
                                    .testTag("profile_tab_${t.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    t.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (sel) DeepNavyInk else TextOnDark
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            ProfileTab.AVATAR -> AvatarTab(profile, settings, onSelectAvatar, onAvatarsSeen)
                            ProfileTab.SETTINGS -> SettingsTab(
                                profile, settings, isGuest, linkState, activeSliceNetwork,
                                onRename, onSoundChange, onHapticsChange, onHintsChange,
                                onPrefer5gSliceChange, onEdgeServerUrlChange, onBoostCarrierSlice,
                                onSignInGoogle, onResetProgress, onSignOut
                            )
                            ProfileTab.REPLAYS -> ReplaysTab(profile, replaysState, onWatchReplay)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, isGuest: Boolean, onClose: () -> Unit) {
    val avatar = AvatarCatalog.displayedFor(profile)
    val arena = GameCatalog.arenaForTrophies(profile.trophies)
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarPortrait(avatar, 72.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = TextOnDark, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "Code ${profile.friendCode}${if (isGuest) " • Offline guest" else ""}",
                style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniStat("🏆 ${profile.trophies}")
                MiniStat("${profile.wins}W-${profile.losses}L")
                MiniStat(arena.name.substringAfter(": "))
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.testTag("profile_close")) {
            Icon(Icons.Default.Close, contentDescription = "Close profile", tint = TextMutedOnDark)
        }
    }
}

@Composable
private fun MiniStat(text: String) {
    Surface(shape = RoundedCornerShape(50), color = DarkSurfaceCard, border = BorderStroke(1.dp, DarkBorderSubtle)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = TextOnDark, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AvatarTab(
    profile: UserProfile,
    settings: AppSettings,
    onSelectAvatar: (String) -> Unit,
    onAvatarsSeen: (List<String>) -> Unit
) {
    val unlocked = AvatarCatalog.unlockedFor(profile)
    val newIds = remember(profile) { unlocked.map { it.id }.filter { it !in settings.seenAvatarIds } }
    // Opening the tab marks newly unlocked avatars as seen (the badges stay visible this time).
    LaunchedEffect(newIds) { if (newIds.isNotEmpty()) onAvatarsSeen(newIds) }
    val current = AvatarCatalog.displayedFor(profile)
    var focused by remember { mutableStateOf<AvatarDefinition?>(null) }

    Column {
        Text(
            "${unlocked.size}/${AvatarCatalog.all.size} unlocked • Win battles, climb trophies and capture Bosses to unlock more.",
            style = MaterialTheme.typography.bodySmall, color = TextMutedOnDark
        )
        Spacer(Modifier.height(8.dp))
        focused?.let { f ->
            val isUnlocked = f.isUnlocked(profile)
            Surface(
                shape = RoundedCornerShape(16.dp), color = DarkSurfaceCard,
                border = BorderStroke(1.5.dp, if (isUnlocked) SaffronGold else DarkBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarPortrait(f, 48.dp, locked = !isUnlocked)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
                        Text(
                            if (isUnlocked) "Unlocked • ${f.requirementText()}" else "🔒 ${f.requirementText()}",
                            style = MaterialTheme.typography.labelSmall, color = if (isUnlocked) LimePlayTop else TextMutedOnDark
                        )
                        if (!isUnlocked) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { f.progress(profile) },
                                color = SaffronGold, trackColor = DeepNavyInk,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                            )
                        }
                    }
                    if (isUnlocked && f.id != current.id) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSelectAvatar(f.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = LimePlayTop, contentColor = DeepNavyInk),
                            modifier = Modifier.testTag("avatar_equip_button")
                        ) { Text("Use", fontWeight = FontWeight.ExtraBold) }
                    } else if (f.id == current.id) {
                        Text("IN USE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = SaffronGold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(AvatarCatalog.all, key = { it.id }) { av ->
                val isUnlocked = av.isUnlocked(profile)
                val isCurrent = av.id == current.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (focused?.id == av.id) DarkSurfaceElevated else Color.Transparent)
                        .clickable { focused = av }
                        .padding(4.dp)
                        .testTag("avatar_${av.id}")
                ) {
                    Box {
                        AvatarPortrait(av, 62.dp, locked = !isUnlocked, ringColor = if (isCurrent) LimePlayTop else SaffronGold)
                        if (av.id in newIds) {
                            Surface(shape = RoundedCornerShape(50), color = CarthageCoral, modifier = Modifier.align(Alignment.TopEnd)) {
                                Text("NEW", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = "In use", tint = LimePlayTop,
                                modifier = Modifier.size(18.dp).align(Alignment.BottomEnd).background(DeepNavyInk, CircleShape)
                            )
                        }
                    }
                    Text(
                        av.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isUnlocked) TextOnDark else TextMutedOnDark,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    profile: UserProfile,
    settings: AppSettings,
    isGuest: Boolean,
    linkState: RadioLinkSnapshot,
    activeSliceNetwork: android.net.Network?,
    onRename: (String) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onHintsChange: (Boolean) -> Unit,
    onPrefer5gSliceChange: (Boolean) -> Unit,
    onEdgeServerUrlChange: (String) -> Unit,
    onBoostCarrierSlice: () -> Unit,
    onSignInGoogle: () -> Unit,
    onResetProgress: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var edgeUrl by remember(settings.edgeServerUrl) { mutableStateOf(settings.edgeServerUrl) }
    var pingTesting by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<EdgePingTestResult?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsCard("Player name") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("settings_name_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextOnDark, unfocusedTextColor = TextOnDark,
                        focusedBorderColor = SaffronGold, unfocusedBorderColor = DarkBorderSubtle
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onRename(name) },
                    enabled = name.trim() != profile.displayName && name.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronGold, contentColor = DeepNavyInk)
                ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
            }
        }
        SettingsCard("5G SA & Low-Latency Network") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${linkState.bearer.detailLabel}${linkState.carrierName.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = bearerAccentColor(linkState.bearer)
                    )
                    Text(
                        text = "${linkState.sliceStatus.detailLabel} • ↓${linkState.downstreamKbps / 1000} / ↑${linkState.upstreamKbps / 1000} Mbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedOnDark
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            SettingSwitch(
                icon = Icons.Default.CellTower,
                label = "Request 5G Low-Latency Slice",
                checked = settings.prefer5gSlice,
                onChange = onPrefer5gSliceChange,
                tag = "settings_5g_slice"
            )
            if (linkState.sliceUpsellAvailable && linkState.sliceStatus != SliceStatus.ACTIVE) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onBoostCarrierSlice,
                    colors = ButtonDefaults.buttonColors(containerColor = LimePlayTop, contentColor = DeepNavyInk),
                    modifier = Modifier.fillMaxWidth().testTag("settings_boost_5g_slice")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Activate Carrier 5G Latency Slice", fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Optional Edge / MEC WebSocket URL (ws:// or wss://)",
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedOnDark
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = edgeUrl,
                onValueChange = {
                    edgeUrl = it.take(200)
                    pingResult = null
                },
                placeholder = { Text("ws://10.45.0.1:8080/ws", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings_edge_url_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextOnDark, unfocusedTextColor = TextOnDark,
                    focusedBorderColor = Color(0xFF22D3EE), unfocusedBorderColor = DarkBorderSubtle
                )
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onEdgeServerUrlChange(edgeUrl) },
                    enabled = edgeUrl.trim() != settings.edgeServerUrl,
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronGold, contentColor = DeepNavyInk)
                ) { Text("Save URL", fontWeight = FontWeight.ExtraBold) }
                OutlinedButton(
                    onClick = {
                        onEdgeServerUrlChange(edgeUrl)
                        pingTesting = true
                        pingResult = null
                        scope.launch {
                            pingResult = EdgeWebSocketClient.testConnection(edgeUrl, activeSliceNetwork)
                            pingTesting = false
                        }
                    },
                    enabled = !pingTesting && EdgeWebSocketClient.isValidWebSocketUrl(edgeUrl),
                    border = BorderStroke(1.2.dp, Color(0xFF22D3EE)),
                    modifier = Modifier.testTag("settings_ping_edge_button")
                ) {
                    Text(if (pingTesting) "Pinging..." else "Ping Edge", color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                }
            }
            pingResult?.let { res ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = res.message,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (res.success) LimePlayTop else CarthageCoral
                )
            }
        }
        SettingsCard("Game") {
            SettingSwitch(Icons.AutoMirrored.Filled.VolumeUp, "Sound effects", settings.soundEnabled, onSoundChange, "settings_sound")
            SettingSwitch(Icons.Default.Vibration, "Vibration", settings.hapticsEnabled, onHapticsChange, "settings_haptics")
            SettingSwitch(Icons.Default.Lightbulb, "Move hints", settings.hintsEnabled, onHintsChange, "settings_hints")
        }
        SettingsCard("Account") {
            if (isGuest) {
                Text(
                    "Offline practice mode: sign in with Google to unlock live 1v1 matchmaking & Private Rooms.",
                    style = MaterialTheme.typography.bodySmall, color = TextMutedOnDark
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSignInGoogle,
                    colors = ButtonDefaults.buttonColors(containerColor = CeramicBlueTop, contentColor = DeepNavyInk),
                    modifier = Modifier.fillMaxWidth().testTag("settings_google_signin")
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Sign in with Google", fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmReset = true },
                    border = BorderStroke(1.5.dp, CarthageCoral),
                    modifier = Modifier.fillMaxWidth().testTag("settings_reset")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = CarthageCoral)
                    Spacer(Modifier.width(6.dp))
                    Text("Reset guest progress", color = CarthageCoral, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = TextOnDark)
                    Spacer(Modifier.width(6.dp))
                    Text("Sign out", color = TextOnDark)
                }
            }
        }
        Text(
            "WasteCrush • Tounes Nadhifa • 5G SA Ready",
            style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("Trophies, coins, Bosses, quests and avatars on this phone go back to the start. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetProgress() }) { Text("Reset", color = CarthageCoral) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = DarkSurfaceCard, border = BorderStroke(1.5.dp, DarkBorderSubtle), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = SaffronGold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = SidiSkyLight)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextOnDark, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedThumbColor = DeepNavyInk, checkedTrackColor = LimePlayTop)
        )
    }
}

@Composable
private fun ReplaysTab(
    profile: UserProfile,
    replaysState: UiState<List<MatchReplay>>,
    onWatchReplay: (String) -> Unit
) {
    when (replaysState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SaffronGold) }
        is UiState.Error -> Text(replaysState.message, color = TextMutedOnDark)
        is UiState.Success -> {
            val mine = replaysState.data.filter { profile.userId in it.participantUids || it.creatorUid == profile.userId }
            if (mine.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = TextMutedOnDark, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No replays yet", style = MaterialTheme.typography.titleSmall, color = TextOnDark)
                    Text("Play a battle: it is recorded here automatically.", style = MaterialTheme.typography.bodySmall, color = TextMutedOnDark, textAlign = TextAlign.Center)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mine.forEach { rep -> ReplayRow(profile, rep) { onWatchReplay(rep.id) } }
                }
            }
        }
    }
}

@Composable
private fun ReplayRow(profile: UserProfile, rep: MatchReplay, onWatch: () -> Unit) {
    val place = GameCatalog.placeById(rep.placeId)
    val won = rep.winnerUid == profile.userId
    val me = if (rep.player1Summary.uid == profile.userId) rep.player1Summary else rep.player2Summary
    val rival = if (rep.player1Summary.uid == profile.userId) rep.player2Summary else rep.player1Summary
    Surface(
        shape = RoundedCornerShape(16.dp), color = DarkSurfaceCard,
        border = BorderStroke(1.5.dp, if (won) LimePlayTop.copy(alpha = 0.7f) else CarthageCoral.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onWatch).testTag("replay_row")
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(place.imageRes), contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (won) "Victory" else "Defeat"} • ${place.name}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (won) LimePlayTop else CarthageCoral
                )
                Text("vs ${rival.name} • ${rep.durationSec}s", style = MaterialTheme.typography.labelSmall, color = TextOnDark)
                Text("${me.totalDamage} vs ${rival.totalDamage} dmg", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
            }
            Icon(Icons.Default.PlayCircle, contentDescription = "Watch replay", tint = SaffronGold, modifier = Modifier.size(34.dp))
        }
    }
}
