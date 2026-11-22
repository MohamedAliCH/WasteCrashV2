package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.EnvironmentScene
import com.example.ui.components.GlossyButton
import com.example.ui.components.PlaceRouletteReel
import com.example.ui.components.RarityChip
import com.example.ui.components.TunisiaMapCanvas
import com.example.ui.theme.*
import kotlin.random.Random

@Composable
fun TunisiaMapScreen(
    profile: UserProfile,
    initialPlaceId: String? = null,
    onRouletteTick: () -> Unit = {},
    onRouletteLocked: () -> Unit = {},
    onBack: () -> Unit,
    onStartBattle: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showPlaceRoulette by remember { mutableStateOf(false) }
    var mapRouletteSpinToken by remember { mutableLongStateOf(0L) }
    var mapRouletteTargetId by remember { mutableStateOf("") }
    var mapRouletteSpinning by remember { mutableStateOf(false) }

    var selectedArenaFilter by remember { mutableIntStateOf(-1) }
    val currentArena = GameCatalog.arenaForTrophies(profile.trophies)
    val defaultPlace = remember(initialPlaceId, currentArena) {
        initialPlaceId?.let { GameCatalog.placeById(it) } ?: currentArena.places.first()
    }
    var selectedPlaceId by remember(initialPlaceId) { mutableStateOf(defaultPlace.id) }

    val visiblePlaces = remember(selectedArenaFilter) {
        if (selectedArenaFilter == -1) GameCatalog.allPlaces
        else GameCatalog.allPlaces.filter { it.arenaId == selectedArenaFilter }
    }

    val selectedPlace = remember(selectedPlaceId, visiblePlaces) {
        visiblePlaces.firstOrNull { it.id == selectedPlaceId } ?: visiblePlaces.first()
    }
    val selectedArena = GameCatalog.arenaById(selectedPlace.arenaId)
    val purifiedSet = remember(profile.purifiedPlaceIds) { profile.purifiedPlaceIds.toSet() }
    val selectedPurified = selectedPlace.id in purifiedSet
    val arenaPlayable = profile.trophies >= selectedArena.minTrophies
    val guardianBoss = GameCatalog.bossById(selectedPlace.guardianBossId)
    val guardianUnlocked = (profile.bossLevels[guardianBoss.id] ?: 0) > 0
    val purifiedPlacesCount = GameCatalog.allPlaces.count { it.id in purifiedSet }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1E42), DarkBgPrimary, Color(0xFF040A18))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceCard)
                            .border(1.5.dp, DarkBorderSubtle, CircleShape)
                            .testTag("tunisia_map_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnDark
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tunisia Eco-Map",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = TextOnDark
                        )
                        Text(
                            text = "Tap any pin across Tunisia to inspect its landmark",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedOnDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (purifiedPlacesCount > 0) EcoEmerald.copy(alpha = 0.24f) else SaffronGold.copy(alpha = 0.20f),
                    border = BorderStroke(1.5.dp, if (purifiedPlacesCount > 0) EcoEmeraldBright else SaffronGold)
                ) {
                    Text(
                        text = "$purifiedPlacesCount/15 Purified",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (purifiedPlacesCount > 0) LimePlayTop else GlowBeamGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Arena Filter Chips + Interactive Place Roulette Spin Action
            val filters = listOf(
                -1 to "All (15)",
                0 to "Beaches",
                1 to "Monuments",
                2 to "Sahara"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filters) { (arenaId, label) ->
                        val selected = selectedArenaFilter == arenaId
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedArenaFilter = arenaId
                                val nextList = if (arenaId == -1) GameCatalog.allPlaces else GameCatalog.allPlaces.filter { it.arenaId == arenaId }
                                if (nextList.none { it.id == selectedPlaceId }) {
                                    selectedPlaceId = nextList.first().id
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceCard,
                                labelColor = TextMutedOnDark,
                                selectedContainerColor = SaffronGold,
                                selectedLabelColor = DeepNavyInk
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = DarkBorderSubtle,
                                selectedBorderColor = GlowBeamGold
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            val pool = visiblePlaces
                            if (pool.isNotEmpty()) {
                                mapRouletteTargetId = pool[Random.nextInt(pool.size)].id
                                mapRouletteSpinToken = System.currentTimeMillis()
                                mapRouletteSpinning = true
                                showPlaceRoulette = true
                            }
                        }
                        .testTag("open_map_roulette_button"),
                    shape = RoundedCornerShape(50),
                    color = EcoEmerald,
                    border = BorderStroke(1.8.dp, GlowBeamGold)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Spin Place Roulette",
                            tint = GlowBeamGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Roulette",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }

            // Interactive Geographic Map of Tunisia
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .testTag("tunisia_interactive_map"),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF040B1A),
                border = BorderStroke(
                    2.5.dp,
                    Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop))
                ),
                shadowElevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TunisiaMapCanvas(
                        places = visiblePlaces,
                        selectedPlaceId = selectedPlace.id,
                        userTrophies = profile.trophies,
                        purifiedPlaceIds = purifiedSet,
                        onPlaceSelected = { tapped -> selectedPlaceId = tapped.id },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating North/South Legend Pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF08142B).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, DarkBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LegendDot(CeramicBlueTop, "Beaches")
                            LegendDot(SaffronGold, "Heritage")
                            LegendDot(GlowBeamOrange, "Sahara")
                        }
                    }

                    // Active Pin Pill at bottom-right
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF08142B).copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, SaffronGold.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = selectedPlace.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextOnDark
                            )
                        }
                    }
                }
            }

            // Quick Location Jump Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visiblePlaces, key = { it.id }) { place ->
                    val isSelected = place.id == selectedPlace.id
                    val isPurified = place.id in purifiedSet
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedPlaceId = place.id }
                            .testTag("map_chip_${place.id}"),
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) SaffronGold else DarkSurfaceCard,
                        border = BorderStroke(
                            1.5.dp,
                            if (isSelected) Color.White else if (isPurified) EcoEmeraldBright else Color(0xFF1E3259)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPurified) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = when {
                                    isSelected -> DeepNavyInk
                                    isPurified -> EcoEmeraldBright
                                    else -> TextMutedOnDark
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) DeepNavyInk else if (isPurified) TextOnDark else TextMutedOnDark
                            )
                        }
                    }
                }
            }

            // Selected Location Detail Showcase Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(2.dp, if (selectedPurified) EcoEmeraldBright.copy(alpha = 0.85f) else SaffronGold.copy(alpha = 0.55f)),
                shadowElevation = 10.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                    ) {
                        EnvironmentScene(
                            place = selectedPlace,
                            pollution = if (selectedPurified) 0f else 0.90f,
                            purified = selectedPurified,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, DarkBgPrimary.copy(alpha = 0.90f))
                                    )
                                )
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = if (selectedPurified) EcoEmerald else CarthageCoral.copy(alpha = 0.92f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedPurified) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (selectedPurified) {
                                        "${selectedPlace.regionTag} • PURIFIED"
                                    } else {
                                        "LOCKED • Win a match here to purify"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = selectedPlace.name,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = "${selectedPlace.heritageHighlight} • ${selectedArena.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SaffronCream
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = selectedPlace.ecoFact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextOnDark
                        )

                        // Guardian Boss Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.8.dp, guardianBoss.rarity.frameColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(guardianBoss.bodyColor.copy(alpha = 0.22f))
                                        .border(1.5.dp, guardianBoss.rarity.frameColor, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BossMonsterAvatar(
                                        boss = guardianBoss,
                                        animateIdle = selectedPurified,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "LOCATION GUARDIAN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SaffronGold
                                        )
                                        RarityChip(guardianBoss.rarity)
                                    }
                                    Text(
                                        text = guardianBoss.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TextOnDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (guardianUnlocked) "${guardianBoss.abilityName} • Captured" else "${guardianBoss.abilityName} • Win battles to capture",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (guardianUnlocked) EcoEmeraldBright else TextMutedOnDark
                                    )
                                }
                            }
                        }

                        if (arenaPlayable) {
                            GlossyButton(
                                text = if (selectedPurified) {
                                    "PLAY 1v1 IN ${selectedArena.subtitle.uppercase()}"
                                } else {
                                    "PURIFY ${selectedPlace.name.uppercase()} IN 1v1"
                                },
                                onClick = onStartBattle,
                                icon = Icons.Default.Bolt,
                                topColor = LimePlayTop,
                                bottomColor = LimePlayBottom,
                                lipColor = LimePlayLip,
                                height = 54.dp,
                                modifier = Modifier.fillMaxWidth(),
                                tag = "map_play_cta"
                            )
                        } else {
                            val needed = (selectedArena.minTrophies - profile.trophies).coerceAtLeast(0)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0A162D),
                                border = BorderStroke(1.5.dp, SaffronGold.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Earn $needed more Trophies to reach ${selectedArena.name} & purify ${selectedPlace.name}!",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SaffronCream
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPlaceRoulette) {
            Dialog(
                onDismissRequest = { if (!mapRouletteSpinning) showPlaceRoulette = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        PlaceRouletteReel(
                            places = visiblePlaces,
                            targetPlaceId = mapRouletteTargetId.ifBlank { selectedPlace.id },
                            spinToken = mapRouletteSpinToken,
                            title = "Place Selection",
                            spinDurationMs = 2600,
                            onTick = onRouletteTick,
                            onSpinFinished = { landed ->
                                mapRouletteSpinning = false
                                selectedPlaceId = landed.id
                                onRouletteLocked()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        IconButton(
                            onClick = { showPlaceRoulette = false },
                            enabled = !mapRouletteSpinning,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close roulette", tint = TextOnDark)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (visiblePlaces.isNotEmpty() && !mapRouletteSpinning) {
                                    val candidates = visiblePlaces.filter { it.id != mapRouletteTargetId }.ifEmpty { visiblePlaces }
                                    mapRouletteTargetId = candidates[Random.nextInt(candidates.size)].id
                                    mapRouletteSpinToken = System.currentTimeMillis()
                                    mapRouletteSpinning = true
                                }
                            },
                            enabled = !mapRouletteSpinning,
                            border = BorderStroke(1.5.dp, SaffronGold),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceCard.copy(alpha = 0.92f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("map_roulette_spin_again")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Spin Again", color = SaffronGold, fontWeight = FontWeight.ExtraBold)
                        }

                        GlossyButton(
                            text = "Select",
                            onClick = { showPlaceRoulette = false },
                            enabled = !mapRouletteSpinning,
                            topColor = LimePlayTop,
                            bottomColor = LimePlayBottom,
                            lipColor = LimePlayLip,
                            height = 48.dp,
                            modifier = Modifier.weight(1f),
                            tag = "map_roulette_confirm"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextOnDark
        )
    }
}
