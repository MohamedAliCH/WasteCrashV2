package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsStore
import com.example.engine.GameSessionState
import com.example.model.GameCatalog
import com.example.model.TunisianPlace
import com.example.model.UserProfile
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.EnvironmentScene
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class AlbumCollectPhase { READY, ANIMATING, COLLECTED }

@Composable
fun PostMatchScreen(
    state: GameSessionState,
    profile: UserProfile?,
    onPlayAgain: () -> Unit,
    onWatchReplay: (String) -> Unit,
    onOpenAlbum: () -> Unit = {},
    onAlbumCollectChime: () -> Unit = {},
    onReturnHome: () -> Unit
) {
    val place = GameCatalog.placeById(state.placeId)
    val boss = GameCatalog.bossById(state.sharedBossId)
    val local = state.localPlayer
    val rival = state.opponentPlayer
    val reward = state.rewardOutcome
    val trophyDelta = reward?.trophyDelta ?: if (state.wonMatch) 30 else -15
    val coinsEarned = reward?.coinsEarned ?: if (state.wonMatch) 95 else 30

    val isFirstPurification = state.wonMatch && reward?.newlyPurifiedPlaceId == state.placeId
    val albumTotalCount = (profile?.purifiedPlaceIds?.distinct()?.size ?: 0).coerceAtLeast(
        if (isFirstPurification) 1 else 0
    )

    val bgBrush = if (state.wonMatch) {
        Brush.verticalGradient(listOf(CeramicBlueBottom, Color(0xFF0E7490), BattleBoardDark))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF6B1D1D), BattleBoardDark, Color(0xFF0A1426)))
    }

    var sweepReplayToken by remember(state.matchId) { mutableIntStateOf(0) }
    var sweepCompleted by remember(state.matchId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Victory / Defeat Glossy Banner
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (state.wonMatch) SaffronGold else CarthageCoral,
                border = BorderStroke(3.dp, DeepNavyInk),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (state.wonMatch) "VICTORY!" else "DEFEAT",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (state.wonMatch) DeepNavyInk else Color.White
                    )
                    Text(
                        text = if (state.wonMatch) {
                            "${place.name} purified! You defeated ${boss.name} first."
                        } else {
                            "${rival.name} edged ahead at ${place.name}."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (state.wonMatch) DeepNavyInk else SaffronCream,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Dramatic Dirty -> Clean Eco-Purification Wave Transformation Card
            PurificationWaveCard(
                place = place,
                wonMatch = state.wonMatch,
                fallbackPollution = local.pollutionLevel.coerceAtLeast(0.75f),
                replayToken = sweepReplayToken,
                onReplayClick = { sweepReplayToken++ },
                onSweepFinished = { sweepCompleted = true }
            )

            // First-Time Map Unlock: "Add to Album" Label + Collect-to-Album Animation
            AnimatedVisibility(
                visible = isFirstPurification && sweepCompleted,
                enter = fadeIn(tween(320)) + expandVertically(tween(360, easing = FastOutSlowInEasing))
            ) {
                FirstTimeAlbumUnlockCard(
                    place = place,
                    albumTotalCount = albumTotalCount,
                    onCollectChime = onAlbumCollectChime,
                    onOpenAlbum = onOpenAlbum
                )
            }

            // Head-to-Head Damage & Pollution Clearing Breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(2.dp, DarkBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1v1 Race Breakdown",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextOnDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ComparisonColumn(
                            title = local.name,
                            damage = local.totalDamage,
                            restoration = local.restorationPct,
                            accent = EcoEmeraldBright,
                            modifier = Modifier.weight(1f)
                        )
                        ComparisonColumn(
                            title = rival.name,
                            damage = rival.totalDamage,
                            restoration = rival.restorationPct,
                            accent = BossHpRedTop,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = DarkBorderSubtle.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniMetric("Blue Tiles", "${state.blueTilesMatched}", CeramicBlueTop)
                        MiniMetric("Total Matched", "${state.totalTilesMatched}", SaffronGold)
                        MiniMetric("Best Combo", "${state.maxCombo}x", EcoEmeraldBright)
                    }
                }
            }

            // Trophy & Eco-Coin Payout Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PayoutBadge(
                    icon = Icons.Default.EmojiEvents,
                    label = "Trophies",
                    value = if (trophyDelta >= 0) "+$trophyDelta" else "$trophyDelta",
                    bgColor = DarkSurfaceCard,
                    borderColor = SaffronGold,
                    iconTint = SaffronGold,
                    modifier = Modifier.weight(1f)
                )
                PayoutBadge(
                    icon = Icons.Default.MonetizationOn,
                    label = "Eco-Coins",
                    value = "+$coinsEarned",
                    bgColor = DarkSurfaceCard,
                    borderColor = EcoEmeraldBright,
                    iconTint = EcoEmeraldBright,
                    modifier = Modifier.weight(1f)
                )
            }

            // Winner Captured Boss Card Reveal with Real Uploaded Sprite!
            if (state.wonMatch) {
                val lvl = profile?.bossLevels?.get(boss.id) ?: 1
                val copies = profile?.bossCopies?.get(boss.id) ?: 1
                val needed = GameCatalog.copiesNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(2.5.dp, boss.rarity.frameColor),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(boss.bodyColor.copy(alpha = 0.22f))
                                .border(2.dp, boss.rarity.frameColor, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            BossMonsterAvatar(boss = boss, modifier = Modifier.size(72.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "+1 BOSS CARD CAPTURED!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EcoEmeraldBright
                                )
                                RarityChip(boss.rarity)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = boss.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextOnDark
                            )
                            Text(
                                text = "Level $lvl  •  $copies / $needed copies for next level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedOnDark
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (copies.toFloat() / needed.toFloat()).coerceIn(0f, 1f) },
                                color = EcoEmeraldBright,
                                trackColor = Color(0xFF061022),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }

            // Quest Progress Ticker
            if (reward != null && reward.newlyCompletedQuestIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(2.dp, SaffronGold)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Quest Objective Completed!", style = MaterialTheme.typography.titleSmall, color = GlowBeamGold)
                            val names = reward.newlyCompletedQuestIds.mapNotNull { id ->
                                GameCatalog.quests.firstOrNull { it.id == id }?.title
                            }.joinToString(", ")
                            Text("Claim rewards in Quests: $names", style = MaterialTheme.typography.bodyMedium, color = TextOnDark)
                        }
                    }
                }
            }

            // Action Buttons: Watch Replay, Battle Again, Back to Hub
            if (!state.replayId.isNullOrBlank()) {
                GlossyButton(
                    text = "Watch Battle Replay",
                    onClick = { onWatchReplay(state.replayId) },
                    icon = Icons.Default.PlayCircle,
                    topColor = CeramicBlueTop,
                    bottomColor = CeramicBlueBottom,
                    lipColor = CeramicBlueLip,
                    modifier = Modifier.fillMaxWidth(),
                    tag = "post_match_watch_replay"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlossyButton(
                    text = "Home Hub",
                    onClick = onReturnHome,
                    topColor = Color(0xFF2B4D8A),
                    bottomColor = Color(0xFF18305C),
                    lipColor = Color(0xFF0C1A36),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    tag = "post_match_home"
                )
                GlossyButton(
                    text = "Race Again",
                    onClick = onPlayAgain,
                    icon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f),
                    tag = "post_match_rematch"
                )
            }
        }
    }
}

/**
 * Animated Dirty -> Clean Transformation Postcard:
 * Sweeps a luminous emerald-gold purification wave across the polluted landmark,
 * lifting smog & litter away to unveil the clean real Tunisian landmark photo with sparkles.
 */
@Composable
private fun PurificationWaveCard(
    place: TunisianPlace,
    wonMatch: Boolean,
    fallbackPollution: Float,
    replayToken: Int,
    onReplayClick: () -> Unit,
    onSweepFinished: () -> Unit
) {
    val sweep = remember(place.id) { Animatable(0f) }
    val sheen = remember(place.id) { Animatable(0f) }
    var phaseDone by remember(place.id, replayToken) { mutableStateOf(false) }

    // Pre-computed sparkle seeds for zero per-frame allocations inside Canvas
    data class EcoSpark(val yFrac: Float, val trailOffset: Float, val radiusDp: Float, val gold: Boolean)
    val sparks = remember(place.id) {
        val rng = Random(place.id.hashCode() xor 0x5A3C)
        List(24) {
            EcoSpark(
                yFrac = 0.08f + rng.nextFloat() * 0.84f,
                trailOffset = rng.nextFloat() * 0.14f,
                radiusDp = 2.0f + rng.nextFloat() * 3.4f,
                gold = it % 2 == 0
            )
        }
    }

    LaunchedEffect(wonMatch, place.id, replayToken) {
        sweep.snapTo(0f)
        sheen.snapTo(0f)
        phaseDone = false
        if (wonMatch) {
            delay(520L)
            sweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1550, easing = FastOutSlowInEasing)
            )
            phaseDone = true
            onSweepFinished()
            sheen.animateTo(1f, tween(520, easing = LinearEasing))
        }
    }

    val isClean = wonMatch && sweep.value >= 0.98f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("purification_reveal_card"),
        shape = RoundedCornerShape(22.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(
            width = if (isClean) 2.5.dp else 2.dp,
            brush = if (isClean) {
                Brush.linearGradient(listOf(EcoEmeraldBright, GlowBeamGold, LimePlayTop))
            } else {
                Brush.linearGradient(listOf(SaffronGold.copy(alpha = 0.7f), DarkBorderSubtle))
            }
        ),
        shadowElevation = 10.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(186.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                // 1. Clean real landmark photo underneath (revealed left-to-right)
                Image(
                    painter = painterResource(id = place.imageRes),
                    contentDescription = "${place.name} clean",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Polluted / dirty layer clipped strictly to the right of the sweeping wave
                val dirtyClipLeft = if (wonMatch) sweep.value else 0f
                if (dirtyClipLeft < 0.999f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(left = size.width * dirtyClipLeft) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    ) {
                        EnvironmentScene(
                            place = place,
                            pollution = if (wonMatch) 1.0f else fallbackPollution,
                            purified = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 3. Glowing Eco-Purification Wave Curtain & Sparkles along the sweep front
                if (wonMatch && (sweep.value in 0.005f..0.995f || sheen.value in 0.01f..0.99f)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = sweep.value
                        if (s in 0.005f..0.995f) {
                            val waveX = size.width * s
                            val bandW = 34.dp.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        EcoEmeraldBright.copy(alpha = 0.55f),
                                        GlowBeamGold.copy(alpha = 0.92f),
                                        Color.White.copy(alpha = 0.95f),
                                        Color.Transparent
                                    ),
                                    startX = waveX - bandW,
                                    endX = waveX + bandW * 0.45f
                                ),
                                topLeft = Offset(waveX - bandW, 0f),
                                size = androidx.compose.ui.geometry.Size(bandW * 1.45f, size.height)
                            )
                            // Sparkling eco-particles bursting behind the purification wave
                            sparks.forEach { sp ->
                                val sx = (waveX - sp.trailOffset * size.width).coerceAtLeast(0f)
                                val sy = sp.yFrac * size.height
                                val fade = (1f - sp.trailOffset / 0.14f).coerceIn(0.2f, 1f)
                                val col = if (sp.gold) GlowBeamGold else EcoEmeraldBright
                                drawCircle(
                                    color = col.copy(alpha = 0.35f * fade),
                                    radius = (sp.radiusDp * 1.9f).dp.toPx(),
                                    center = Offset(sx, sy)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.92f * fade),
                                    radius = sp.radiusDp.dp.toPx(),
                                    center = Offset(sx, sy)
                                )
                            }
                        }
                        // Post-purification diagonal golden luster sheen
                        val sh = sheen.value
                        if (sh in 0.01f..0.99f) {
                            val cx = size.width * (-0.2f + sh * 1.4f)
                            val w = size.width * 0.28f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, GlowBeamGold.copy(alpha = 0.28f), Color.Transparent),
                                    startX = cx - w,
                                    endX = cx + w
                                )
                            )
                        }
                    }
                }

                // Status badge pill (top-left): POLLUTED -> PURIFYING -> PURIFIED ✨
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = when {
                        isClean -> EcoEmerald.copy(alpha = 0.94f)
                        wonMatch && sweep.value > 0.02f -> SaffronGoldDark.copy(alpha = 0.92f)
                        else -> CarthageCoral.copy(alpha = 0.92f)
                    },
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.75f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isClean -> Icons.Default.AutoAwesome
                                wonMatch && sweep.value > 0.02f -> Icons.Default.CleaningServices
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                isClean -> "PURIFIED!"
                                wonMatch && sweep.value > 0.02f -> "PURIFYING..."
                                else -> "POLLUTED"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = Color.White
                        )
                    }
                }

                // Replay Transformation Pill (top-right once done)
                if (wonMatch && phaseDone) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onReplayClick)
                            .testTag("replay_purification_button"),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF071124).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, GlowBeamGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Replay purification", tint = GlowBeamGold, modifier = Modifier.size(13.dp))
                            Text("Replay", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = GlowBeamGold)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (place.arabicName.isNotBlank()) "${place.name} • ${place.arabicName}" else place.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextOnDark
                )
                Text(
                    text = place.ecoFact,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedOnDark
                )
            }
        }
    }
}

/**
 * Shown on the player's FIRST win at a place:
 * Displays a prominent "Add to Album" label + CTA, then plays a Polaroid stamp & fly-into-album
 * animation that bounces and increments the Tunisia Album counter.
 */
@Composable
private fun FirstTimeAlbumUnlockCard(
    place: TunisianPlace,
    albumTotalCount: Int,
    onCollectChime: () -> Unit,
    onOpenAlbum: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var phase by remember(place.id) { mutableStateOf(AlbumCollectPhase.READY) }
    val flyProgress = remember(place.id) { Animatable(0f) }
    val albumBounce = remember(place.id) { Animatable(1f) }
    var displayedCount by remember(place.id, albumTotalCount) {
        mutableIntStateOf((albumTotalCount - 1).coerceAtLeast(0))
    }

    LaunchedEffect(phase) {
        if (phase == AlbumCollectPhase.ANIMATING) {
            onCollectChime()
            if (SettingsStore.current.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            flyProgress.snapTo(0f)
            // Beat 1: Stamp & pop (0 -> 0.42), Beat 2: Arc & dock into album badge (0.42 -> 1.0)
            flyProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1150, easing = FastOutSlowInEasing)
            )
            displayedCount = albumTotalCount
            albumBounce.snapTo(1.22f)
            albumBounce.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            if (SettingsStore.current.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            phase = AlbumCollectPhase.COLLECTED
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("first_time_album_unlock_card"),
        shape = RoundedCornerShape(22.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(
            width = 2.5.dp,
            brush = Brush.linearGradient(listOf(GlowBeamGold, EcoEmeraldBright, SaffronGold))
        ),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF15315E), Color(0xFF0A1A36))))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Discovery Label Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = SaffronGold.copy(alpha = 0.22f),
                border = BorderStroke(1.2.dp, GlowBeamGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GlowBeamGold, modifier = Modifier.size(14.dp))
                    Text(
                        text = when (phase) {
                            AlbumCollectPhase.READY -> "FIRST VICTORY HERE • ADD TO ALBUM"
                            AlbumCollectPhase.ANIMATING -> "ADDING TO TUNISIA ALBUM..."
                            AlbumCollectPhase.COLLECTED -> "UNLOCKED IN MAP & ALBUM!"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                        color = GlowBeamGold
                    )
                }
            }

            when (phase) {
                AlbumCollectPhase.READY -> {
                    Text(
                        text = "Add ${place.name} to your Tunisia Album",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "This landmark is now purified on the Tunisia Map. Collect its postcard into your Album!",
                        style = MaterialTheme.typography.bodySmall,
                        color = SidiSkyLight,
                        textAlign = TextAlign.Center
                    )
                    GlossyButton(
                        text = "Add to Album",
                        onClick = { phase = AlbumCollectPhase.ANIMATING },
                        icon = Icons.Default.CollectionsBookmark,
                        topColor = LimePlayTop,
                        bottomColor = LimePlayBottom,
                        lipColor = LimePlayLip,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "add_to_album_button"
                    )
                }

                AlbumCollectPhase.ANIMATING -> {
                    val p = flyProgress.value
                    // Stage area: Polaroid card lifts, stamps, and arcs down into the Tunisia Album badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Radiating golden celebration burst behind the polaroid
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.38f
                            val ringAlpha = if (p < 0.45f) (p / 0.45f) else (1f - (p - 0.45f) / 0.55f).coerceAtLeast(0f)
                            val radius = (32.dp.toPx() + p * 68.dp.toPx())
                            for (i in 0 until 12) {
                                val rad = (i * 30.0 + p * 45.0) * PI / 180.0
                                val px = cx + (cos(rad) * radius).toFloat()
                                val py = cy + (sin(rad) * radius * 0.65f).toFloat()
                                drawCircle(
                                    color = (if (i % 2 == 0) GlowBeamGold else EcoEmeraldBright).copy(alpha = 0.85f * ringAlpha),
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }

                        // Flying Polaroid Postcard
                        val beat1 = (p / 0.42f).coerceIn(0f, 1f)
                        val beat2 = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .width(142.dp)
                                .height(98.dp)
                                .graphicsLayer {
                                    val popScale = 0.86f + 0.22f * beat1
                                    val shrinkScale = 1f - 0.80f * beat2
                                    val s = popScale * shrinkScale
                                    scaleX = s
                                    scaleY = s
                                    translationY = (-8.dp.toPx() * beat1) + (82.dp.toPx() * beat2)
                                    rotationZ = -5f * beat1 + 14f * beat2
                                    alpha = (1f - beat2 * 0.92f).coerceIn(0f, 1f)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E2346),
                            border = BorderStroke(2.2.dp, GlowBeamGold),
                            shadowElevation = 10.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                Image(
                                    painter = painterResource(id = place.imageRes),
                                    contentDescription = place.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.Center),
                                    shape = RoundedCornerShape(8.dp),
                                    color = EcoEmerald.copy(alpha = 0.94f),
                                    border = BorderStroke(1.2.dp, GlowBeamGold)
                                ) {
                                    Text(
                                        text = "ADDED ✓",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Target Tunisia Album Dock Pill at bottom of stage
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    val dockGlowScale = 1f + 0.14f * beat2
                                    scaleX = dockGlowScale
                                    scaleY = dockGlowScale
                                },
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF08142C),
                            border = BorderStroke(2.dp, if (beat2 > 0.5f) EcoEmeraldBright else GlowBeamGold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = GlowBeamGold, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Tunisia Album • $displayedCount / 15",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                AlbumCollectPhase.COLLECTED -> {
                    Surface(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = albumBounce.value
                                scaleY = albumBounce.value
                            }
                            .testTag("album_collected_badge"),
                        shape = RoundedCornerShape(50),
                        color = EcoEmeraldDark.copy(alpha = 0.92f),
                        border = BorderStroke(2.dp, GlowBeamGold)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LimePlayTop, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${place.name} saved! Album $albumTotalCount / 15",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenAlbum,
                        border = BorderStroke(1.5.dp, GlowBeamGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("post_match_open_album")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GlowBeamGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View in Tunisia Album", color = GlowBeamGold, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonColumn(
    title: String,
    damage: Int,
    restoration: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = accent, maxLines = 1)
        Text(text = "$damage DMG", style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
        Text(text = "$restoration% Clean", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
    }
}

@Composable
private fun MiniMetric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
    }
}

@Composable
private fun PayoutBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    bgColor: Color,
    borderColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
            }
        }
    }
}
