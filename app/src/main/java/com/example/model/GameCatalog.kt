package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.theme.*

enum class WasteType(
    val displayName: String,
    val shortLabel: String,
    val primaryColor: Color,
    val darkColor: Color,
    @DrawableRes val spriteRes: Int,
    val ecoTip: String
) {
    PLASTIC_BLUE("Plastic Bottle", "Plastic", TilePlasticBlue, TilePlasticBlueDark, R.drawable.sprite_tile_plastic, "Rinse & crush plastic bottles to save coastal wildlife."),
    GLASS_GREEN("Glass Bottle", "Glass", TileGlassGreen, TileGlassGreenDark, R.drawable.sprite_tile_glass, "Glass can be recycled endlessly without losing purity."),
    ALUMINUM_YELLOW("Aluminum Can", "Can", TileAluminumYellow, TileAluminumYellowDark, R.drawable.sprite_tile_can, "Recycling 1 can saves enough energy to run a TV for 3 hours."),
    ORGANIC_RED("Organic Core", "Organic", TileOrganicRed, TileOrganicRedDark, R.drawable.sprite_tile_apple, "Composting food scraps enriches Tunisian olive & palm groves."),
    CARDBOARD_TAN("Cardboard Box", "Box", TileCardboardTan, TileCardboardTanDark, R.drawable.sprite_tile_box, "Flatten clean cardboard boxes to reduce landfill volume.")
}

enum class BossRarity(
    val label: String,
    val frameColor: Color,
    val accentColor: Color,
    val basePowerMultiplier: Float
) {
    COMMON("Common", RarityCommon, Color(0xFF94AFCB), 1.0f),
    RARE("Rare", RarityRare, Color(0xFFFCD34D), 1.18f),
    EPIC("Epic", RarityEpic, Color(0xFFC084FC), 1.38f),
    LEGENDARY("Legendary", RarityLegendary, RarityLegendaryGold, 1.62f)
}

enum class BossAbilityType {
    ROW_SWEEP,
    COL_SWEEP,
    ECO_BOMB,
    COLOR_PURGE,
    MEGA_STRIKE
}

data class TunisianPlace(
    val id: String,
    val name: String,
    val arenaId: Int,
    val regionTag: String,
    val ecoFact: String,
    /** Position on the map, normalised with TunisiaGeo.project(lon, lat). */
    val mapX: Float = 0.5f,
    val mapY: Float = 0.5f,
    val guardianBossId: String = "boss_b1",
    val heritageHighlight: String = "",
    /** Real (cleaned) photo, revealed once the place is purified. */
    @DrawableRes val imageRes: Int = R.drawable.scene_hammamet_clean,
    /** Cartoon version shown while the place is still polluted (null = use the photo, desaturated). */
    @DrawableRes val toonRes: Int? = null,
    /** Painted litter overlay that fades out as the place gets cleaner (null = procedural litter). */
    @DrawableRes val dirtyRes: Int? = null,
    val arabicName: String = ""
)

/** Small trash monster fought in waves 1 and 2 before the shared Boss. Takes x2 damage from its own material. */
data class MiniMobDefinition(
    val id: String,
    val name: String,
    val weakness: WasteType,
    @DrawableRes val spriteRes: Int,
    /** Relative toughness (1.0 = average mini). */
    val hpWeight: Float = 1f
)

data class ArenaDefinition(
    val id: Int,
    val name: String,
    val subtitle: String,
    val minTrophies: Int,
    val nextArenaTrophies: Int?,
    @DrawableRes val bannerRes: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val places: List<TunisianPlace>
)

data class BossDefinition(
    val id: String,
    val name: String,
    val title: String,
    val arenaId: Int,
    val rarity: BossRarity,
    @DrawableRes val spriteRes: Int,
    val bodyColor: Color,
    val accentColor: Color,
    val preferredWaste: WasteType,
    val abilityType: BossAbilityType,
    val abilityName: String,
    val abilityDescription: String,
    val baseAbilityDamage: Int,
    val chargeRequired: Int,
    val bossBattleHp: Int,
    val lore: String
) {
    fun abilityDamageAtLevel(level: Int): Int {
        val lvl = level.coerceAtLeast(1)
        return (baseAbilityDamage * (1f + (lvl - 1) * 0.16f)).toInt()
    }

    fun tileBonusPercentAtLevel(level: Int): Int {
        val lvl = level.coerceAtLeast(1)
        return 10 + (lvl - 1) * 4
    }
}

enum class QuestCategory {
    DAILY,
    CHALLENGE
}

enum class QuestMetric {
    SINGLE_MATCH_BLUE_TILES,
    WIN_WITH_ONLY_EPIC_BOSSES,
    CLEAR_WAVES,
    USE_BOSS_ABILITIES,
    MATCH_TOTAL_TILES,
    PURIFY_BEACHES,
    HIGH_COMBO_CHAIN
}

data class QuestDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: QuestCategory,
    val metric: QuestMetric,
    val target: Int,
    val coinReward: Int,
    val cardRewardCount: Int,
    val rewardRarity: BossRarity
)

object GameCatalog {

    // 3 environments x 5 real places (team spec "Tounes Nadhifa"): Coasts & lakes 0, Monuments 300, Desert & oases 600.
    val arenas: List<ArenaDefinition> = listOf(
        ArenaDefinition(
            id = 0,
            name = "Arena 0: Coasts & Lakes",
            subtitle = "Beaches, islands & wetlands",
            minTrophies = 0,
            nextArenaTrophies = 300,
            bannerRes = R.drawable.img_arena_beaches,
            primaryColor = SidiCobalt,
            secondaryColor = Color(0xFF00B4D8),
            places = listOf(
                TunisianPlace("place_hammamet", "Hammamet", 0, "Hammamet-Sud Beach", "Thousands of visitors come every summer: sorting waste keeps the sand clean.", 0.740f, 0.153f, "boss_b1", "Mediterranean Jasmine Coast", R.drawable.scene_hammamet_clean, R.drawable.scene_hammamet_toon, R.drawable.scene_hammamet_dirty, "الحمامات"),
                TunisianPlace("place_djerba", "Djerba", 0, "Sidi Mahrez Beach", "Djerba is an island: litter left on the beach quickly reaches the sea and the sea turtles.", 0.813f, 0.500f, "boss_b2", "Island of Dreams", R.drawable.scene_djerba_clean, arabicName = "جربة"),
                TunisianPlace("place_mahdia", "Mahdia", 0, "Cap Africa", "Mahdia lives from fishing: a lost net keeps trapping fish for years.", 0.840f, 0.273f, "boss_b3", "Turquoise Reef Peninsula", R.drawable.scene_mahdia_clean, arabicName = "المهدية"),
                TunisianPlace("place_tabarka", "Tabarka", 0, "The Needles of Tabarka", "Red coral grows off Tabarka: a plastic bottle takes centuries to disappear.", 0.332f, 0.080f, "boss_b4", "Coral Coast", R.drawable.scene_tabarka_clean, arabicName = "طبرقة"),
                TunisianPlace("place_ichkeul", "Ichkeul Lake", 0, "Ichkeul National Park", "A UNESCO World Heritage park where thousands of migrating birds spend the winter.", 0.532f, 0.052f, "boss_b5", "UNESCO Wetland", R.drawable.scene_ichkeul_clean, R.drawable.scene_ichkeul_toon, R.drawable.scene_ichkeul_dirty, "بحيرة إشكل")
            )
        ),
        ArenaDefinition(
            id = 1,
            name = "Arena 1: Monuments",
            subtitle = "Ancient heritage & medinas",
            minTrophies = 300,
            nextArenaTrophies = 600,
            bannerRes = R.drawable.img_arena_monuments,
            primaryColor = SaharanAmber,
            secondaryColor = Color(0xFFD97706),
            places = listOf(
                TunisianPlace("place_carthage", "Carthage", 1, "Antonine Baths", "A UNESCO site. A recycled can saves about 95% of the energy of a new one.", 0.677f, 0.093f, "boss_m1", "UNESCO Punic & Roman Ruins", R.drawable.scene_carthage_clean, R.drawable.scene_carthage_toon, R.drawable.scene_carthage_dirty, "قرطاج"),
                TunisianPlace("place_el_jem", "El Jem", 1, "El Jem Amphitheatre", "The amphitheatre held 35,000 spectators: imagine their litter without sorting bins!", 0.760f, 0.301f, "boss_m2", "UNESCO Roman Amphitheatre", R.drawable.scene_eljem_clean, arabicName = "الجم"),
                TunisianPlace("place_dougga", "Dougga", 1, "Capitol of Dougga", "The best-preserved Roman town in North Africa: keeping it clean keeps it alive.", 0.433f, 0.151f, "boss_m3", "UNESCO Hilltop Roman City", R.drawable.scene_dougga_clean, arabicName = "دقة"),
                TunisianPlace("place_medina", "Medina of Tunis", 1, "Souks of the Medina", "Cardboard from the souks recycles easily if it is sorted and kept dry.", 0.642f, 0.100f, "boss_m4", "UNESCO Historic Medina", R.drawable.scene_medina_clean, arabicName = "مدينة تونس"),
                TunisianPlace("place_sbeitla", "Sbeitla", 1, "Temples of Sufetula", "These temples are 1,800 years old. A plastic bag can last more than 400 years.", 0.413f, 0.309f, "boss_m5", "Roman Temples of Sufetula", R.drawable.scene_sbeitla_clean, arabicName = "سبيطلة")
            )
        ),
        ArenaDefinition(
            id = 2,
            name = "Arena 2: Desert & Oases",
            subtitle = "Saharan dunes & springs",
            minTrophies = 600,
            nextArenaTrophies = null,
            bannerRes = R.drawable.img_arena_desert,
            primaryColor = Color(0xFFEA580C),
            secondaryColor = EcoEmerald,
            places = listOf(
                TunisianPlace("place_tozeur", "Tozeur", 2, "Tozeur Palm Grove", "Composted organic waste feeds the palm grove soil instead of polluting it.", 0.194f, 0.484f, "boss_d1", "Thousand-Palm Djerid Oasis", R.drawable.scene_tozeur_clean, R.drawable.scene_tozeur_toon, R.drawable.scene_tozeur_dirty, "توزر"),
                TunisianPlace("place_douz", "Douz", 2, "Gateway to the Sahara", "Desert wind carries plastic bags very far: people call them \"desert flowers\".", 0.389f, 0.545f, "boss_d2", "Gateway to the Grand Erg", R.drawable.scene_douz_clean, arabicName = "دوز"),
                TunisianPlace("place_chott_el_djerid", "Chott el-Djerid", 2, "Great Salt Lake", "The largest salt lake of the Sahara: its salt crust is as fragile as the desert.", 0.259f, 0.507f, "boss_d3", "Mirrored Saharan Salt Lake", R.drawable.scene_chott_clean, arabicName = "شط الجريد"),
                TunisianPlace("place_matmata", "Matmata", 2, "Troglodyte Houses", "Houses dug into the earth: a village that has reused its soil for centuries.", 0.598f, 0.535f, "boss_d4", "Troglodyte Lunar Dwellings", R.drawable.scene_matmata_clean, arabicName = "مطماطة"),
                TunisianPlace("place_tataouine", "Tataouine", 2, "Ksar Ouled Soltane", "The ksour were granaries: nothing was wasted, every reserve was precious.", 0.703f, 0.616f, "boss_d5", "Ksar Granaries", R.drawable.scene_tataouine_clean, arabicName = "تطاوين")
            )
        )
    )

    val allPlaces: List<TunisianPlace> = arenas.flatMap { it.places }

    // Mini trash monsters per environment (team art). Waves: 2 minis, then 3 minis, then the shared Boss.
    val miniMobs: List<MiniMobDefinition> = listOf(
        MiniMobDefinition("mob_sachet", "Sachet volant", WasteType.PLASTIC_BLUE, R.drawable.enemy_sachet, 0.9f),
        MiniMobDefinition("mob_bouteille", "Bouteillon", WasteType.PLASTIC_BLUE, R.drawable.enemy_bouteille, 1.1f),
        MiniMobDefinition("mob_gobelet", "Gobelet", WasteType.CARDBOARD_TAN, R.drawable.enemy_gobelet, 1.0f),
        MiniMobDefinition("mob_canette", "Canette rouillée", WasteType.ALUMINUM_YELLOW, R.drawable.enemy_canette, 1.05f),
        MiniMobDefinition("mob_tesson", "Tesson", WasteType.GLASS_GREEN, R.drawable.enemy_tesson, 1.15f),
        MiniMobDefinition("mob_goutte", "Gouttelette", WasteType.ORGANIC_RED, R.drawable.enemy_goutte, 1.0f)
    )

    private val minisByArena: Map<Int, List<String>> = mapOf(
        0 to listOf("mob_sachet", "mob_bouteille", "mob_gobelet"),
        1 to listOf("mob_canette", "mob_gobelet", "mob_tesson"),
        2 to listOf("mob_goutte", "mob_sachet", "mob_canette")
    )

    fun miniMobById(id: String): MiniMobDefinition = miniMobs.firstOrNull { it.id == id } ?: miniMobs.first()

    fun minisForArena(arenaId: Int): List<MiniMobDefinition> =
        (minisByArena[arenaId] ?: minisByArena.getValue(0)).map { miniMobById(it) }

    /**
     * Deterministic 4 mini-mob waves for a match: both players (and spectators) derive the same
     * 2 + 3 + 3 + 4 mini trash monsters from the shared match id before facing the Wave 5 Shared Boss.
     */
    fun matchWaves(seedKey: String, arenaId: Int): List<List<String>> {
        val pool = minisForArena(arenaId).map { it.id }
        val rng = kotlin.random.Random(seedKey.hashCode() xor 0x5bd1e995)
        val pick = { pool[rng.nextInt(pool.size)] }
        return listOf(
            List(2) { pick() },
            List(3) { pick() },
            List(3) { pick() },
            List(4) { pick() }
        )
    }

    @DrawableRes val miniWave1Sprite: Int = R.drawable.enemy_sachet
    @DrawableRes val miniWave2Sprite: Int = R.drawable.enemy_bouteille

    // 15 Bosses (5 per arena, cumulative pool) with the team's illustrations. IDs are kept stable
    // so existing profiles (levels, copies, decks) stay valid.
    val bosses: List<BossDefinition> = listOf(
        // Arena 0: Coasts & Lakes
        BossDefinition(
            id = "boss_b1", name = "Plasticos", title = "Beach Bag Brute",
            arenaId = 0, rarity = BossRarity.COMMON, spriteRes = R.drawable.enemy_plasticos,
            bodyColor = Color(0xFF1E88E5), accentColor = Color(0xFF90CAF9),
            preferredWaste = WasteType.PLASTIC_BLUE, abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Tidal Row Sweep", abilityDescription = "Blasts an entire horizontal row and boosts Plastic matches.",
            baseAbilityDamage = 220, chargeRequired = 14, bossBattleHp = 1450,
            lore = "Born from the plastic bags abandoned on the beaches of Hammamet."
        ),
        BossDefinition(
            id = "boss_b2", name = "Cartonnier", title = "Soggy Box Heap",
            arenaId = 0, rarity = BossRarity.COMMON, spriteRes = R.drawable.enemy_cartonnier,
            bodyColor = Color(0xFFB45309), accentColor = Color(0xFFFDE68A),
            preferredWaste = WasteType.CARDBOARD_TAN, abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Cardboard Recycler", abilityDescription = "Recycles every Cardboard tile on the board.",
            baseAbilityDamage = 240, chargeRequired = 15, bossBattleHp = 1500,
            lore = "A pile of wet cardboard boxes that came alive behind the hotels."
        ),
        BossDefinition(
            id = "boss_b3", name = "Méduse plastique", title = "Gulf Jelly of Bags",
            arenaId = 0, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_meduse,
            bodyColor = Color(0xFF06B6D4), accentColor = Color(0xFFA5F3FC),
            preferredWaste = WasteType.PLASTIC_BLUE, abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Tentacle Sweep", abilityDescription = "Clears a full vertical column with stinging plastic tentacles.",
            baseAbilityDamage = 290, chargeRequired = 16, bossBattleHp = 1620,
            lore = "A jellyfish of plastic bags drifted in from the Gulf of Tunis."
        ),
        BossDefinition(
            id = "boss_b4", name = "Marée noire", title = "Oil Slick Tide",
            arenaId = 0, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_maree,
            bodyColor = Color(0xFF334155), accentColor = Color(0xFFFCA5A5),
            preferredWaste = WasteType.ORGANIC_RED, abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Slick Purge", abilityDescription = "Recycles every Organic tile on the board.",
            baseAbilityDamage = 320, chargeRequired = 16, bossBattleHp = 1700,
            lore = "An oil slick and its trash washed up on the rocks of Tabarka."
        ),
        BossDefinition(
            id = "boss_b5", name = "Usine fumante", title = "Smoking Factory",
            arenaId = 0, rarity = BossRarity.EPIC, spriteRes = R.drawable.enemy_kraken,
            bodyColor = Color(0xFF8B5CF6), accentColor = Color(0xFFFACC15),
            preferredWaste = WasteType.ALUMINUM_YELLOW, abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Smokestack Strike", abilityDescription = "Cross-board crush plus heavy direct damage.",
            baseAbilityDamage = 400, chargeRequired = 17, bossBattleHp = 1900,
            lore = "Factory smoke and waste around the Bizerte lake gave life to this giant."
        ),

        // Arena 1: Monuments
        BossDefinition(
            id = "boss_m1", name = "Momie de carton", title = "Souvenir Wrap Mummy",
            arenaId = 1, rarity = BossRarity.COMMON, spriteRes = R.drawable.enemy_momie,
            bodyColor = Color(0xFFB45309), accentColor = Color(0xFFFDE68A),
            preferredWaste = WasteType.CARDBOARD_TAN, abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Unwrap Sweep", abilityDescription = "Flattens an entire row and deals steady damage.",
            baseAbilityDamage = 260, chargeRequired = 14, bossBattleHp = 1750,
            lore = "Wrapped in the cardboard of souvenirs left behind by tourists."
        ),
        BossDefinition(
            id = "boss_m2", name = "Amphore pourrie", title = "Rotten Picnic Jar",
            arenaId = 1, rarity = BossRarity.COMMON, spriteRes = R.drawable.enemy_amphore,
            bodyColor = Color(0xFFDC2626), accentColor = Color(0xFFFCA5A5),
            preferredWaste = WasteType.ORGANIC_RED, abilityType = BossAbilityType.ECO_BOMB,
            abilityName = "Compost Burst", abilityDescription = "Smashes a 3x3 crater of tiles into compost.",
            baseAbilityDamage = 300, chargeRequired = 15, bossBattleHp = 1800,
            lore = "An old amphora full of picnic leftovers that went bad."
        ),
        BossDefinition(
            id = "boss_m3", name = "Golem de rouille", title = "Rusty Can Golem",
            arenaId = 1, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_golem,
            bodyColor = Color(0xFFF59E0B), accentColor = Color(0xFFFEF08A),
            preferredWaste = WasteType.ALUMINUM_YELLOW, abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Can Magnet", abilityDescription = "Magnetizes and crushes every Can tile on the board.",
            baseAbilityDamage = 330, chargeRequired = 16, bossBattleHp = 1880,
            lore = "Rusty cans forgotten between the ancient columns."
        ),
        BossDefinition(
            id = "boss_m4", name = "Gladiateur bouteilles", title = "Bottle-Armour Gladiator",
            arenaId = 1, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_gladiateur,
            bodyColor = Color(0xFF0284C7), accentColor = Color(0xFFBAE6FD),
            preferredWaste = WasteType.PLASTIC_BLUE, abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Arena Charge", abilityDescription = "Sweeps two adjacent columns clean.",
            baseAbilityDamage = 360, chargeRequired = 16, bossBattleHp = 1980,
            lore = "Fights in the arena of El Jem wearing armour made of plastic bottles."
        ),
        BossDefinition(
            id = "boss_m5", name = "Mosaïque de tessons", title = "Broken Glass Mosaic",
            arenaId = 1, rarity = BossRarity.EPIC, spriteRes = R.drawable.enemy_mosaique,
            bodyColor = Color(0xFF059669), accentColor = Color(0xFF6EE7B7),
            preferredWaste = WasteType.GLASS_GREEN, abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Shard Storm", abilityDescription = "Fires a cross of glass shards for massive direct damage.",
            baseAbilityDamage = 450, chargeRequired = 17, bossBattleHp = 2150,
            lore = "Broken glass shards that believe they are a Roman mosaic."
        ),

        // Arena 2: Desert & Oases
        BossDefinition(
            id = "boss_d1", name = "Pneumatos", title = "Burnt Tyre Stack",
            arenaId = 2, rarity = BossRarity.COMMON, spriteRes = R.drawable.enemy_pneu,
            bodyColor = Color(0xFF44403C), accentColor = Color(0xFFFEF3C7),
            preferredWaste = WasteType.ALUMINUM_YELLOW, abilityType = BossAbilityType.ROW_SWEEP,
            abilityName = "Rolling Tyre", abilityDescription = "Rolls across a full row at blistering speed.",
            baseAbilityDamage = 290, chargeRequired = 14, bossBattleHp = 2050,
            lore = "A pile of old burnt tyres by the road to the Sahara."
        ),
        BossDefinition(
            id = "boss_d2", name = "Tempête de sachets", title = "Plastic Bag Sandstorm",
            arenaId = 2, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_tempete,
            bodyColor = Color(0xFFD97706), accentColor = Color(0xFFFEF3C7),
            preferredWaste = WasteType.PLASTIC_BLUE, abilityType = BossAbilityType.COLOR_PURGE,
            abilityName = "Desert Flower Gust", abilityDescription = "Recycles every Plastic tile on the board.",
            baseAbilityDamage = 360, chargeRequired = 16, bossBattleHp = 2180,
            lore = "The wind of Douz lifts thousands of bags: a real plastic storm."
        ),
        BossDefinition(
            id = "boss_d3", name = "Scorpion de canettes", title = "Bottle-Cap Scorpion",
            arenaId = 2, rarity = BossRarity.RARE, spriteRes = R.drawable.enemy_scorpion,
            bodyColor = Color(0xFFEA580C), accentColor = Color(0xFFFED7AA),
            preferredWaste = WasteType.ALUMINUM_YELLOW, abilityType = BossAbilityType.COL_SWEEP,
            abilityName = "Stinger Columns", abilityDescription = "Clears twin vertical columns.",
            baseAbilityDamage = 380, chargeRequired = 16, bossBattleHp = 2250,
            lore = "A scorpion of bottle caps and cans that crawled out of the desert."
        ),
        BossDefinition(
            id = "boss_d4", name = "Gadoue", title = "Palm Grove Sludge",
            arenaId = 2, rarity = BossRarity.EPIC, spriteRes = R.drawable.enemy_djinn,
            bodyColor = Color(0xFF65A30D), accentColor = Color(0xFFD9F99D),
            preferredWaste = WasteType.ORGANIC_RED, abilityType = BossAbilityType.ECO_BOMB,
            abilityName = "Mega Eco-Bomb", abilityDescription = "Detonates a 3x3 Eco-Bomb for massive Boss damage.",
            baseAbilityDamage = 440, chargeRequired = 17, bossBattleHp = 2350,
            lore = "Waste water and dump sludge, awakened in the heart of the palm grove."
        ),
        BossDefinition(
            id = "boss_d5", name = "Roi des ordures", title = "King of the Dumps",
            arenaId = 2, rarity = BossRarity.LEGENDARY, spriteRes = R.drawable.enemy_roi,
            bodyColor = Color(0xFF0891B2), accentColor = Color(0xFFFDE047),
            preferredWaste = WasteType.ORGANIC_RED, abilityType = BossAbilityType.MEGA_STRIKE,
            abilityName = "Royal Purge", abilityDescription = "A radiant cross-board nova dealing supreme Boss damage!",
            baseAbilityDamage = 550, chargeRequired = 18, bossBattleHp = 2500,
            lore = "A living heap of garbage: it rules over every illegal dump."
        )
    )

    val quests: List<QuestDefinition> = listOf(
        QuestDefinition(
            id = "q_daily_blue_100",
            title = "Plastic Tide Breaker",
            description = "Match 100 blue tiles (Plastic Bottles) in a single match",
            category = QuestCategory.DAILY,
            metric = QuestMetric.SINGLE_MATCH_BLUE_TILES,
            target = 100,
            coinReward = 180,
            cardRewardCount = 2,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_daily_waves_6",
            title = "Wave Sweeper",
            description = "Clear 6 mini-trash waves across 1v1 matches",
            category = QuestCategory.DAILY,
            metric = QuestMetric.CLEAR_WAVES,
            target = 6,
            coinReward = 120,
            cardRewardCount = 1,
            rewardRarity = BossRarity.COMMON
        ),
        QuestDefinition(
            id = "q_daily_skills_5",
            title = "Eco-Deck Tactician",
            description = "Activate 5 charged Boss abilities during battles",
            category = QuestCategory.DAILY,
            metric = QuestMetric.USE_BOSS_ABILITIES,
            target = 5,
            coinReward = 150,
            cardRewardCount = 1,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_daily_tiles_250",
            title = "Rapid Sorter",
            description = "Match 250 total recyclable tiles across matches",
            category = QuestCategory.DAILY,
            metric = QuestMetric.MATCH_TOTAL_TILES,
            target = 250,
            coinReward = 140,
            cardRewardCount = 2,
            rewardRarity = BossRarity.COMMON
        ),
        QuestDefinition(
            id = "q_challenge_epic_3",
            title = "Epic Purifier",
            description = "Win 3 matches using only Epic Bosses in your Eco-Deck",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.WIN_WITH_ONLY_EPIC_BOSSES,
            target = 3,
            coinReward = 450,
            cardRewardCount = 3,
            rewardRarity = BossRarity.EPIC
        ),
        QuestDefinition(
            id = "q_challenge_beaches_2",
            title = "Mediterranean Guardian",
            description = "Purify 2 Coasts & Lakes locations to 100% clean-up",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.PURIFY_BEACHES,
            target = 2,
            coinReward = 260,
            cardRewardCount = 2,
            rewardRarity = BossRarity.RARE
        ),
        QuestDefinition(
            id = "q_challenge_combo_4",
            title = "Cascade Legend",
            description = "Trigger a 4x or higher Match-3 chain combo in battle",
            category = QuestCategory.CHALLENGE,
            metric = QuestMetric.HIGH_COMBO_CHAIN,
            target = 1,
            coinReward = 220,
            cardRewardCount = 2,
            rewardRarity = BossRarity.EPIC
        )
    )

    fun arenaForTrophies(trophies: Int): ArenaDefinition {
        return arenas.lastOrNull { trophies >= it.minTrophies } ?: arenas.first()
    }

    fun arenaById(arenaId: Int): ArenaDefinition {
        return arenas.firstOrNull { it.id == arenaId } ?: arenas.first()
    }

    fun placeById(placeId: String): TunisianPlace {
        return allPlaces.firstOrNull { it.id == placeId } ?: allPlaces.first()
    }

    fun bossById(bossId: String): BossDefinition {
        return bosses.firstOrNull { it.id == bossId } ?: bosses.first()
    }

    // Cumulative Boss Pool: higher Arenas include all earlier Bosses + new ones
    fun cumulativeBossPoolForArena(arenaId: Int): List<BossDefinition> {
        return bosses.filter { it.arenaId <= arenaId }
    }

    fun copiesNeededForNextLevel(currentLevel: Int, rarity: BossRarity): Int {
        val base = when (rarity) {
            BossRarity.COMMON -> 4
            BossRarity.RARE -> 3
            BossRarity.EPIC -> 2
            BossRarity.LEGENDARY -> 2
        }
        return base * currentLevel.coerceAtLeast(1)
    }

    fun coinsNeededForNextLevel(currentLevel: Int, rarity: BossRarity): Int {
        val base = when (rarity) {
            BossRarity.COMMON -> 80
            BossRarity.RARE -> 140
            BossRarity.EPIC -> 220
            BossRarity.LEGENDARY -> 350
        }
        return base * currentLevel.coerceAtLeast(1)
    }
}
