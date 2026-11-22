package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoardTile
import com.example.model.GameCatalog
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.Match3BoardGrid
import com.example.ui.components.RarityChip
import com.example.ui.theme.*
import com.example.viewmodel.SpectatorPerspective
import com.example.viewmodel.SpectatorTheatreState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectatorTheatreScreen(
    state: SpectatorTheatreState,
    onSetPerspective: (SpectatorPerspective) -> Unit,
    onToggleDeckInspector: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onScrubFrame: (Int) -> Unit,
    onSendCheer: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val arena = GameCatalog.arenaById(state.arenaId)
    val place = GameCatalog.placeById(state.placeId)
    val boss = GameCatalog.bossById(state.bossId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BattleBoardDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (state.isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = SaffronGold)
                Spacer(Modifier.height(10.dp))
                Text("Loading Spectator Feed...", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.TvOff, contentDescription = null, tint = CarthageCoral, modifier = Modifier.size(48.dp))
                Text(state.errorMessage, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Button(onClick = onBack) { Text("Back to Watch Hub") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 540.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Theatre Bar: Back, Live/Replay badge, Place, Deck Inspector button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp).testTag("spectator_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (state.isLiveMode) CarthageCoral else SidiCobalt
                        ) {
                            Text(
                                text = if (state.isLiveMode) "${state.statusText} • ${state.spectatorCount} 👁" else "REPLAY • ${state.currentElapsedSec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Prominent Deck Inspector Button!
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onToggleDeckInspector)
                            .testTag("spectator_inspect_decks_button"),
                        shape = RoundedCornerShape(50),
                        color = SaffronGold,
                        border = BorderStroke(2.dp, DeepNavyInk)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Style, contentDescription = "Inspect Decks", tint = DeepNavyInk, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("View Decks", style = MaterialTheme.typography.labelMedium, color = DeepNavyInk)
                        }
                    }
                }

                // Perspective Switcher: Player 1 Screen | Opponent Screen | Dual Split!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BattleBoardCell)
                        .border(1.5.dp, SidiCobalt, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PerspectiveChip(
                        label = state.p1Name.take(10),
                        selected = state.perspective == SpectatorPerspective.PLAYER_1,
                        onClick = { onSetPerspective(SpectatorPerspective.PLAYER_1) },
                        modifier = Modifier.weight(1f).testTag("perspective_p1")
                    )
                    PerspectiveChip(
                        label = "Opponent (${state.p2Name.take(8)})",
                        selected = state.perspective == SpectatorPerspective.OPPONENT,
                        onClick = { onSetPerspective(SpectatorPerspective.OPPONENT) },
                        modifier = Modifier.weight(1.2f).testTag("perspective_opponent")
                    )
                    PerspectiveChip(
                        label = "Dual View",
                        selected = state.perspective == SpectatorPerspective.DUAL_SPLIT,
                        onClick = { onSetPerspective(SpectatorPerspective.DUAL_SPLIT) },
                        modifier = Modifier.weight(0.9f).testTag("perspective_dual")
                    )
                }

                // Event / Cheer Callout Strip
                val ticker = state.cheerBanner.ifBlank { state.currentEventText.ifBlank { "${place.name} • Shared Boss: ${boss.name}" } }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = BattleBoardCell,
                    border = BorderStroke(1.dp, SaffronGold.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = ticker,
                        style = MaterialTheme.typography.labelMedium,
                        color = SaffronCream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Main Viewing Viewport (Single Player Screen OR Opponent Screen OR Dual Side-by-Side!)
                if (state.perspective == SpectatorPerspective.DUAL_SPLIT) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactSpectatorBoardPane(
                                playerName = state.p1Name,
                                trophies = state.p1Trophies,
                                wave = state.p1Wave,
                                bossHp = state.p1BossHp,
                                restorationPct = state.p1RestorationPct,
                                board = state.p1Board,
                                accentColor = EcoEmeraldBright,
                                modifier = Modifier.weight(1f)
                            )
                            CompactSpectatorBoardPane(
                                playerName = state.p2Name,
                                trophies = state.p2Trophies,
                                wave = state.p2Wave,
                                bossHp = state.p2BossHp,
                                restorationPct = state.p2RestorationPct,
                                board = state.p2Board,
                                accentColor = CarthageCoral,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Compact Inline Both-Decks Comparison Strip
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = BattleBoardCell,
                            border = BorderStroke(1.5.dp, SidiCobalt)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("EQUIPPED ECO-DECKS COMPARISON", style = MaterialTheme.typography.labelSmall, color = SaffronGold)
                                DeckInlineRow(state.p1Name, state.p1DeckIds, state.p1DeckLevels, state.p1Charges, EcoEmeraldBright)
                                DeckInlineRow(state.p2Name, state.p2DeckIds, state.p2DeckLevels, state.p2Charges, Color(0xFFFF8A80))
                            }
                        }
                    }
                } else {
                    val viewingOpponent = state.perspective == SpectatorPerspective.OPPONENT
                    val activeName = if (viewingOpponent) state.p2Name else state.p1Name
                    val activeTrophies = if (viewingOpponent) state.p2Trophies else state.p1Trophies
                    val activeWave = if (viewingOpponent) state.p2Wave else state.p1Wave
                    val activeBossHp = if (viewingOpponent) state.p2BossHp else state.p1BossHp
                    val activeBossMax = if (viewingOpponent) state.p2BossMaxHp else state.p1BossMaxHp
                    val activeRest = if (viewingOpponent) state.p2RestorationPct else state.p1RestorationPct
                    val activeDamage = if (viewingOpponent) state.p2Damage else state.p1Damage
                    val activeBoard = if (viewingOpponent) state.p2Board else state.p1Board
                    val activeDeckIds = if (viewingOpponent) state.p2DeckIds else state.p1DeckIds
                    val activeDeckLevels = if (viewingOpponent) state.p2DeckLevels else state.p1DeckLevels
                    val activeCharges = if (viewingOpponent) state.p2Charges else state.p1Charges

                    // Upper Environment Restoration Window for Selected Player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.75f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(2.dp, if (viewingOpponent) CarthageCoral else EcoEmeraldBright, RoundedCornerShape(18.dp))
                    ) {
                        // Same living scene as in battle: the place cleans up as the replay advances
                        com.example.ui.components.EnvironmentScene(
                            place = place,
                            pollution = 1f - activeRest / 100f,
                            purified = activeRest >= 100,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(shape = RoundedCornerShape(50), color = DeepNavyInk.copy(alpha = 0.85f)) {
                                Text(
                                    text = "VIEWING: $activeName ($activeTrophies 🏆)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (viewingOpponent) Color(0xFFFF8A80) else EcoEmeraldBright,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(50), color = EcoEmeraldDark.copy(alpha = 0.9f)) {
                                Text(
                                    text = "Clean $activeRest% • $activeDamage DMG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BossMonsterAvatar(
                                boss = boss,
                                isMiniTrashWave = activeWave < com.example.engine.BattleRules.BOSS_WAVE_INDEX,
                                waveIndex = activeWave,
                                modifier = Modifier.size(76.dp)
                            )
                            Surface(shape = RoundedCornerShape(50), color = DeepNavyInk.copy(alpha = 0.88f)) {
                                Text(
                                    text = if (activeWave < com.example.engine.BattleRules.BOSS_WAVE_INDEX) {
                                        "Wave $activeWave / ${com.example.engine.BattleRules.MINI_WAVE_COUNT}"
                                    } else {
                                        "${boss.name}: $activeBossHp / $activeBossMax HP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SaffronGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Active Competitor's 3-Slot Eco-Deck Charge Strip
                    DeckInlineRow(
                        ownerName = activeName,
                        deckIds = activeDeckIds,
                        deckLevels = activeDeckLevels,
                        charges = activeCharges,
                        accent = if (viewingOpponent) Color(0xFFFF8A80) else EcoEmeraldBright
                    )

                    // Live / Recorded 7x7 Board of Selected Competitor!
                    Match3BoardGrid(
                        board = activeBoard,
                        selectedTile = null,
                        interactive = false,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                // Bottom Bar: Replay Controls OR Live Cheer Bar
                if (!state.isLiveMode && state.replayFramesCount > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = BattleBoardCell,
                        border = BorderStroke(1.5.dp, SidiCobalt)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = onTogglePlayPause,
                                    modifier = Modifier.size(44.dp).testTag("replay_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (state.isPlayingReplay) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Play or pause replay",
                                        tint = EcoEmeraldBright,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Slider(
                                    value = state.currentFrameIndex.toFloat(),
                                    onValueChange = { onScrubFrame(it.toInt()) },
                                    valueRange = 0f..(state.replayFramesCount - 1).coerceAtLeast(1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = SaffronGold,
                                        activeTrackColor = EcoEmeraldBright
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onCycleSpeed) {
                                    Text("${state.playbackSpeed}x", style = MaterialTheme.typography.labelLarge, color = SaffronGold)
                                }
                            }
                        }
                    }
                } else {
                    // Live Spectator Cheer Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CheerButton("Eco Surge!", onClick = { onSendCheer("Spectators cheered: Eco Surge!") }, modifier = Modifier.weight(1f))
                        CheerButton("Clean Sweep!", onClick = { onSendCheer("Spectators cheered: Clean Sweep!") }, modifier = Modifier.weight(1f))
                        CheerButton("Go Tunisia!", onClick = { onSendCheer("Spectators cheered: Go Tunisia!") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Full Dual Eco-Deck Inspector Bottom Sheet
    if (state.showDeckInspector) {
        ModalBottomSheet(
            onDismissRequest = onToggleDeckInspector,
            containerColor = SurfaceWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Match Eco-Decks Inspector", style = MaterialTheme.typography.headlineMedium, color = DeepNavyInk)
                DeckInspectorSection(
                    playerName = state.p1Name,
                    trophies = state.p1Trophies,
                    deckIds = state.p1DeckIds,
                    deckLevels = state.p1DeckLevels,
                    charges = state.p1Charges,
                    badgeColor = EcoEmerald
                )
                HorizontalDivider(color = SurfaceSoftBlue)
                DeckInspectorSection(
                    playerName = "${state.p2Name} (Opponent)",
                    trophies = state.p2Trophies,
                    deckIds = state.p2DeckIds,
                    deckLevels = state.p2DeckLevels,
                    charges = state.p2Charges,
                    badgeColor = CarthageCoral
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun PerspectiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) SaffronGold else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) DeepNavyInk else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactSpectatorBoardPane(
    playerName: String,
    trophies: Int,
    wave: Int,
    bossHp: Int,
    restorationPct: Int,
    board: List<List<BoardTile>>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = BattleBoardCell,
            border = BorderStroke(1.5.dp, accentColor)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(playerName, style = MaterialTheme.typography.labelMedium, color = accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$trophies 🏆 • Clean $restorationPct%", style = MaterialTheme.typography.labelSmall, color = Color.White)
                Text(
                    if (wave < com.example.engine.BattleRules.BOSS_WAVE_INDEX) "Wave $wave/${com.example.engine.BattleRules.MINI_WAVE_COUNT}" else "Boss $bossHp HP",
                    style = MaterialTheme.typography.labelSmall,
                    color = SaffronGold
                )
            }
        }
        Match3BoardGrid(
            board = board,
            selectedTile = null,
            interactive = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DeckInlineRow(
    ownerName: String,
    deckIds: List<String>,
    deckLevels: List<Int>,
    charges: List<Int>,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        deckIds.take(3).forEachIndexed { idx, bossId ->
            val boss = GameCatalog.bossById(bossId)
            val lvl = deckLevels.getOrElse(idx) { 1 }
            val chg = charges.getOrElse(idx) { 0 }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = BattleBoardCell,
                border = BorderStroke(1.5.dp, boss.rarity.frameColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BossMonsterAvatar(boss = boss, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(boss.name.substringBefore("-"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White, maxLines = 1)
                        Text("Lv.$lvl • $chg%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckInspectorSection(
    playerName: String,
    trophies: Int,
    deckIds: List<String>,
    deckLevels: List<Int>,
    charges: List<Int>,
    badgeColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(badgeColor))
            Text("$playerName  •  $trophies Trophies", style = MaterialTheme.typography.titleMedium, color = DeepNavyInk)
        }
        deckIds.take(3).forEachIndexed { idx, bossId ->
            val boss = GameCatalog.bossById(bossId)
            val lvl = deckLevels.getOrElse(idx) { 1 }
            val chg = charges.getOrElse(idx) { 0 }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceSoftBlue,
                border = BorderStroke(1.5.dp, boss.rarity.frameColor)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BossMonsterAvatar(boss = boss, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${boss.name} (Lv.$lvl)", style = MaterialTheme.typography.titleSmall, color = DeepNavyInk)
                            RarityChip(boss.rarity)
                        }
                        Text("${boss.abilityName} • ${boss.abilityDamageAtLevel(lvl)} DMG", style = MaterialTheme.typography.labelSmall, color = SidiCobalt)
                        Text("Charge: $chg% • Boosts ${boss.preferredWaste.displayName}s", style = MaterialTheme.typography.labelSmall, color = MutedSlateText)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = SidiCobalt,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}
