package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.QuestClaimOutcome
import com.example.model.*
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.components.RarityChip
import com.example.ui.components.StatPill
import com.example.ui.theme.*

@Composable
fun QuestsScreen(
    profile: UserProfile,
    claimedOutcome: QuestClaimOutcome?,
    onClaimQuest: (QuestDefinition) -> Unit,
    onDismissClaimOutcome: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var selectedCategory by remember { mutableStateOf(QuestCategory.DAILY) }

    val quests = remember(selectedCategory) {
        GameCatalog.quests.filter { it.category == selectedCategory }
    }

    val dailyReady = GameCatalog.quests.count { q ->
        q.category == QuestCategory.DAILY &&
            (profile.questProgress[q.id] ?: 0) >= q.target &&
            profile.questClaimed[q.id] != true
    }
    val challengeReady = GameCatalog.quests.count { q ->
        q.category == QuestCategory.CHALLENGE &&
            (profile.questProgress[q.id] ?: 0) >= q.target &&
            profile.questClaimed[q.id] != true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C1E42), DarkBgPrimary, Color(0xFF050D1D))
                )
            )
            .then(if (onBack != null) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Eco-Coins Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceCard)
                            .border(1.5.dp, DarkBorderSubtle, CircleShape)
                            .testTag("quests_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextOnDark
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text("Quests & Challenges", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
                    Text("Earn Eco-Coins & Boss Cards every battle", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
                }
            }
            StatPill(
                value = "${profile.ecoCoins}",
                icon = Icons.Default.MonetizationOn,
                badgeColor = EcoEmeraldBright
            )
        }

        // Daily Reset Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceCard,
            border = BorderStroke(1.8.dp, SaffronGold.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(Color(0xFF173268), DarkSurfaceCard)))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Active Cycle: ${profile.dailyQuestSeedDate}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextOnDark)
                        Text("Progress updates automatically after every 1v1 match!", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
                    }
                }
            }
        }

        // Category Switcher: Daily Quests vs Elite Challenges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceCard)
                .border(1.8.dp, DarkBorderSubtle, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuestTabChip(
                label = if (dailyReady > 0) "Daily Quests ($dailyReady)" else "Daily Quests",
                selected = selectedCategory == QuestCategory.DAILY,
                onClick = { selectedCategory = QuestCategory.DAILY },
                modifier = Modifier.weight(1f).testTag("quests_tab_daily")
            )
            QuestTabChip(
                label = if (challengeReady > 0) "Elite Challenges ($challengeReady)" else "Elite Challenges",
                selected = selectedCategory == QuestCategory.CHALLENGE,
                onClick = { selectedCategory = QuestCategory.CHALLENGE },
                modifier = Modifier.weight(1f).testTag("quests_tab_challenges")
            )
        }

        // Quests List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(quests, key = { it.id }) { quest ->
                val progress = (profile.questProgress[quest.id] ?: 0).coerceAtMost(quest.target)
                val claimed = profile.questClaimed[quest.id] == true
                val completed = progress >= quest.target

                QuestCardItem(
                    quest = quest,
                    progress = progress,
                    completed = completed,
                    claimed = claimed,
                    onClaim = { onClaimQuest(quest) }
                )
            }
        }
    }

    // Celebratory Quest Reward Dialog (Eco-Coins + Boss Cards!)
    if (claimedOutcome != null) {
        val boss = GameCatalog.bossById(claimedOutcome.rewardedBossId)
        AlertDialog(
            onDismissRequest = onDismissClaimOutcome,
            containerColor = DarkSurfaceCard,
            title = {
                Text("Quest Reward Claimed!", style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(boss.bodyColor.copy(alpha = 0.22f))
                            .border(3.dp, boss.rarity.frameColor, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BossMonsterAvatar(boss = boss, modifier = Modifier.size(80.dp))
                    }
                    RarityChip(boss.rarity)
                    Text(
                        text = "+${claimedOutcome.cardsAwarded}x ${boss.name} Cards",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextOnDark
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = EcoEmerald.copy(alpha = 0.22f),
                        border = BorderStroke(1.5.dp, EcoEmeraldBright)
                    ) {
                        Text(
                            text = "+${claimedOutcome.coinsAwarded} Eco-Coins Added!",
                            style = MaterialTheme.typography.titleSmall,
                            color = EcoEmeraldBright,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                GlossyButton(
                    text = "Awesome!",
                    onClick = onDismissClaimOutcome,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

@Composable
private fun QuestTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SaffronGold else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) DeepNavyInk else TextMutedOnDark
            )
        }
    }
}

@Composable
private fun QuestCardItem(
    quest: QuestDefinition,
    progress: Int,
    completed: Boolean,
    claimed: Boolean,
    onClaim: () -> Unit
) {
    val frac = (progress.toFloat() / quest.target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quest_card_${quest.id}"),
        shape = RoundedCornerShape(20.dp),
        color = if (claimed) Color(0xFF0A162D) else DarkSurfaceCard,
        border = BorderStroke(
            width = if (completed && !claimed) 2.5.dp else 1.5.dp,
            color = if (completed && !claimed) EcoEmeraldBright else DarkBorderSubtle
        ),
        shadowElevation = if (claimed) 1.dp else 6.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(quest.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
                        RarityChip(quest.rewardRarity)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(quest.description, style = MaterialTheme.typography.bodyMedium, color = TextMutedOnDark)
                }
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF061022))
                    .border(1.2.dp, DarkBorderSubtle, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .background(
                            Brush.horizontalGradient(
                                if (completed) listOf(EcoEmeraldBright, EcoEmerald) else listOf(CeramicBlueTop, SidiCobalt)
                            )
                        )
                )
                Text(
                    text = "$progress / ${quest.target}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Rewards Strip + Claim Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EcoEmerald.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, EcoEmeraldBright.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = EcoEmeraldBright, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+${quest.coinReward}", style = MaterialTheme.typography.labelMedium, color = EcoEmeraldBright)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SaffronGold.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, SaffronGold.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = SaffronGold, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+${quest.cardRewardCount} ${quest.rewardRarity.label} Card", style = MaterialTheme.typography.labelMedium, color = GlowBeamGold)
                        }
                    }
                }

                when {
                    claimed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Claimed", tint = EcoEmeraldBright, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Claimed", style = MaterialTheme.typography.labelMedium, color = EcoEmeraldBright)
                        }
                    }
                    completed -> {
                        GlossyButton(
                            text = "CLAIM",
                            onClick = onClaim,
                            height = 44.dp,
                            modifier = Modifier.width(106.dp),
                            tag = "claim_quest_${quest.id}"
                        )
                    }
                    else -> {
                        Text("In Progress", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
                    }
                }
            }
        }
    }
}
