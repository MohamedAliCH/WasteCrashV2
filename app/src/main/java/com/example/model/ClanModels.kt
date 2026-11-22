package com.example.model

data class ClanMember(
    val name: String,
    val role: String,
    val trophies: Int,
    val friendCode: String,
    val leadBossId: String,
    val isCurrentUser: Boolean = false
)

data class ClanMessage(
    val id: String,
    val senderName: String,
    val senderRole: String,
    val text: String,
    val timestampLabel: String,
    val isMe: Boolean = false
)

data class EcoClan(
    val id: String,
    val name: String,
    val tag: String,
    val region: String,
    val motto: String,
    val badgeColorHex: Long,
    val minTrophies: Int,
    val weeklyCleanPoints: Int,
    val weeklyGoalPoints: Int,
    val members: List<ClanMember>,
    val chatMessages: List<ClanMessage>
) {
    val totalTrophies: Int
        get() = members.sumOf { it.trophies }
}

object ClanCatalog {
    val defaultClans: List<EcoClan> = listOf(
        EcoClan(
            id = "clan_carthage_knights",
            name = "Carthage Eco-Knights",
            tag = "CAR",
            region = "Tunis & Carthage",
            motto = "Restoring ancient shores and climbing Trophy Road together.",
            badgeColorHex = 0xFFFFB800,
            minTrophies = 0,
            weeklyCleanPoints = 4280,
            weeklyGoalPoints = 5000,
            members = listOf(
                ClanMember("Amine_TN", "Leader", 1120, "TN7A2K", "boss_m5"),
                ClanMember("Yasmine_Eco", "Co-Leader", 840, "TN9M4R", "boss_b5"),
                ClanMember("Sami_Carthage", "Elder", 610, "TN3C8P", "boss_m2"),
                ClanMember("Ines_Hammamet", "Member", 390, "TN5D1V", "boss_b4")
            ),
            chatMessages = listOf(
                ClanMessage("m1", "Amine_TN", "Leader", "Welcome Guardians! 720 pts left for our Gold Weekly Eco-Chest!", "10m ago"),
                ClanMessage("m2", "Yasmine_Eco", "Co-Leader", "Blue Plastic combos charge Plasti-Crab super fast on Hammamet.", "4m ago")
            )
        ),
        EcoClan(
            id = "clan_sidi_bou_squad",
            name = "Sidi Bou Clean Squad",
            tag = "SBS",
            region = "Gulf of Tunis",
            motto = "Cobalt cliffs, spotless beaches, rapid Match-3 cascades.",
            badgeColorHex = 0xFF3CB4FF,
            minTrophies = 0,
            weeklyCleanPoints = 3640,
            weeklyGoalPoints = 5000,
            members = listOf(
                ClanMember("Nour_Djerba", "Leader", 940, "TN8K6Z", "boss_b5"),
                ClanMember("Karim_Tozeur", "Elder", 520, "TN4B9Q", "boss_m4"),
                ClanMember("Salma_Bizerte", "Member", 310, "TN6H2W", "boss_b2")
            ),
            chatMessages = listOf(
                ClanMessage("m3", "Nour_Djerba", "Leader", "Who is pushing Arena 1 Monuments tonight?", "18m ago")
            )
        ),
        EcoClan(
            id = "clan_sahara_solar",
            name = "Sahara Solar Guardians",
            tag = "SAH",
            region = "Tozeur & Douz",
            motto = "Protecting Tunisia's desert oases and Chott El Djerid.",
            badgeColorHex = 0xFFF57C00,
            minTrophies = 300,
            weeklyCleanPoints = 4810,
            weeklyGoalPoints = 5000,
            members = listOf(
                ClanMember("Mehdi_Sfax", "Leader", 1340, "TN2X7M", "boss_d5"),
                ClanMember("Rania_Tozeur", "Co-Leader", 980, "TN1P5L", "boss_d3"),
                ClanMember("Aziz_Douz", "Elder", 760, "TN9T3J", "boss_d1")
            ),
            chatMessages = listOf(
                ClanMessage("m4", "Mehdi_Sfax", "Leader", "Almost at 5,000 Eco-Chest points — keep purifying!", "25m ago")
            )
        ),
        EcoClan(
            id = "clan_cap_bon_wave",
            name = "Cap Bon Wave Cleaners",
            tag = "CAP",
            region = "Hammamet & Kelibia",
            motto = "Coastal speed-racers protecting every sandy cove.",
            badgeColorHex = 0xFF18B85B,
            minTrophies = 0,
            weeklyCleanPoints = 2590,
            weeklyGoalPoints = 5000,
            members = listOf(
                ClanMember("Lina_Kelibia", "Leader", 670, "TN4W8C", "boss_b3"),
                ClanMember("Omar_Nabeul", "Member", 280, "TN7R2N", "boss_b1")
            ),
            chatMessages = listOf(
                ClanMessage("m5", "Lina_Kelibia", "Leader", "Share your Friend Codes in chat so we can compare decks!", "1h ago")
            )
        ),
        EcoClan(
            id = "clan_djerba_coral",
            name = "Djerba Coral Defenders",
            tag = "DJE",
            region = "Djerba & Mahdia",
            motto = "Zero marine plastics. Pure Mediterranean blue.",
            badgeColorHex = 0xFF9333EA,
            minTrophies = 0,
            weeklyCleanPoints = 3120,
            weeklyGoalPoints = 5000,
            members = listOf(
                ClanMember("Skander_Mahdia", "Leader", 790, "TN3M9F", "boss_b4"),
                ClanMember("Mariem_Djerba", "Elder", 460, "TN8Y1S", "boss_b3")
            ),
            chatMessages = listOf(
                ClanMessage("m6", "Skander_Mahdia", "Leader", "Drum-Root 3x3 Eco-Bomb destroys Mini-Boss waves!", "35m ago")
            )
        )
    )
}

object SocialSeedCatalog {
    val communityProfiles: List<PublicProfile> = listOf(
        PublicProfile(
            id = "seed_amine",
            userId = "seed_amine",
            displayName = "Amine_TN",
            friendCode = "TN7A2K",
            trophies = 1120,
            arenaId = 2,
            wins = 38,
            equippedDeckIds = listOf("boss_m5", "boss_d5", "boss_b4")
        ),
        PublicProfile(
            id = "seed_yasmine",
            userId = "seed_yasmine",
            displayName = "Yasmine_Eco",
            friendCode = "TN9M4R",
            trophies = 840,
            arenaId = 1,
            wins = 27,
            equippedDeckIds = listOf("boss_b5", "boss_m2", "boss_b3")
        ),
        PublicProfile(
            id = "seed_sami",
            userId = "seed_sami",
            displayName = "Sami_Carthage",
            friendCode = "TN3C8P",
            trophies = 610,
            arenaId = 1,
            wins = 19,
            equippedDeckIds = listOf("boss_m2", "boss_m1", "boss_b4")
        ),
        PublicProfile(
            id = "seed_ines",
            userId = "seed_ines",
            displayName = "Ines_Hammamet",
            friendCode = "TN5D1V",
            trophies = 390,
            arenaId = 0,
            wins = 14,
            equippedDeckIds = listOf("boss_b4", "boss_b2", "boss_b1")
        ),
        PublicProfile(
            id = "seed_nour",
            userId = "seed_nour",
            displayName = "Nour_Djerba",
            friendCode = "TN8K6Z",
            trophies = 940,
            arenaId = 2,
            wins = 31,
            equippedDeckIds = listOf("boss_b5", "boss_d3", "boss_m4")
        )
    )
}
