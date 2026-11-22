package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BossDefinition
import com.example.model.BossRarity
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossCollectionScreen(
    profile: UserProfile,
    onEquipBoss: (slotIndex: Int, bossId: String) -> Unit,
    onUpgradeBoss: (bossId: String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var inspectedBoss by remember { mutableStateOf<BossDefinition?>(null) }

    val filters = listOf(
        "ALL" to "All (15)",
        "A0" to "Beaches",
        "A1" to "Monuments",
        "A2" to "Desert",
        "EPIC" to "Epic Only"
    )

    val filteredBosses = remember(selectedFilter) {
        when (selectedFilter) {
            "A0" -> GameCatalog.bosses.filter { it.arenaId == 0 }
            "A1" -> GameCatalog.bosses.filter { it.arenaId == 1 }
            "A2" -> GameCatalog.bosses.filter { it.arenaId == 2 }
            "EPIC" -> GameCatalog.bosses.filter { it.rarity == BossRarity.EPIC }
            else -> GameCatalog.bosses
        }
    }

    val unlockedCount = GameCatalog.bosses.count { (profile.bossLevels[it.id] ?: 0) > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1E42), DarkBgPrimary, Color(0xFF050D1D))
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Eco-Coins
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Eco-Deck & Bosses", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
                Text("Purified $unlockedCount / 15 Tunisian Bosses", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
            }
            StatPill(
                value = "${profile.ecoCoins}",
                icon = Icons.Default.MonetizationOn,
                badgeColor = EcoEmeraldBright
            )
        }

        // Active 3-Slot Eco-Deck Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = DarkSurfaceCard,
            border = BorderStroke(
                2.dp,
                Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, CeramicBlueTop))
            ),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.verticalGradient(listOf(Color(0xFF163268), DarkSurfaceCard)))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ACTIVE ECO-DECK (3 SLOTS)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = GlowBeamGold)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.equippedDeckIds.take(3).forEachIndexed { slotIdx, bossId ->
                        val boss = GameCatalog.bossById(bossId)
                        val lvl = profile.bossLevels[bossId] ?: 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { inspectedBoss = boss }
                                .testTag("deck_slot_card_$slotIdx"),
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(2.dp, boss.rarity.frameColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SLOT ${slotIdx + 1}", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
                                BossMonsterAvatar(boss = boss, animateIdle = false, modifier = Modifier.size(46.dp))
                                Text(
                                    text = boss.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextOnDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Image(
                                        painter = painterResource(boss.preferredWaste.spriteRes),
                                        contentDescription = boss.preferredWaste.shortLabel,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${boss.preferredWaste.shortLabel} • Lv.$lvl",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                                        color = EcoEmeraldBright,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                val dupNatures = profile.equippedDeckIds.take(3)
                    .map { GameCatalog.bossById(it).preferredWaste }
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                if (dupNatures.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Shared ${dupNatures.joinToString { it.shortLabel }} nature: casting one skill resets the other.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = SaffronCream
                        )
                    }
                }
            }
        }

        // Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { (key, label) ->
                val selected = selectedFilter == key
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = key },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) },
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

        // 15-Boss Collection Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredBosses, key = { it.id }) { boss ->
                val lvl = profile.bossLevels[boss.id] ?: 0
                val copies = profile.bossCopies[boss.id] ?: 0
                val equipped = boss.id in profile.equippedDeckIds
                BossCardTile(
                    boss = boss,
                    level = lvl,
                    copies = copies,
                    isEquipped = equipped,
                    onClick = { inspectedBoss = boss },
                    modifier = Modifier.testTag("boss_card_${boss.id}")
                )
            }
        }
    }

    // Boss Detail, Upgrade & Slot Assignment Bottom Sheet
    inspectedBoss?.let { boss ->
        val lvl = profile.bossLevels[boss.id] ?: 0
        val unlocked = lvl > 0
        val copies = profile.bossCopies[boss.id] ?: 0
        val neededCopies = GameCatalog.copiesNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)
        val neededCoins = GameCatalog.coinsNeededForNextLevel(lvl.coerceAtLeast(1), boss.rarity)
        val canUpgrade = unlocked && copies >= neededCopies && profile.ecoCoins >= neededCoins && lvl < 10

        ModalBottomSheet(
            onDismissRequest = { inspectedBoss = null },
            containerColor = DarkSurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(boss.bodyColor.copy(alpha = 0.22f))
                            .border(2.5.dp, boss.rarity.frameColor, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = boss, modifier = Modifier.size(72.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RarityChip(boss.rarity)
                            Text("Arena ${boss.arenaId}", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
                        }
                        Text(boss.name, style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
                        Text(boss.title, style = MaterialTheme.typography.bodyMedium, color = TextMutedOnDark)
                        Text(
                            text = if (unlocked) "Level $lvl  •  $copies/$neededCopies copies" else "Locked — Win in Arena ${boss.arenaId}+ to capture",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (unlocked) EcoEmeraldBright else BossHpRedTop
                        )
                    }
                }

                // Active Battle Ability Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.5.dp, DarkBorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ACTIVE SKILL: ${boss.abilityName}", style = MaterialTheme.typography.titleSmall, color = SaffronGold)
                        Text(boss.abilityDescription, style = MaterialTheme.typography.bodyMedium, color = TextOnDark)
                        Text(
                            text = "Nature: ${boss.preferredWaste.shortLabel}  •  Power: ${boss.abilityDamageAtLevel(lvl.coerceAtLeast(1))} DMG  •  +${boss.tileBonusPercentAtLevel(lvl.coerceAtLeast(1))}% on ${boss.preferredWaste.displayName}s",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EcoEmeraldBright
                        )
                        Text(
                            text = "Tip: Using a ${boss.preferredWaste.shortLabel} skill resets any other ${boss.preferredWaste.shortLabel} skill in your deck.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMutedOnDark
                        )
                    }
                }

                Text(boss.lore, style = MaterialTheme.typography.bodyMedium, color = TextMutedOnDark)

                // Upgrade CTA
                if (unlocked) {
                    GlossyButton(
                        text = if (lvl >= 10) "MAX LEVEL REACHED" else "Upgrade to Lv.${lvl + 1} ($neededCoins Coins)",
                        onClick = { onUpgradeBoss(boss.id) },
                        icon = Icons.Default.ArrowUpward,
                        enabled = canUpgrade,
                        modifier = Modifier.fillMaxWidth(),
                        tag = "upgrade_boss_button"
                    )

                    // Equip into Slot 1 / 2 / 3
                    Text("Equip into Eco-Deck Slot:", style = MaterialTheme.typography.labelMedium, color = TextOnDark)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (slot in 0..2) {
                            val isHere = profile.equippedDeckIds.getOrNull(slot) == boss.id
                            OutlinedButton(
                                onClick = {
                                    onEquipBoss(slot, boss.id)
                                    inspectedBoss = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("equip_slot_$slot"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(2.dp, if (isHere) EcoEmeraldBright else CeramicBlueTop)
                            ) {
                                Text(
                                    text = if (isHere) "In Slot ${slot + 1}" else "Slot ${slot + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isHere) EcoEmeraldBright else CeramicBlueTop
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
