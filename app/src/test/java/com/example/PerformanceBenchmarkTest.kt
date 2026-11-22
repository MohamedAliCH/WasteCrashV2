package com.example

import com.example.engine.BattleRules
import com.example.engine.Match3Engine
import com.example.model.GameCatalog
import com.example.model.WasteType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * JVM performance benchmark suite validating that core match-3 resolution,
 * board serialization, and 5-stage combat rules stay well inside the 16.6ms
 * 60fps Android frame budget.
 */
class PerformanceBenchmarkTest {

    @Test
    fun `match3 swap cascade resolution stays well under 16ms 60fps frame budget`() {
        val rng = Random(42)
        var board = Match3Engine.createInitialBoard(rng)

        // Warm up JIT
        repeat(80) {
            val mv = Match3Engine.findAnyValidMove(board) ?: run {
                board = Match3Engine.createInitialBoard(rng)
                Match3Engine.findAnyValidMove(board)!!
            }
            val stepped = Match3Engine.attemptSwapSteps(board, mv.first, mv.second, rng)
            if (stepped != null) board = stepped.finalResolution.newBoard
        }

        val samplesUs = LongArray(500)
        var totalSteps = 0
        for (i in samplesUs.indices) {
            val mv = Match3Engine.findAnyValidMove(board) ?: run {
                board = Match3Engine.createInitialBoard(rng)
                Match3Engine.findAnyValidMove(board)!!
            }
            val t0 = System.nanoTime()
            val stepped = Match3Engine.attemptSwapSteps(board, mv.first, mv.second, rng)
            val dtUs = (System.nanoTime() - t0) / 1_000L
            samplesUs[i] = dtUs
            if (stepped != null) {
                board = stepped.finalResolution.newBoard
                totalSteps += stepped.steps.size
            }
        }
        samplesUs.sort()
        val p50Ms = samplesUs[samplesUs.size / 2] / 1000.0
        val p95Ms = samplesUs[(samplesUs.size * 95) / 100] / 1000.0
        val maxMs = samplesUs.last() / 1000.0

        println(
            "PERF Match3Engine.attemptSwapSteps (500 moves, $totalSteps cascade steps): " +
                "p50=${"%.3f".format(p50Ms)}ms, p95=${"%.3f".format(p95Ms)}ms, max=${"%.3f".format(maxMs)}ms"
        )
        assertTrue("Expected p95 swap resolution < 4.0ms, got $p95Ms ms", p95Ms < 4.0)
    }

    @Test
    fun `board encode decode roundtrip and battle rules stay sub-millisecond`() {
        val rng = Random(99)
        val board = Match3Engine.createInitialBoard(rng)
        val deck = listOf("boss_b1", "boss_b2", "boss_b3")
        val waves = GameCatalog.matchWaves("perf_seed", 1)
        val initialMobs = BattleRules.buildWave(waves[0], BattleRules.waveTotalHp(1, 1))

        // Warm up
        repeat(100) {
            val flat = Match3Engine.encodeBoardFlat(board)
            Match3Engine.decodeBoardFlat(flat)
            BattleRules.applyDamage(180, 1, initialMobs, 2600, waves, 1, "Boss")
        }

        val tEncStart = System.nanoTime()
        repeat(1000) {
            val flat = Match3Engine.encodeBoardFlat(board)
            Match3Engine.decodeBoardFlat(flat)
        }
        val avgRoundtripMs = (System.nanoTime() - tEncStart) / 1_000_000.0 / 1000.0

        val counts = mapOf(WasteType.PLASTIC_BLUE to 4, WasteType.ALUMINUM_YELLOW to 3)
        val tRulesStart = System.nanoTime()
        var charges = listOf(10, 20, 30)
        repeat(1000) {
            BattleRules.applyDamage(210, 1, initialMobs, 2600, waves, 1, "Boss")
            charges = BattleRules.computeUpdatedSkillCharges(charges, deck, counts, 7, null).charges
        }
        val avgRulesMs = (System.nanoTime() - tRulesStart) / 1_000_000.0 / 1000.0

        println(
            "PERF Serialization (1000 roundtrips): avg=${"%.4f".format(avgRoundtripMs)}ms/op | " +
                "BattleRules (1000 ticks): avg=${"%.4f".format(avgRulesMs)}ms/op"
        )
        assertTrue("Expected board roundtrip < 0.5ms/op, got $avgRoundtripMs ms", avgRoundtripMs < 0.5)
        assertTrue("Expected BattleRules tick < 0.5ms/op, got $avgRulesMs ms", avgRulesMs < 0.5)
    }

    @Test
    fun `harder 5-stage match requires sustained strategic play instead of ending too fast`() {
        val rng = Random(2026)
        val boss = GameCatalog.bossById("boss_b1")
        val bossMaxHp = BattleRules.scaledBossHp(boss.bossBattleHp)
        val waves = GameCatalog.matchWaves("hard_match_test", 0)
        var wave = 1
        var mobs = BattleRules.buildWave(waves[0], BattleRules.waveTotalHp(1, 0))
        var bossHp = bossMaxHp
        var board = Match3Engine.createInitialBoard(rng)
        var charges = listOf(0, 0, 0)
        val deck = listOf("boss_b1", "boss_b2", "boss_b5")

        var moves = 0
        var enrageHits = 0
        while (bossHp > 0 && moves < 150) {
            moves++
            val mv = Match3Engine.findAnyValidMove(board) ?: run {
                board = Match3Engine.createInitialBoard(rng)
                Match3Engine.findAnyValidMove(board)!!
            }
            val stepped = Match3Engine.attemptSwapSteps(board, mv.first, mv.second, rng) ?: continue
            board = stepped.finalResolution.newBoard
            for (step in stepped.steps) {
                val (weakness, weakMult) = BattleRules.weaknessFor(wave, mobs, boss)
                val enraged = BattleRules.isBossEnraged(wave, bossHp, bossMaxHp)
                if (enraged) enrageHits++
                val nonWeakMult = if (enraged) BattleRules.BOSS_ENRAGE_ARMOR_MULT else 1f
                val dmg = (BattleRules.weightedCleared(step.countsByType, weakness, weakMult, nonWeakMult) * 18f * (1f + (step.comboIndex - 1) * 0.35f)).toInt()
                val out = BattleRules.applyDamage(dmg, wave, mobs, bossHp, waves, 0, boss.name)
                wave = out.waveIndex
                mobs = out.mobs
                bossHp = out.bossHp
            }
            charges = BattleRules.computeUpdatedSkillCharges(
                charges, deck, stepped.finalResolution.countsByType, stepped.finalResolution.totalCleared, null
            ).charges
        }

        println("BALANCE 5-stage match simulation (Arena 0, no skills): cleared in $moves moves, enraged boss hits=$enrageHits")
        assertTrue("Match ended too fast ($moves moves) — should require at least 35 moves without skills", moves >= 35)
        assertTrue("Expected Boss Enrage shield phase to trigger during Wave 5", enrageHits > 0)
    }
}
