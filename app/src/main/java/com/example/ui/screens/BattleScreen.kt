package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsStore
import com.example.engine.BattleRules
import com.example.engine.GameSessionState
import com.example.engine.GridPos
import com.example.engine.SessionSyncStatus
import com.example.model.GameCatalog
import com.example.network.LatencyTelemetryState
import com.example.ui.components.BattleTelemetryStrip
import com.example.ui.components.BattleVfxOverlay
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.EnemyStage
import com.example.ui.components.EnvironmentScene
import com.example.ui.components.ImmutableBoard
import com.example.ui.components.LiveLatencySparklineCard
import com.example.ui.components.Match3BoardGrid
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun BattleScreen(
    state: GameSessionState,
    telemetry: LatencyTelemetryState = LatencyTelemetryState(),
    onTileTap: (GridPos) -> Unit,
    onTileSwipe: (GridPos, GridPos) -> Unit,
    onActivateBossSlot: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onForfeit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showQuitDialog by remember { mutableStateOf(false) }
    var showLatencyChart by remember { mutableStateOf(false) }

    // Layout anchors in root coordinates so projectiles fly seamlessly from matched tiles -> active mob
    var rootOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var boardBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var targetCenterInRoot by remember { mutableStateOf(Offset.Unspecified) }

    // Synchronized mob impact token fired right when a projectile volley strikes the target mob
    var impactHitToken by remember { mutableLongStateOf(0L) }
    var isFlashing by remember { mutableStateOf(false) }
    LaunchedEffect(impactHitToken) {
        if (impactHitToken > 0L) {
            isFlashing = true
            delay(155L)
            isFlashing = false
        }
    }

    BackHandler { showQuitDialog = true }

    val place = GameCatalog.placeById(state.placeId)
    val sharedBoss = GameCatalog.bossById(state.sharedBossId)
    val local = state.localPlayer
    val rival = state.opponentPlayer

    LaunchedEffect(state.invalidSwapToken) {
        if (state.invalidSwapToken > 0L && SettingsStore.current.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val mins = state.secondsRemaining / 60
    val secs = state.secondsRemaining % 60
    val timerText = String.format("%02d:%02d", mins, secs)
    val leadDelta = local.restorationPct - rival.restorationPct

    // Check if any equipped bosses share a nature (waste element) so the UI can show subtle link hint
    val equippedNatures = remember(local.deckBossIds) {
        local.deckBossIds.take(3).map { GameCatalog.bossById(it).preferredWaste }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootOriginInRoot = coords.positionInRoot()
            }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF061024), Color(0xFF0C2046), Color(0xFF050D1E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 1. TOP PREMIUM 1v1 LIVE RACE & FIREBASE SYNC HUD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = DeepNavyInk.copy(alpha = 0.92f),
                border = BorderStroke(
                    2.dp,
                    if (state.isFinalSprint) CarthageCoral else CeramicBlueTop.copy(alpha = 0.8f)
                ),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Local Player + Cloud Sync Indicator
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                val dotColor = when (state.syncStatus) {
                                    SessionSyncStatus.LIVE_SYNCED -> LimePlayTop
                                    SessionSyncStatus.SYNCING -> SaffronGold
                                    else -> Color(0xFF94A3B8)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = local.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = LimePlayTop,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (local.waveIndex < BattleRules.BOSS_WAVE_INDEX) {
                                    "Wave ${local.waveIndex}/${BattleRules.MINI_WAVE_COUNT} • ${local.restorationPct}% Clean"
                                } else {
                                    "BOSS • ${local.bossHp} HP"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        // Center: Glossy Countdown Timer + Final Sprint / Spectators
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (state.isFinalSprint) CarthageCoral else SaffronGold,
                                border = BorderStroke(2.dp, Color.White)
                            ) {
                                Text(
                                    text = timerText,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (state.isFinalSprint) Color.White else DeepNavyInk,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                                )
                            }
                            if (state.isFinalSprint) {
                                Text(
                                    text = "FINAL SPRINT x1.25",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Black),
                                    color = GlowBeamGold
                                )
                            } else if (state.spectatorCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = SidiSkyLight, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("${state.spectatorCount} live", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = SidiSkyLight)
                                }
                            }
                        }

                        // Right: Opponent Status + Sound / Quit
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rival.name,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFFFF8A80),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (rival.waveIndex < BattleRules.BOSS_WAVE_INDEX) {
                                        "Wave ${rival.waveIndex}/${BattleRules.MINI_WAVE_COUNT}"
                                    } else {
                                        "BOSS ${rival.bossHp}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(
                                onClick = onToggleSound,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Toggle audio",
                                    tint = SidiSkyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { showQuitDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Leave match",
                                    tint = SidiSkyLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    // Head-to-Head Environmental Restoration Race Progress Bars + Competitive Lead Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RaceProgressMiniBar(
                            label = "YOU • ${local.restorationPct}%",
                            fraction = local.restorationPct / 100f,
                            fillBrush = Brush.horizontalGradient(listOf(EcoEmerald, LimePlayTop)),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when {
                                leadDelta > 0 -> EcoEmerald.copy(alpha = 0.92f)
                                leadDelta < 0 -> CarthageCoral.copy(alpha = 0.92f)
                                else -> SaffronGold.copy(alpha = 0.92f)
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                        ) {
                            Text(
                                text = when {
                                    leadDelta > 0 -> "+${leadDelta}%"
                                    leadDelta < 0 -> "${leadDelta}%"
                                    else -> "TIED"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = if (leadDelta == 0) DeepNavyInk else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        RaceProgressMiniBar(
                            label = "RIVAL • ${rival.restorationPct}%",
                            fraction = rival.restorationPct / 100f,
                            fillBrush = Brush.horizontalGradient(listOf(CarthageCoralDark, BossHpRedTop)),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    // Live 5G SA Radio Bearer, Slice Status & Real-Time RTT / Jitter Strip
                    BattleTelemetryStrip(
                        telemetry = telemetry,
                        expanded = showLatencyChart,
                        onToggleExpand = { showLatencyChart = !showLatencyChart }
                    )
                }
            }

            // 2. UPPER TUNISIAN LANDMARK BATTLEFIELD & REAL-TIME ENVIRONMENTAL RESTORATION
            val stageBorderBrush = remember(state.isFeverActive) {
                if (state.isFeverActive) {
                    Brush.linearGradient(listOf(EcoEmeraldBright, GlowBeamGold, LimePlayTop))
                } else {
                    Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.5.dp, stageBorderBrush, RoundedCornerShape(22.dp))
            ) {
                // The place reacts to the fight: cartoon + litter while polluted, real photo once purified
                EnvironmentScene(
                    place = place,
                    pollution = local.pollutionLevel,
                    purified = local.bossHp <= 0 || (state.isFinished && state.wonMatch),
                    modifier = Modifier.fillMaxSize()
                )

                // Location + Environmental Pollution Gauge Top Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CeramicBlueBottom.copy(alpha = 0.92f),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Text(
                            text = "${place.name} • ${place.regionTag}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    val smogPct = (local.pollutionLevel * 100f).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (smogPct <= 25) EcoEmerald else DeepNavyInk.copy(alpha = 0.88f),
                        border = BorderStroke(1.5.dp, if (smogPct <= 25) LimePlayTop else SaffronGold)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (smogPct <= 25) Icons.Default.Eco else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (smogPct <= 25) Color.White else SaffronGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (smogPct == 0) "PURIFIED!" else "Smog $smogPct%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                    }
                }

                // Competitive Rival Surge Alert (top-left under place pill when rival pops a skill or reaches Boss)
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.rivalSurgeBanner.isNotBlank() && !showLatencyChart,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = CarthageCoralDark.copy(alpha = 0.92f),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = GlowBeamGold, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = state.rivalSurgeBanner,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Center enemies: mini trash monsters of waves 1-4, then the shared Wave 5 Boss
                EnemyStage(
                    waveIndex = local.waveIndex,
                    mobs = local.waveMobs,
                    boss = sharedBoss,
                    bossHp = local.bossHp,
                    bossMaxHp = local.bossMaxHp,
                    hitFlash = isFlashing,
                    hitToken = impactHitToken,
                    defeatedToken = state.mobDefeatedToken,
                    onTargetCenterChanged = { center ->
                        targetCenterInRoot = center
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 14.dp)
                )

                // Floating Combo / Damage Pop-Up Banner with crisp spring pop-in
                if (state.comboBannerText.isNotBlank()) {
                    val comboScale = remember(state.hitFlashToken) { Animatable(0.72f) }
                    LaunchedEffect(state.hitFlashToken) {
                        comboScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 780f))
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .scale(comboScale.value),
                        shape = RoundedCornerShape(50),
                        color = if (state.isFeverActive) EcoEmeraldBright else SaffronGold,
                        border = BorderStroke(2.dp, DeepNavyInk)
                    ) {
                        Text(
                            text = state.comboBannerText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = DeepNavyInk,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                        )
                    }
                }

                // Expandable Live 5G SA RTT Sparkline & Slice Diagnostics Card
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLatencyChart,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                ) {
                    LiveLatencySparklineCard(
                        telemetry = telemetry,
                        onClose = { showLatencyChart = false }
                    )
                }
            }

            // 3. MIDDLE 3-SLOT ECO-DECK SKILL BAR (Nature Badge + Shared-Nature Reset Indicator + Smooth Charge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                local.deckBossIds.take(3).forEachIndexed { idx, bossId ->
                    val boss = GameCatalog.bossById(bossId)
                    val charge = local.skillCharges.getOrElse(idx) { 0 }.coerceIn(0, 100)
                    val ready = charge >= 100
                    val wasSiblingReset = idx in state.resetSkillSlots
                    val sharesNature = equippedNatures.count { it == boss.preferredWaste } > 1
                    val animCharge by animateFloatAsState(
                        targetValue = charge / 100f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "skillCharge_$idx"
                    )
                    val readyScale by animateFloatAsState(
                        targetValue = if (ready) 1.03f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
                        label = "skillReadyScale_$idx"
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(readyScale)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = ready) {
                                if (SettingsStore.current.hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onActivateBossSlot(idx)
                            }
                            .testTag("eco_deck_skill_slot_$idx"),
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            wasSiblingReset -> CarthageCoralDark.copy(alpha = 0.92f)
                            ready -> EcoEmerald
                            else -> DeepNavyInk.copy(alpha = 0.90f)
                        },
                        border = BorderStroke(
                            width = if (ready || wasSiblingReset) 2.5.dp else 1.8.dp,
                            color = when {
                                wasSiblingReset -> Color(0xFFFF8A80)
                                ready -> GlowBeamGold
                                else -> boss.rarity.frameColor
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(44.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .align(Alignment.Center)
                                        .clip(CircleShape)
                                        .background(boss.bodyColor.copy(alpha = 0.25f))
                                        .border(1.5.dp, if (ready) GlowBeamGold else boss.rarity.frameColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BossMonsterAvatar(
                                        boss = boss,
                                        animateIdle = ready,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                // Waste Nature badge so shared-nature skills are crystal clear
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(boss.preferredWaste.darkColor)
                                        .border(1.2.dp, if (sharesNature) SaffronGold else Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(boss.preferredWaste.spriteRes),
                                        contentDescription = boss.preferredWaste.shortLabel,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(5.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        wasSiblingReset -> "RESET!"
                                        ready -> "CAST!"
                                        else -> boss.name
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (ready || wasSiblingReset) Color.White else SaffronCream,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF0A162C))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animCharge)
                                            .background(if (ready) GlowBeamGold else boss.preferredWaste.primaryColor)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (wasSiblingReset) "Same nature" else "$charge%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                        color = if (wasSiblingReset) GlowBeamGold else Color.White,
                                        maxLines = 1
                                    )
                                    if (ready) {
                                        Icon(Icons.Default.Bolt, contentDescription = "Ready", tint = GlowBeamGold, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3.5 SPEED STREAK / ECO FEVER MOMENTUM STRIP (isolated Canvas decay = 0 recompositions!)
            if (state.streakCount >= 2) {
                SpeedStreakBanner(
                    streakCount = state.streakCount,
                    streakExpiresAtMs = state.streakExpiresAtMs,
                    isFeverActive = state.isFeverActive
                )
            }

            // 4. LOWER OBSIDIAN-GOLD 7x7 MATCH-3 GRID (Skippable via ImmutableBoard + remembered callbacks)
            val immutableBoard = remember(local.board) { ImmutableBoard(local.board) }
            val stableTap = remember(onTileTap, haptic) {
                { pos: GridPos ->
                    if (SettingsStore.current.hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onTileTap(pos)
                }
            }
            val stableSwipe = remember(onTileSwipe, haptic) {
                { from: GridPos, to: GridPos ->
                    if (SettingsStore.current.hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onTileSwipe(from, to)
                }
            }
            val stableBounds = remember {
                { rect: Rect -> boardBoundsInRoot = rect }
            }
            Match3BoardGrid(
                board = immutableBoard,
                selectedTile = state.selectedTile,
                interactive = true,
                useSandstoneTheme = false,
                invalidSwapPair = state.invalidSwapPair,
                invalidSwapToken = state.invalidSwapToken,
                popBurstTiles = state.popBurstTiles,
                popBurstToken = state.popBurstToken,
                hintPair = state.hintPair,
                feverMode = state.isFeverActive,
                onBoardBoundsChanged = stableBounds,
                onTileTap = stableTap,
                onTileSwipe = stableSwipe,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )
        }

        // 5. FULL-SCREEN SYNCHRONIZED TILE-TO-MOB PROJECTILE & IMPACT VFX LAYER
        BattleVfxOverlay(
            volleys = state.projectileVolleys,
            boardBoundsInRoot = boardBoundsInRoot,
            targetCenterInRoot = targetCenterInRoot,
            rootOriginInRoot = rootOriginInRoot,
            onVolleyImpact = { volley ->
                impactHitToken = System.nanoTime()
                if (SettingsStore.current.hapticsEnabled && (volley.isCritical || volley.isAbility || volley.isWeakHit)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        )
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            containerColor = DarkSurfaceCard,
            title = { Text("Leave 1v1 Battle?", style = MaterialTheme.typography.titleLarge, color = TextOnDark) },
            text = { Text("Leaving now ends your speed race and returns to the Battle Hub.", color = TextMutedOnDark) },
            confirmButton = {
                TextButton(onClick = {
                    showQuitDialog = false
                    onForfeit()
                }) { Text("Leave", color = BossHpRedTop) }
            },
            dismissButton = {
                TextButton(onClick = { showQuitDialog = false }) { Text("Keep Racing", color = CeramicBlueTop) }
            }
        )
    }
}

@Composable
private fun SpeedStreakBanner(
    streakCount: Int,
    streakExpiresAtMs: Long,
    isFeverActive: Boolean
) {
    val bonusPct = ((BattleRules.streakDamageMultiplier(streakCount) - 1f) * 100f).toInt()
    val streakProgress = remember(streakExpiresAtMs) { Animatable(1f) }
    LaunchedEffect(streakExpiresAtMs) {
        val remaining = (streakExpiresAtMs - System.currentTimeMillis()).coerceIn(100L, BattleRules.STREAK_WINDOW_MS)
        streakProgress.snapTo((remaining.toFloat() / BattleRules.STREAK_WINDOW_MS.toFloat()).coerceIn(0f, 1f))
        streakProgress.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing))
    }
    val barColor = if (isFeverActive) GlowBeamGold else EcoEmeraldBright
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = if (isFeverActive) EcoEmeraldDark.copy(alpha = 0.94f) else DeepNavyInk.copy(alpha = 0.90f),
        border = BorderStroke(1.2.dp, if (isFeverActive) GlowBeamGold else SaffronGold.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (isFeverActive) GlowBeamGold else SaffronGold,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isFeverActive) "ECO FEVER ${streakCount}x • +$bonusPct% DMG" else "${streakCount}x SPEED STREAK • +$bonusPct% DMG",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Black),
                color = if (isFeverActive) GlowBeamGold else Color.White
            )
            // Read streakProgress.value ONLY inside Canvas.onDraw (zero 60fps recomposition!)
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                drawRect(color = Color.Black.copy(alpha = 0.4f))
                val frac = streakProgress.value.coerceIn(0f, 1f)
                if (frac > 0f) {
                    drawRect(color = barColor, size = Size(size.width * frac, size.height))
                }
            }
        }
    }
}

@Composable
private fun RaceProgressMiniBar(
    label: String,
    fraction: Float,
    fillBrush: Brush,
    modifier: Modifier = Modifier
) {
    val animFrac by animateFloatAsState(
        targetValue = fraction.coerceIn(0.03f, 1f),
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "raceBar"
    )
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(BossHpTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animFrac)
                    .background(fillBrush)
            )
        }
    }
}
