package com.example

import com.example.data.RewardRules
import com.example.engine.BOARD_SIZE
import com.example.engine.BattleRules
import com.example.engine.BoardTile
import com.example.engine.Match3Engine
import com.example.engine.MatchTelemetry
import com.example.engine.MobState
import com.example.engine.PlayerBattleSnapshot
import com.example.model.GameCatalog
import com.example.model.UserProfile
import com.example.model.WasteType
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BattleRulesTest {

    private val waves = listOf(
        listOf("mob_sachet", "mob_bouteille"),
        listOf("mob_canette", "mob_tesson", "mob_goutte"),
        listOf("mob_sachet", "mob_canette", "mob_bouteille"),
        listOf("mob_sachet", "mob_bouteille", "mob_canette", "mob_goutte")
    )

    @Test
    fun `catalog has 15 places with art and 15 bosses with team sprites`() {
        assertEquals(15, GameCatalog.allPlaces.size)
        assertEquals(15, GameCatalog.bosses.size)
        assertEquals(15, GameCatalog.bosses.map { it.id }.distinct().size)
        assertEquals(listOf(0, 300, 600), GameCatalog.arenas.map { it.minTrophies })
        // The four places with painted pollution have both a cartoon and a litter overlay
        val painted = GameCatalog.allPlaces.filter { it.dirtyRes != null }.map { it.id }.toSet()
        assertEquals(setOf("place_hammamet", "place_ichkeul", "place_carthage", "place_tozeur"), painted)
        assertTrue(GameCatalog.allPlaces.filter { it.dirtyRes != null }.all { it.toonRes != null })
    }

    @Test
    fun `match waves are deterministic per match and use the arena minis`() {
        for (arena in 0..2) {
            val a = GameCatalog.matchWaves("match_abc", arena)
            val b = GameCatalog.matchWaves("match_abc", arena)
            assertEquals(a, b)
            assertEquals(BattleRules.MINI_WAVE_COUNT, a.size)
            assertEquals(listOf(2, 3, 3, 4), a.map { it.size })
            val pool = GameCatalog.minisForArena(arena).map { it.id }.toSet()
            assertTrue(a.flatten().all { it in pool })
        }
    }

    @Test
    fun `wave hp is split across mobs and sums to the wave total`() {
        val total = BattleRules.waveTotalHp(1, 0)
        val mobs = BattleRules.buildWave(waves[0], total)
        assertEquals(2, mobs.size)
        assertTrue(kotlin.math.abs(mobs.sumOf { it.hp } - total) <= 2)
        assertTrue(mobs.all { it.alive && it.hp == it.maxHp })
    }

    @Test
    fun `damage kills mobs in order, overflows across 4 waves, and advances to the wave 5 boss`() {
        val wave1 = BattleRules.buildWave(waves[0], BattleRules.waveTotalHp(1, 0))
        // Kill only the first mob, a bit of overflow into the second
        val hit1 = BattleRules.applyDamage(wave1[0].hp + 10, 1, wave1, 2400, waves, 0, "Boss")
        assertEquals(1, hit1.waveIndex)
        assertFalse(hit1.mobs[0].alive)
        assertEquals(wave1[1].hp - 10, hit1.mobs[1].hp)
        assertEquals(listOf("Sachet volant"), hit1.defeatedNames)

        // Finish wave 1 exactly: wave 2 (3 mobs) appears even with no damage left over
        val hit2 = BattleRules.applyDamage(hit1.mobs[1].hp, 1, hit1.mobs, 2400, waves, 0, "Boss")
        assertEquals(2, hit2.waveIndex)
        assertTrue(hit2.waveTransitioned)
        assertEquals(3, hit2.mobs.size)
        assertEquals(waves[1], hit2.mobs.map { it.id })

        // Clear wave 2 -> wave 3 (3 mobs) -> wave 4 (4 mobs)
        val wave2Hp = hit2.mobs.sumOf { it.hp }
        val hit3 = BattleRules.applyDamage(wave2Hp, 2, hit2.mobs, 2400, waves, 0, "Boss")
        assertEquals(3, hit3.waveIndex)
        assertEquals(3, hit3.mobs.size)

        val wave3Hp = hit3.mobs.sumOf { it.hp }
        val hit4 = BattleRules.applyDamage(wave3Hp, 3, hit3.mobs, 2400, waves, 0, "Boss")
        assertEquals(4, hit4.waveIndex)
        assertEquals(4, hit4.mobs.size)

        // Huge hit clears wave 4 and the rest goes into the Wave 5 Shared Boss
        val wave4Hp = hit4.mobs.sumOf { it.hp }
        val hit5 = BattleRules.applyDamage(wave4Hp + 400, 4, hit4.mobs, 2400, waves, 0, "Boss")
        assertEquals(BattleRules.BOSS_WAVE_INDEX, hit5.waveIndex)
        assertEquals(2000, hit5.bossHp)
        assertTrue(hit5.mobs.isEmpty())

        val kill = BattleRules.applyDamage(5000, BattleRules.BOSS_WAVE_INDEX, emptyList(), 2000, waves, 0, "Plasticos")
        assertEquals(0, kill.bossHp)
        assertEquals(listOf("Plasticos"), kill.defeatedNames)
    }

    @Test
    fun `weakness targets the first alive mob's material and boss enrages under 35 percent hp`() {
        val boss = GameCatalog.bossById("boss_b1")
        val mobs = listOf(MobState("mob_sachet", 0, 100), MobState("mob_canette", 80, 100))
        val (weak, mult) = BattleRules.weaknessFor(1, mobs, boss)
        assertEquals(WasteType.ALUMINUM_YELLOW, weak)
        assertEquals(BattleRules.MINI_WEAKNESS_MULT, mult, 0.001f)
        val counts = mapOf(WasteType.ALUMINUM_YELLOW to 3, WasteType.GLASS_GREEN to 3)
        assertEquals(3 * BattleRules.MINI_WEAKNESS_MULT + 3f, BattleRules.weightedCleared(counts, weak, mult), 0.01f)
        assertEquals(boss.preferredWaste to BattleRules.BOSS_WEAKNESS_MULT, BattleRules.weaknessFor(BattleRules.BOSS_WAVE_INDEX, emptyList(), boss))

        // Boss Enrage shield triggers at <= 35% HP on Wave 5
        assertFalse(BattleRules.isBossEnraged(4, 100, 2000))
        assertFalse(BattleRules.isBossEnraged(BattleRules.BOSS_WAVE_INDEX, 1000, 2000))
        assertTrue(BattleRules.isBossEnraged(BattleRules.BOSS_WAVE_INDEX, 700, 2000))
        assertFalse(BattleRules.isBossEnraged(BattleRules.BOSS_WAVE_INDEX, 0, 2000))
    }

    @Test
    fun `timeout winner is identical on both clients and breaks ties deterministically`() {
        val a = PlayerBattleSnapshot(uid = "alice", totalDamage = 900, restorationPct = 40)
        val b = PlayerBattleSnapshot(uid = "bob", totalDamage = 900, restorationPct = 40)
        assertEquals(BattleRules.timeoutWinner(a, b), BattleRules.timeoutWinner(a, b))
        assertEquals("alice", BattleRules.timeoutWinner(a, b))
        assertEquals("alice", BattleRules.timeoutWinner(b, a)) // order doesn't matter on a full tie
        assertEquals("bob", BattleRules.timeoutWinner(a, b.copy(totalDamage = 901)))
        assertEquals("bob", BattleRules.timeoutWinner(a, b.copy(restorationPct = 41)))
    }

    @Test
    fun `boards never keep leftover matches after resolution`() {
        val rng = Random(1234)
        repeat(200) {
            val board = Match3Engine.createInitialBoard(rng)
            val move = Match3Engine.findAnyValidMove(board) ?: return@repeat
            val res = Match3Engine.attemptSwap(board, move.first, move.second, rng) ?: return@repeat
            assertFalse("leftover run", hasRun(res.newBoard))
            assertTrue(Match3Engine.hasValidMove(res.newBoard))
        }
    }

    @Test
    fun `breakResidualMatches removes runs`() {
        val types = WasteType.entries
        // Striped board with a forced horizontal run of PLASTIC in row 0
        val board = List(BOARD_SIZE) { r ->
            List(BOARD_SIZE) { c ->
                val t = if (r == 0 && c < 4) WasteType.PLASTIC_BLUE else types[(r * 2 + c * 3 + 1) % types.size]
                BoardTile(id = (r * BOARD_SIZE + c).toLong(), type = t)
            }
        }
        assertTrue(hasRun(board))
        assertFalse(hasRun(Match3Engine.breakResidualMatches(board, Random(1))))
    }

    @Test
    fun `match result rewards and trophy floor`() {
        val profile = UserProfile(userId = "u", trophies = 5, ecoCoins = 100, bossLevels = mapOf("boss_b1" to 1), bossCopies = mapOf("boss_b1" to 0))
        val tele = MatchTelemetry(false, 0, "place_hammamet", "boss_b5", 100, 10, 1, 0, 1, 20, emptyMap(), listOf("boss_b1"))
        val (lost, outcome) = RewardRules.matchResult(profile, tele)
        assertEquals(0, lost.trophies)
        assertEquals(-5, outcome.trophyDelta) // real delta after the floor
        assertEquals(1, lost.losses)

        val (won, wonOutcome) = RewardRules.matchResult(profile, tele.copy(won = true))
        assertEquals(35, won.trophies)
        assertEquals(1, won.bossLevels["boss_b5"])
        assertEquals("boss_b5", wonOutcome.capturedBossId)
        assertEquals(100 + RewardRules.coinsFor(true, 0), won.ecoCoins)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `upgrade requires enough copies`() {
        RewardRules.upgradeBoss(UserProfile(bossLevels = mapOf("boss_b1" to 1), bossCopies = mapOf("boss_b1" to 0), ecoCoins = 9999), "boss_b1")
    }

    @Test
    fun `AI rival provides competitive pressure across all 5 stages`() {
        for (arena in 0..2) {
            val avgBossHp = BattleRules.scaledBossHp(
                GameCatalog.cumulativeBossPoolForArena(arena).map { it.bossBattleHp }.average().toInt()
            )
            val pool = BattleRules.totalPool(arena, avgBossHp)
            val times = (1..20).map { seed ->
                val rng = Random(seed * 31 + arena)
                var board = Match3Engine.createInitialBoard(rng)
                var charges = listOf(0, 0, 0)
                var dealt = 0
                var sec = 0
                while (dealt < pool && sec < 400) {
                    val t = BattleRules.botTick(board, charges, listOf("boss_b1", "boss_b2", "boss_b3"), listOf(1 + arena, 1 + arena, 1 + arena), arena, rng)
                    board = t.board; charges = t.charges; dealt += t.damage; sec++
                }
                sec
            }
            val avg = times.average()
            println("arena $arena (5-stage pool=$pool HP): AI purifies in ${avg.toInt()}s (min ${times.min()}, max ${times.max()})")
            assertTrue("arena $arena too fast: $avg s", avg >= 75)
            assertTrue("arena $arena too slow: $avg s", avg <= 145)
        }
    }

    @Test
    fun `using a skill resets any other deck slot with the same waste nature`() {
        // boss_b1 (Plastic), boss_b2 (Cardboard), boss_b3 (Plastic)
        val deck = listOf("boss_b1", "boss_b2", "boss_b3")
        val before = listOf(100, 65, 85)

        // Casting slot 0 (Plastic) resets slot 0 AND sibling slot 2 (Plastic), keeps slot 1 (Cardboard) intact
        val result = BattleRules.computeUpdatedSkillCharges(
            currentCharges = before,
            deckBossIds = deck,
            countsByType = mapOf(WasteType.PLASTIC_BLUE to 7),
            totalCleared = 7,
            consumedSlotIndex = 0
        )
        assertEquals(listOf(0, 65, 0), result.charges)
        assertEquals(setOf(2), result.resetSiblingSlots)

        // Casting slot 1 (Cardboard) only resets slot 1, leaves Plastic slots untouched
        val result2 = BattleRules.computeUpdatedSkillCharges(
            currentCharges = listOf(55, 100, 40),
            deckBossIds = deck,
            countsByType = mapOf(WasteType.CARDBOARD_TAN to 9),
            totalCleared = 9,
            consumedSlotIndex = 1
        )
        assertEquals(listOf(55, 0, 40), result2.charges)
        assertTrue(result2.resetSiblingSlots.isEmpty())
    }

    @Test
    fun `skills take multiple deliberate moves to charge`() {
        val deck = listOf("boss_b1", "boss_b2", "boss_b5") // Common Plastic, Common Cardboard, Epic Can
        var charges = listOf(0, 0, 0)
        var moves = 0
        // Simulate repeated 3-tile Plastic matches until slot 0 (Plasticos) reaches 100%
        while (charges[0] < 100 && moves < 30) {
            moves++
            charges = BattleRules.computeUpdatedSkillCharges(
                currentCharges = charges,
                deckBossIds = deck,
                countsByType = mapOf(WasteType.PLASTIC_BLUE to 3),
                totalCleared = 3,
                consumedSlotIndex = null
            ).charges
        }
        assertTrue("Expected at least 6 on-color 3-match moves to charge, got $moves", moves >= 6)
        assertTrue("Expected at most 12 on-color 3-match moves to charge, got $moves", moves <= 12)
        // Off-color slots should still be well below 100%
        assertTrue("Off-color slot should charge slower than on-color slot", charges[1] < 60)
    }

    @Test
    fun `speed streak and final sprint multipliers scale competitively`() {
        assertEquals(1.0f, BattleRules.streakDamageMultiplier(1), 0.001f)
        assertEquals(1.24f, BattleRules.streakDamageMultiplier(4), 0.001f)
        assertEquals(1.40f, BattleRules.streakDamageMultiplier(8), 0.001f)
        assertEquals(1.0f, BattleRules.finalSprintMultiplier(45), 0.001f)
        assertEquals(1.25f, BattleRules.finalSprintMultiplier(15), 0.001f)
        assertEquals(1.25f, BattleRules.finalSprintMultiplier(1), 0.001f)
    }

    @Test
    fun `horizontal place roulette reel lands accurately on every target place`() {
        for (itemCount in listOf(5, 15)) {
            var currentSlot = 0f
            for (targetIdx in 0 until itemCount) {
                val destSlot = com.example.ui.components.RouletteReelMath.targetSlotForIndex(
                    currentSlot = currentSlot,
                    targetIndex = targetIdx,
                    itemCount = itemCount,
                    minCardsTravelled = 20
                )
                assertTrue("Reel should glide forward at least 20 cards", destSlot - currentSlot >= 20f)
                val landedIdx = com.example.ui.components.RouletteReelMath.indexAtSlot(destSlot, itemCount)
                assertEquals(
                    "Failed reel landing for itemCount=$itemCount target=$targetIdx",
                    targetIdx,
                    landedIdx
                )
                currentSlot = destSlot
            }
        }
    }

    @Test
    fun `winning a match purifies the place once and flags first-time album unlock`() {
        val startProfile = UserProfile(purifiedPlaceIds = emptyList())
        val winTelemetry = MatchTelemetry(
            won = true,
            arenaId = 0,
            placeId = "place_tabarka",
            bossId = "boss_b4",
            totalDamage = 2500,
            restorationPct = 100,
            wavesCleared = 5,
            abilitiesUsed = 3,
            maxCombo = 4,
            totalTilesMatched = 70,
            tilesMatchedByType = mapOf(WasteType.PLASTIC_BLUE to 20),
            deckBossIds = listOf("boss_b1", "boss_b2", "boss_b3")
        )

        // First win at Tabarka -> unlocks place_tabarka and sets newlyPurifiedPlaceId
        val (afterFirstWin, firstOutcome) = RewardRules.matchResult(startProfile, winTelemetry)
        assertEquals(listOf("place_tabarka"), afterFirstWin.purifiedPlaceIds)
        assertEquals("place_tabarka", firstOutcome.newlyPurifiedPlaceId)

        // Second win at Tabarka -> stays deduplicated and does not re-trigger first-time label
        val (afterSecondWin, secondOutcome) = RewardRules.matchResult(afterFirstWin, winTelemetry)
        assertEquals(listOf("place_tabarka"), afterSecondWin.purifiedPlaceIds)
        assertEquals(null, secondOutcome.newlyPurifiedPlaceId)

        // Losing at Hammamet -> does not unlock place_hammamet
        val (afterLoss, lossOutcome) = RewardRules.matchResult(
            afterSecondWin,
            winTelemetry.copy(won = false, placeId = "place_hammamet")
        )
        assertEquals(listOf("place_tabarka"), afterLoss.purifiedPlaceIds)
        assertEquals(null, lossOutcome.newlyPurifiedPlaceId)
    }

    private fun hasRun(board: List<List<BoardTile>>): Boolean {
        for (r in 0 until BOARD_SIZE) for (c in 0 until BOARD_SIZE - 2) {
            if (board[r][c].type == board[r][c + 1].type && board[r][c].type == board[r][c + 2].type) return true
        }
        for (c in 0 until BOARD_SIZE) for (r in 0 until BOARD_SIZE - 2) {
            if (board[r][c].type == board[r + 1][c].type && board[r][c].type == board[r + 2][c].type) return true
        }
        return false
    }
}
