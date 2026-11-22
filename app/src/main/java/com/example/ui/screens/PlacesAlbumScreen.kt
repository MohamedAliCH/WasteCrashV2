package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ArenaDefinition
import com.example.model.GameCatalog
import com.example.model.TunisianPlace
import com.example.model.UserProfile
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesAlbumScreen(
    profile: UserProfile,
    onViewOnMap: (String) -> Unit
) {
    var inspectedPlace by remember { mutableStateOf<TunisianPlace?>(null) }

    val purifiedSet = remember(profile.purifiedPlaceIds) { profile.purifiedPlaceIds.toSet() }
    val unlockedPlacesCount = GameCatalog.allPlaces.count { it.id in purifiedSet }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1D3B), Color(0xFF071329), Color(0xFF040A18))
                )
            )
    ) {
        // Subtle Tunisian Zellij 8-point geometric star lattice backdrop
        ZellijPatternBackdrop(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 74.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(GameCatalog.arenas, key = { it.id }) { arena ->
                val arenaUnlockedCount = arena.places.count { it.id in purifiedSet }
                val arenaUnlocked = arenaUnlockedCount > 0

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metallic Beveled Arena Section Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0E2244),
                        border = BorderStroke(
                            1.5.dp,
                            if (arenaUnlocked) SaffronGold.copy(alpha = 0.85f) else Color(0xFF5D7CA6)
                        ),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF17335E), Color(0xFF0C1E3D))
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Arena ${arena.id} • ${arena.subtitle}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (arenaUnlocked) GlowBeamGold else Color(0xFFC8DAF2),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$arenaUnlockedCount / ${arena.places.size}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (arenaUnlocked) GlowBeamGold else Color(0xFFD6E4F8)
                            )
                        }
                    }

                    // Staggered Row 1: 3 Tilted Landscape Polaroid Cards
                    val topPlaces = arena.places.take(3)
                    val topTilts = listOf(-2.2f, 1.4f, -1.7f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topPlaces.forEachIndexed { idx, place ->
                            AlbumPolaroidCard(
                                place = place,
                                arena = arena,
                                unlocked = place.id in purifiedSet,
                                guardianCaptured = (profile.bossLevels[place.guardianBossId] ?: 0) > 0,
                                tiltDegrees = topTilts.getOrElse(idx) { 0f },
                                onClick = { inspectedPlace = place },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Staggered Row 2: 2 Centered Tilted Landscape Polaroid Cards
                    val bottomPlaces = arena.places.drop(3).take(2)
                    val bottomTilts = listOf(1.9f, -1.9f)
                    if (bottomPlaces.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(0.68f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                bottomPlaces.forEachIndexed { idx, place ->
                                    AlbumPolaroidCard(
                                        place = place,
                                        arena = arena,
                                        unlocked = place.id in purifiedSet,
                                        guardianCaptured = (profile.bossLevels[place.guardianBossId] ?: 0) > 0,
                                        tiltDegrees = bottomTilts.getOrElse(idx) { 0f },
                                        onClick = { inspectedPlace = place },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Capsule Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .testTag("album_total_pill"),
            shape = RoundedCornerShape(50),
            color = Color(0xFF071124).copy(alpha = 0.94f),
            border = BorderStroke(
                1.5.dp,
                if (unlockedPlacesCount >= 15) SaffronGold else Color(0xFF5D7CA6)
            ),
            shadowElevation = 10.dp
        ) {
            Text(
                text = "Tunisia Album : $unlockedPlacesCount / 15",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (unlockedPlacesCount > 0) GlowBeamGold else TextOnDark,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }

    // Postcard Detail Bottom Sheet
    inspectedPlace?.let { place ->
        val arena = GameCatalog.arenaById(place.arenaId)
        val unlocked = place.id in purifiedSet
        val guardian = GameCatalog.bossById(place.guardianBossId)
        val guardianCaptured = (profile.bossLevels[guardian.id] ?: 0) > 0

        ModalBottomSheet(
            onDismissRequest = { inspectedPlace = null },
            containerColor = DarkSurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, if (unlocked) EcoEmeraldBright else SaffronGold.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                ) {
                    com.example.ui.components.EnvironmentScene(
                        place = place,
                        pollution = if (unlocked) 0f else 0.90f,
                        purified = unlocked,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, DarkBgPrimary.copy(alpha = 0.9f)))
                            )
                    )
                    if (!unlocked) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF071124).copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, SaffronGold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Win a match here to purify",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = GlowBeamGold
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "${place.regionTag.uppercase()} • ${arena.name.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SaffronGold
                        )
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = place.heritageHighlight,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = CeramicBlueTop
                )
                Text(
                    text = place.ecoFact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextOnDark
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.5.dp, guardian.rarity.frameColor)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BossMonsterAvatar(boss = guardian, animateIdle = false, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("GUARDIAN", style = MaterialTheme.typography.labelSmall, color = SaffronGold)
                                RarityChip(guardian.rarity)
                            }
                            Text(guardian.name, style = MaterialTheme.typography.titleSmall, color = TextOnDark)
                            Text(
                                text = if (guardianCaptured) "Captured in your Eco-Deck" else "Purify this region to capture",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (guardianCaptured) EcoEmeraldBright else TextMutedOnDark
                            )
                        }
                    }
                }

                GlossyButton(
                    text = "View on Tunisia Map",
                    onClick = {
                        val targetId = place.id
                        inspectedPlace = null
                        onViewOnMap(targetId)
                    },
                    icon = Icons.Default.Map,
                    topColor = CeramicBlueTop,
                    bottomColor = CeramicBlueBottom,
                    lipColor = CeramicBlueLip,
                    height = 50.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AlbumPolaroidCard(
    place: TunisianPlace,
    arena: ArenaDefinition,
    unlocked: Boolean,
    guardianCaptured: Boolean,
    tiltDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val guardian = remember(place.guardianBossId) { GameCatalog.bossById(place.guardianBossId) }
    val desaturatedFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })
    }

    Surface(
        modifier = modifier
            .height(104.dp)
            .graphicsLayer { rotationZ = tiltDegrees }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${place.id}"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0E2346),
        border = if (unlocked) {
            BorderStroke(1.8.dp, Brush.linearGradient(listOf(GlowBeamGold, SaffronGold)))
        } else {
            BorderStroke(1.5.dp, Color(0xFF7B9AC4))
        },
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Photo Viewport (~72% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.72f)
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF050C1A))
            ) {
                Image(
                    painter = painterResource(id = place.imageRes),
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (unlocked) null else desaturatedFilter,
                    modifier = Modifier.fillMaxSize()
                )

                if (unlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFF061022).copy(alpha = 0.55f))
                                )
                            )
                    )
                    // Subtle emerald discovery dot at top-left
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EcoEmeraldBright)
                            .border(1.dp, Color.White, CircleShape)
                    )
                    // Tiny Guardian Boss medallion in top-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF08142C).copy(alpha = 0.88f))
                            .border(
                                1.2.dp,
                                if (guardianCaptured) EcoEmeraldBright else guardian.rarity.frameColor,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(
                            boss = guardian,
                            animateIdle = false,
                            modifier = Modifier
                                .size(20.dp)
                                .alpha(if (guardianCaptured) 1f else 0.7f)
                        )
                    }
                } else {
                    // Shadowy monochrome scrim + centered glowing golden padlock
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF050B16).copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(SaffronGold.copy(alpha = 0.42f), Color.Transparent)
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = GlowBeamGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Polaroid Caption Bar (~28% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.28f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unlocked) place.name else "???",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (unlocked) Color.White else Color(0xFFD6E4F8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ZellijPatternBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 52.dp.toPx()
        val motifSize = step * 0.42f
        val lineColor = Color(0xFF3CB4FF).copy(alpha = 0.055f)
        val stroke = Stroke(width = 1.dp.toPx())

        val cols = (size.width / step).toInt() + 2
        val rows = (size.height / step).toInt() + 2
        for (r in 0..rows) {
            for (c in 0..cols) {
                val cx = c * step
                val cy = r * step
                val topLeft = Offset(cx - motifSize / 2f, cy - motifSize / 2f)
                val boxSize = Size(motifSize, motifSize)
                drawRect(color = lineColor, topLeft = topLeft, size = boxSize, style = stroke)
                rotate(degrees = 45f, pivot = Offset(cx, cy)) {
                    drawRect(color = lineColor, topLeft = topLeft, size = boxSize, style = stroke)
                }
            }
        }
    }
}
