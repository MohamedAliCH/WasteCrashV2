package com.example

import com.example.data.RoomCodeGenerator
import com.example.network.EdgeWebSocketClient
import com.example.network.RttJitterCalculator
import org.junit.Assert.*
import org.junit.Test

class FiveGMultiplayerTest {

    @Test
    fun rttJitterCalculator_computesStableLowJitterOn5gSaSamples() {
        val calc = RttJitterCalculator(maxSamples = 30)
        val samples = listOf(14, 15, 13, 14, 16, 15, 14, 15)
        var t = 10_000L
        samples.forEach { rtt ->
            calc.recordRttSample(rtt, nowMs = t)
            t += 200L
        }
        val snap = calc.snapshot(nowMs = t)
        assertEquals(15, snap.currentRttMs)
        assertEquals(13, snap.minRttMs)
        assertEquals(15, snap.avgRttMs)
        assertTrue("Expected low jitter on stable 5G SA samples, got ${snap.jitterMs}", (snap.jitterMs ?: 99) <= 2)
        assertEquals(samples.size, snap.historyMs.size)
        assertTrue("Expected positive packet rate", snap.packetRateHz > 0f)
    }

    @Test
    fun rttJitterCalculator_reflectsHighJitterAndCapsHistoryWindow() {
        val calc = RttJitterCalculator(maxSamples = 10)
        var t = 1_000L
        repeat(16) { i ->
            val rtt = if (i % 2 == 0) 20 else 120
            calc.recordRttSample(rtt, nowMs = t)
            t += 100L
        }
        val snap = calc.snapshot(nowMs = t)
        assertEquals(10, snap.historyMs.size)
        assertTrue("Expected elevated jitter on spiky 4G samples, got ${snap.jitterMs}", (snap.jitterMs ?: 0) >= 35)
        assertEquals(120, snap.p95RttMs)
    }

    @Test
    fun roomCodeGenerator_createsAndNormalizesValidSixCharCodes() {
        repeat(20) {
            val code = RoomCodeGenerator.generate()
            assertTrue("Generated code should start with TN: $code", code.startsWith("TN"))
            assertTrue("Generated code should be valid: $code", RoomCodeGenerator.isValid(code))
        }
        assertEquals("TN4K9P", RoomCodeGenerator.normalize("  tn-4k 9p  "))
        assertTrue(RoomCodeGenerator.isValid(RoomCodeGenerator.normalize("tn4k9p")))
        assertFalse(RoomCodeGenerator.isValid("TN12"))
    }

    @Test
    fun edgeWebSocketUrlValidation_acceptsOnlyWsAndWss() {
        assertTrue(EdgeWebSocketClient.isValidWebSocketUrl("wss://mec.tunisia5g.tn/ws"))
        assertTrue(EdgeWebSocketClient.isValidWebSocketUrl("ws://10.45.0.1:8080/ws"))
        assertFalse(EdgeWebSocketClient.isValidWebSocketUrl("https://example.com"))
        assertFalse(EdgeWebSocketClient.isValidWebSocketUrl(""))
    }
}
