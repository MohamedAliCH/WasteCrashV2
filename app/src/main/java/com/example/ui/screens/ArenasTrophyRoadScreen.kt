package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ArenaDefinition
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.StatPill
import com.example.ui.theme.*

@Composable
fun ArenasTrophyRoadScreen(
    profile: UserProfile,
    onOpenPlaceOnMap: (String) -> Unit = {}
) {
    val currentArena = GameCatalog.arenaForTrophies(profile.trophies)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1E42), DarkBgPrimary, Color(0xFF050D1D))
                )
            )
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tunisia Trophy Road", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
                    Text("3 Environments • 15 Places • Cumulative Boss Pools", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
                }
                StatPill(
                    value = "${profile.trophies}",
                    icon = Icons.Default.EmojiEvents,
                    badgeColor = SaffronGold
                )
            }
        }

        items(GameCatalog.arenas, key = { it.id }) { arena ->
            val unlocked = profile.trophies >= arena.minTrophies
            val isCurrent = arena.id == currentArena.id
            ArenaRoadCard(
                arena = arena,
                unlocked = unlocked,
                isCurrent = isCurrent,
                userTrophies = profile.trophies,
                onPlaceClick = onOpenPlaceOnMap
            )
        }
    }
}

@Composable
private fun ArenaRoadCard(
    arena: ArenaDefinition,
    unlocked: Boolean,
    isCurrent: Boolean,
    userTrophies: Int,
    onPlaceClick: (String) -> Unit
) {
    val arenaBosses = GameCatalog.bosses.filter { it.arenaId == arena.id }
    val cumulativeCount = GameCatalog.cumulativeBossPoolForArena(arena.id).size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(
            width = if (isCurrent) 3.dp else 1.8.dp,
            color = if (isCurrent) SaffronGold else DarkBorderSubtle
        ),
        shadowElevation = if (isCurrent) 10.dp else 4.dp
    ) {
        Column {
            // Environment Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
            ) {
                Image(
                    painter = painterResource(id = arena.bannerRes),
                    contentDescription = arena.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = if (unlocked) 0.15f else 0.55f),
                                    DeepNavyInk.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Trophy Requirement Chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = if (unlocked) EcoEmerald else DeepNavyInk.copy(alpha = 0.85f),
                    border = BorderStroke(1.5.dp, Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isCurrent) "CURRENT ARENA • ${arena.minTrophies}+" else "${arena.minTrophies}+ Trophies",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(arena.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(arena.subtitle, style = MaterialTheme.typography.labelMedium, color = SaffronCream)
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 5 Tunisian Places List
                Text("5 Roulette Locations (Tap to view on Map):", style = MaterialTheme.typography.titleSmall, color = TextOnDark)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    arena.places.forEach { place ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlaceClick(place.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, DarkBorderSubtle.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${place.name} (${place.regionTag})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextOnDark
                                    )
                                    Text(
                                        text = place.ecoFact,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                                        color = TextMutedOnDark
                                    )
                                }
                            }
                        }
                    }
                }

                // Cumulative Boss Pool Preview
                HorizontalDivider(color = DarkBorderSubtle.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New Unlockable Bosses (5):", style = MaterialTheme.typography.titleSmall, color = TextOnDark)
                    Text(
                        text = "Active Pool: $cumulativeCount Bosses",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CeramicBlueTop
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    arenaBosses.forEach { boss ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(boss.bodyColor.copy(alpha = 0.22f))
                                    .border(2.dp, boss.rarity.frameColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                BossMonsterAvatar(boss = boss, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = boss.name.substringBefore("-"),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnDark,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (!unlocked) {
                    val missing = (arena.minTrophies - userTrophies).coerceAtLeast(0)
                    Text(
                        text = "Earn $missing more Trophies to unlock ${arena.name} & expand your Boss pool to $cumulativeCount Bosses!",
                        style = MaterialTheme.typography.labelMedium,
                        color = SaffronGold
                    )
                }
            }
        }
    }
}
