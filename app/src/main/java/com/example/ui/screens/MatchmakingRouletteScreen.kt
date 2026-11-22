package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameCatalog
import com.example.network.EdgeWebSocketClient
import com.example.network.RadioLinkSnapshot
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.HomeNetworkBadge
import com.example.ui.components.PlaceRouletteReel
import com.example.ui.components.RarityChip
import com.example.ui.theme.*
import com.example.viewmodel.MatchmakingPhase
import com.example.viewmodel.MatchmakingUiState

@Composable
fun MatchmakingRouletteScreen(
    state: MatchmakingUiState,
    linkState: RadioLinkSnapshot = RadioLinkSnapshot(),
    onPlayPracticeBot: () -> Unit = {},
    onRouletteTick: () -> Unit = {},
    onCancel: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedCode by remember(state.roomCode) { mutableStateOf(false) }
    val arena = GameCatalog.arenaById(state.arenaId)
    val boss = GameCatalog.bossById(state.sharedBossId)

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1B3D), Color(0xFF071124), Color(0xFF040914))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EcoEmerald.copy(alpha = 0.25f),
                    border = BorderStroke(1.5.dp, LimePlayTop)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = LimePlayTop, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when (state.phase) {
                                MatchmakingPhase.SEARCHING -> "ONLINE QUEUE • ${state.searchElapsedSec}s"
                                MatchmakingPhase.ROOM_HOSTING -> "PRIVATE ROOM • ${state.searchElapsedSec}s"
                                else -> "1v1 SESSION LOCKED!"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HomeNetworkBadge(
                        link = linkState,
                        hasEdgeUrl = EdgeWebSocketClient.isValidWebSocketUrl(state.edgeServerUrl),
                        onClick = {}
                    )
                    if (state.phase == MatchmakingPhase.SEARCHING || state.phase == MatchmakingPhase.ROOM_HOSTING) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("cancel_matchmaking_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Matchmaking", tint = Color.White)
                        }
                    }
                }
            }

            if (state.phase == MatchmakingPhase.ROOM_HOSTING) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = SaffronGold,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Private 1v1 Room Ready",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = DarkSurfaceCard,
                        border = BorderStroke(3.dp, Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, LimePlayTop))),
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SHARE THIS CODE WITH PHONE 2",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = SidiSkyLight
                            )
                            Text(
                                text = state.roomCode,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 6.sp
                                ),
                                color = GlowBeamGold,
                                modifier = Modifier.testTag("hosted_room_code_text")
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.roomCode))
                                    copiedCode = true
                                },
                                border = BorderStroke(1.5.dp, if (copiedCode) LimePlayTop else SaffronGold),
                                modifier = Modifier.testTag("copy_room_code_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (copiedCode) LimePlayTop else SaffronGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (copiedCode) "Copied!" else "Copy Code",
                                    color = if (copiedCode) LimePlayTop else SaffronGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (EdgeWebSocketClient.isValidWebSocketUrl(state.edgeServerUrl)) {
                                Text(
                                    text = "Edge WS Relay: ${state.edgeServerUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF22D3EE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = LimePlayTop, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                        Text(
                            text = "Waiting for rival to enter code...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SidiSkyLight
                        )
                    }
                }

                GlossyButton(
                    text = "Close Room",
                    onClick = onCancel,
                    topColor = BossHpRedTop,
                    bottomColor = CarthageCoral,
                    lipColor = CarthageCoralDark,
                    modifier = Modifier.fillMaxWidth(0.75f),
                    tag = "close_room_cta"
                )
            } else if (state.phase == MatchmakingPhase.SEARCHING) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier.size(204.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .rotate(orbitAngle)
                                .border(
                                    width = 3.dp,
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            SaffronGold,
                                            Color.Transparent,
                                            CeramicBlueTop,
                                            Color.Transparent,
                                            SaffronGold
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(172.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(SaffronGold.copy(alpha = 0.25f), DarkSurfaceCard)
                                    )
                                )
                                .border(
                                    width = 3.5.dp,
                                    brush = Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, SaffronGoldDark)),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = arena.bannerRes),
                                contentDescription = arena.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(144.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    Text(
                        text = "Finding 1v1 Rival...",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DeepNavyInk,
                        border = BorderStroke(1.5.dp, SaffronGold)
                    ) {
                        Text(
                            text = "${arena.name}  •  ${state.myTrophies} 🏆 (${state.trophyRangeLabel})",
                            style = MaterialTheme.typography.labelLarge,
                            color = SaffronGold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    CircularProgressIndicator(color = LimePlayTop, strokeWidth = 4.dp)
                    Text(
                        text = "Syncing with Firebase Arena Queue across Tunisia's ${arena.subtitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SidiSkyLight,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onPlayPracticeBot,
                        border = BorderStroke(1.5.dp, SidiSkyLight.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(46.dp)
                            .testTag("play_practice_bot_now_button")
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = SidiSkyLight, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play Practice Bot Instead", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    GlossyButton(
                        text = "Cancel Search",
                        onClick = onCancel,
                        topColor = BossHpRedTop,
                        bottomColor = CarthageCoral,
                        lipColor = CarthageCoralDark,
                        modifier = Modifier.fillMaxWidth(0.78f),
                        tag = "cancel_search_cta"
                    )
                }
            } else {
                // VS Competitor Showcase + Spinning 5-Place Tunisia Roulette Wheel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompetitorPreviewCard(
                            name = state.myName,
                            trophies = state.myTrophies,
                            deckIds = state.myDeckIds,
                            borderColor = LimePlayTop,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(GlowBeamGold, SaffronGold)))
                                .border(2.5.dp, DeepNavyInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = DeepNavyInk
                            )
                        }
                        CompetitorPreviewCard(
                            name = state.opponentName,
                            trophies = state.opponentTrophies,
                            deckIds = state.opponentDeckIds,
                            borderColor = CarthageCoral,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Horizontal Card-Strip Place Roulette (top & bottom pointers framing landmark cards)
                    PlaceRouletteReel(
                        places = arena.places,
                        targetPlaceId = state.lockedPlaceId,
                        spinToken = state.rouletteSpinToken,
                        title = "Place Selection",
                        spinDurationMs = 3050,
                        onTick = onRouletteTick,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Shared Boss Preview Card with Real Uploaded Sprite!
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = BattleBoardCell,
                        border = BorderStroke(2.5.dp, boss.rarity.frameColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BossMonsterAvatar(boss = boss, modifier = Modifier.size(66.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "SHARED BOSS SUMMON",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SaffronGold
                                    )
                                    RarityChip(boss.rarity)
                                }
                                Text(
                                    text = boss.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "First to purify captures +1 ${boss.name} card!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SidiSkyLight
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CompetitorPreviewCard(
    name: String,
    trophies: Int,
    deckIds: List<String>,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = BattleBoardCell,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(text = "$trophies", style = MaterialTheme.typography.labelSmall, color = SaffronGold)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                deckIds.take(3).forEach { id ->
                    val b = GameCatalog.bossById(id)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(b.bodyColor.copy(alpha = 0.25f))
                            .border(1.5.dp, b.rarity.frameColor, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = b, animateIdle = false, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
