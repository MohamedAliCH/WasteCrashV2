package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.BossMonsterAvatar
import com.example.ui.components.GlossyButton
import com.example.ui.theme.*

@Composable
fun SocialScreen(
    profile: UserProfile,
    publicProfilesState: UiState<List<PublicProfile>>,
    clans: List<EcoClan>,
    joinedClanId: String?,
    onAddFriend: (String) -> Unit,
    onRemoveFriend: (String, String) -> Unit,
    onJoinClan: (String) -> Unit,
    onLeaveClan: () -> Unit,
    onCreateClan: (name: String, tag: String, region: String, motto: String) -> Unit,
    onSendClanMessage: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Friends, 1 = Clan

    val allPublicProfiles = (publicProfilesState as? UiState.Success)?.data ?: emptyList()
    val friendProfiles = remember(allPublicProfiles, profile.friendUids) {
        allPublicProfiles.filter { it.userId in profile.friendUids }
    }
    val discoverProfiles = remember(allPublicProfiles, profile.friendUids, profile.userId) {
        allPublicProfiles.filter { it.userId != profile.userId && it.userId !in profile.friendUids }
    }
    val activeClan = remember(clans, joinedClanId) {
        clans.firstOrNull { it.id == joinedClanId }
    }

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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Social Hub",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextOnDark
                )
                Text(
                    text = "Connect with Tunisian Eco-Guardians & join an Eco-Clan",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMutedOnDark
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (activeClan != null) Color(activeClan.badgeColorHex).copy(alpha = 0.22f) else DarkSurfaceCard,
                border = BorderStroke(
                    1.5.dp,
                    if (activeClan != null) Color(activeClan.badgeColorHex) else DarkBorderSubtle
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (activeClan != null) Icons.Default.Shield else Icons.Default.Groups,
                        contentDescription = null,
                        tint = if (activeClan != null) Color(activeClan.badgeColorHex) else CeramicBlueTop,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (activeClan != null) "[${activeClan.tag}]" else "${friendProfiles.size} Friends",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextOnDark
                    )
                }
            }
        }

        // Segmented Switcher: Friends vs Clan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceCard)
                .border(1.8.dp, DarkBorderSubtle, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SocialTabPill(
                text = "Friends (${friendProfiles.size})",
                icon = Icons.Default.Group,
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier
                    .weight(1f)
                    .testTag("social_tab_friends")
            )
            SocialTabPill(
                text = if (activeClan != null) "Clan [${activeClan.tag}]" else "Eco-Clans",
                icon = Icons.Default.Shield,
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier
                    .weight(1f)
                    .testTag("social_tab_clan")
            )
        }

        if (selectedTab == 0) {
            FriendsTabContent(
                profile = profile,
                friendProfiles = friendProfiles,
                discoverProfiles = discoverProfiles,
                onAddFriend = onAddFriend,
                onRemoveFriend = onRemoveFriend,
                modifier = Modifier.weight(1f)
            )
        } else {
            ClanTabContent(
                profile = profile,
                clans = clans,
                activeClan = activeClan,
                onJoinClan = onJoinClan,
                onLeaveClan = onLeaveClan,
                onCreateClan = onCreateClan,
                onSendClanMessage = onSendClanMessage,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FriendsTabContent(
    profile: UserProfile,
    friendProfiles: List<PublicProfile>,
    discoverProfiles: List<PublicProfile>,
    onAddFriend: (String) -> Unit,
    onRemoveFriend: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var friendCodeInput by remember { mutableStateOf("") }
    var copiedNotice by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        // Your Friend Code + Add Friend Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = DarkSurfaceCard,
                border = BorderStroke(1.8.dp, SaffronGold.copy(alpha = 0.7f)),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(CeramicBlueTop, CeramicBlueBottom)))
                                    .border(2.dp, SaffronGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.displayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Your Eco-Friend Code", style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
                                Text(
                                    text = profile.friendCode,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = SaffronGold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(profile.friendCode))
                                copiedNotice = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, CeramicBlueTop)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = CeramicBlueTop, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (copiedNotice) "Copied!" else "Copy",
                                color = TextOnDark,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = friendCodeInput,
                            onValueChange = { friendCodeInput = it.uppercase().take(8) },
                            placeholder = {
                                Text("Friend Code (e.g. TN7A2K)", color = TextMutedOnDark)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextOnDark,
                                unfocusedTextColor = TextOnDark,
                                focusedBorderColor = SaffronGold,
                                unfocusedBorderColor = DarkBorderSubtle,
                                focusedContainerColor = DarkBgPrimary,
                                unfocusedContainerColor = DarkBgPrimary,
                                cursorColor = SaffronGold
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("friend_code_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                        GlossyButton(
                            text = "Add",
                            onClick = {
                                if (friendCodeInput.isNotBlank()) {
                                    onAddFriend(friendCodeInput)
                                    friendCodeInput = ""
                                }
                            },
                            height = 52.dp,
                            modifier = Modifier.width(86.dp),
                            tag = "add_friend_button"
                        )
                    }
                }
            }
        }

        // My Friends Section
        item {
            Text(
                text = "Your Friends (${friendProfiles.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = TextOnDark
            )
        }

        if (friendProfiles.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.5.dp, DarkBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = CeramicBlueTop, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "No friends added yet. Enter a code above or tap + Add on any Tunisian player below!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedOnDark
                        )
                    }
                }
            }
        } else {
            items(friendProfiles, key = { "friend_${it.userId}" }) { friend ->
                PublicPlayerCard(
                    player = friend,
                    isFriend = true,
                    onAction = { onRemoveFriend(friend.userId, friend.displayName) }
                )
            }
        }

        // Discover Tunisian Players
        if (discoverProfiles.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Discover Tunisian Guardians",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextOnDark
                )
            }
            items(discoverProfiles, key = { "discover_${it.userId}" }) { player ->
                PublicPlayerCard(
                    player = player,
                    isFriend = false,
                    onAction = { onAddFriend(player.friendCode) }
                )
            }
        }
    }
}

@Composable
private fun PublicPlayerCard(
    player: PublicProfile,
    isFriend: Boolean,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.5.dp, if (isFriend) EcoEmeraldBright.copy(alpha = 0.7f) else DarkBorderSubtle),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val leadBoss = GameCatalog.bossById(player.equippedDeckIds.firstOrNull() ?: "boss_b1")
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(leadBoss.bodyColor.copy(alpha = 0.22f))
                        .border(2.dp, leadBoss.rarity.frameColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    BossMonsterAvatar(boss = leadBoss, animateIdle = false, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${player.trophies} 🏆 • ${player.wins}W • ${player.friendCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedOnDark
                    )
                }
            }

            if (isFriend) {
                IconButton(
                    onClick = onAction,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CarthageCoral.copy(alpha = 0.16f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove Friend", tint = BossHpRedTop, modifier = Modifier.size(18.dp))
                }
            } else {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onAction),
                    shape = RoundedCornerShape(50),
                    color = CeramicBlueBottom,
                    border = BorderStroke(1.dp, CeramicBlueTop)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClanTabContent(
    profile: UserProfile,
    clans: List<EcoClan>,
    activeClan: EcoClan?,
    onJoinClan: (String) -> Unit,
    onLeaveClan: () -> Unit,
    onCreateClan: (name: String, tag: String, region: String, motto: String) -> Unit,
    onSendClanMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (activeClan != null) {
        ActiveClanView(
            profile = profile,
            clan = activeClan,
            onLeaveClan = onLeaveClan,
            onSendClanMessage = onSendClanMessage,
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(2.dp, SaffronGold.copy(alpha = 0.75f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF173061), DarkSurfaceCard))
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start Your Own Eco-Clan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = GlowBeamGold
                            )
                            Text(
                                text = "Lead friends across Tunisia & unlock Weekly Eco-Chests",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedOnDark
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        GlossyButton(
                            text = "Create",
                            onClick = { showCreateDialog = true },
                            icon = Icons.Default.Add,
                            height = 46.dp,
                            modifier = Modifier.width(116.dp),
                            tag = "create_clan_button"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Featured Tunisian Eco-Clans",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextOnDark
                )
            }

            items(clans, key = { it.id }) { clan ->
                val canJoin = profile.trophies >= clan.minTrophies
                val badgeColor = Color(clan.badgeColorHex)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clan_card_${clan.id}"),
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.8.dp, DarkBorderSubtle),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(badgeColor.copy(alpha = 0.22f))
                                        .border(2.dp, badgeColor, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = badgeColor, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = clan.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = TextOnDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.25f)) {
                                            Text(
                                                text = "[${clan.tag}]",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = badgeColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${clan.region} • ${clan.members.size} Members • ${clan.totalTrophies} 🏆",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMutedOnDark
                                    )
                                }
                            }
                        }

                        Text(
                            text = clan.motto,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextOnDark
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (clan.minTrophies > 0) "Min: ${clan.minTrophies} 🏆" else "Open to All Guardians",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (canJoin) EcoEmeraldBright else SaffronGold
                            )
                            GlossyButton(
                                text = if (canJoin) "JOIN" else "LOCKED",
                                onClick = { onJoinClan(clan.id) },
                                enabled = canJoin,
                                height = 42.dp,
                                modifier = Modifier.width(110.dp),
                                tag = "join_clan_${clan.id}"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateClanDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, tag, region, motto ->
                showCreateDialog = false
                onCreateClan(name, tag, region, motto)
            }
        )
    }
}

@Composable
private fun ActiveClanView(
    profile: UserProfile,
    clan: EcoClan,
    onLeaveClan: () -> Unit,
    onSendClanMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var innerTab by remember { mutableIntStateOf(0) } // 0 = Roster, 1 = Chat
    var chatInput by remember { mutableStateOf("") }
    val badgeColor = Color(clan.badgeColorHex)
    val chestFrac = (clan.weeklyCleanPoints.toFloat() / clan.weeklyGoalPoints.coerceAtLeast(1).toFloat()).coerceIn(0.04f, 1f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Clan Banner Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = DarkSurfaceCard,
            border = BorderStroke(2.dp, badgeColor),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.verticalGradient(listOf(badgeColor.copy(alpha = 0.22f), DarkSurfaceCard)))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(badgeColor.copy(alpha = 0.25f))
                                .border(2.dp, badgeColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = badgeColor, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${clan.name} [${clan.tag}]",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextOnDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${clan.region} • ${clan.members.size} Members • ${clan.totalTrophies} 🏆",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedOnDark
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLeaveClan,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, CarthageCoral)
                    ) {
                        Text("Leave", color = BossHpRedTop, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(text = clan.motto, style = MaterialTheme.typography.bodyMedium, color = TextOnDark)

                // Weekly Eco-Chest Progress
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Weekly Eco-Chest", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SaffronGold)
                        Text("${clan.weeklyCleanPoints} / ${clan.weeklyGoalPoints} pts", style = MaterialTheme.typography.labelSmall, color = TextOnDark)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF061022))
                            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(chestFrac)
                                .background(Brush.horizontalGradient(listOf(SaffronGold, LimePlayTop)))
                        )
                    }
                }
            }
        }

        // Inner Sub-Tabs: Members Roster vs Clan Lounge Chat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceCard)
                .border(1.5.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SocialTabPill(
                text = "Members (${clan.members.size})",
                icon = Icons.Default.Groups,
                selected = innerTab == 0,
                onClick = { innerTab = 0 },
                modifier = Modifier.weight(1f)
            )
            SocialTabPill(
                text = "Clan Chat (${clan.chatMessages.size})",
                icon = Icons.Default.ChatBubble,
                selected = innerTab == 1,
                onClick = { innerTab = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        if (innerTab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(clan.members, key = { "${it.name}_${it.friendCode}" }) { member ->
                    val boss = GameCatalog.bossById(member.leadBossId)
                    val isMe = member.isCurrentUser || member.friendCode == profile.friendCode
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isMe) DarkSurfaceElevated else DarkSurfaceCard,
                        border = BorderStroke(1.5.dp, if (isMe) SaffronGold else DarkBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                BossMonsterAvatar(boss = boss, animateIdle = false, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isMe) "${member.name} (You)" else member.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextOnDark
                                    )
                                    Text(
                                        text = "${member.role} • ${member.friendCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (member.role == "Leader") SaffronGold else TextMutedOnDark
                                    )
                                }
                            }
                            Text(
                                text = "${member.trophies} 🏆",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = SaffronGold
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(clan.chatMessages, key = { it.id }) { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.isMe) Color(0xFF153466) else DarkSurfaceCard,
                        border = BorderStroke(1.2.dp, if (msg.isMe) CeramicBlueTop else DarkBorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${msg.senderName} • ${msg.senderRole}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (msg.isMe) CeramicBlueTop else SaffronGold
                                )
                                Text(
                                    text = msg.timestampLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedOnDark
                                )
                            }
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextOnDark
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it.take(180) },
                    placeholder = { Text("Message your clan...", color = TextMutedOnDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextOnDark,
                        unfocusedTextColor = TextOnDark,
                        focusedBorderColor = CeramicBlueTop,
                        unfocusedBorderColor = DarkBorderSubtle,
                        focusedContainerColor = DarkSurfaceCard,
                        unfocusedContainerColor = DarkSurfaceCard,
                        cursorColor = SaffronGold
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                )
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            onSendClanMessage(chatInput)
                            chatInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EcoEmerald)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CreateClanDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, tag: String, region: String, motto: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Tunis") }
    var motto by remember { mutableStateOf("") }

    val regions = listOf("Tunis", "Hammamet", "Monastir", "Tozeur", "Djerba")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceCard,
        title = {
            Text("Create Eco-Clan", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = TextOnDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(28) },
                    label = { Text("Clan Name", color = TextMutedOnDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextOnDark,
                        unfocusedTextColor = TextOnDark,
                        focusedBorderColor = SaffronGold,
                        unfocusedBorderColor = DarkBorderSubtle
                    )
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(4) },
                    label = { Text("Tag (2-4 chars, e.g. TUN)", color = TextMutedOnDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextOnDark,
                        unfocusedTextColor = TextOnDark,
                        focusedBorderColor = SaffronGold,
                        unfocusedBorderColor = DarkBorderSubtle
                    )
                )
                Text("Home Region:", style = MaterialTheme.typography.labelMedium, color = TextMutedOnDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    regions.take(3).forEach { r ->
                        FilterChip(
                            selected = region == r,
                            onClick = { region = r },
                            label = { Text(r, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                labelColor = TextMutedOnDark,
                                selectedContainerColor = SaffronGold,
                                selectedLabelColor = DeepNavyInk
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = motto,
                    onValueChange = { motto = it.take(80) },
                    label = { Text("Clan Motto", color = TextMutedOnDark) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextOnDark,
                        unfocusedTextColor = TextOnDark,
                        focusedBorderColor = SaffronGold,
                        unfocusedBorderColor = DarkBorderSubtle
                    )
                )
            }
        },
        confirmButton = {
            GlossyButton(
                text = "Create Clan",
                onClick = { onCreate(name, tag, region, motto) },
                height = 44.dp,
                modifier = Modifier.width(136.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMutedOnDark)
            }
        }
    )
}

@Composable
private fun SocialTabPill(
    text: String,
    icon: ImageVector,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) DeepNavyInk else TextMutedOnDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) DeepNavyInk else TextMutedOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
