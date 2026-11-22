package com.example.network

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class RadioBearer(val shortLabel: String, val detailLabel: String) {
    FIVE_G_SA("5G SA", "5G Standalone (NR)"),
    FIVE_G_NSA("5G NSA", "5G Non-Standalone"),
    LTE_4G("4G LTE", "4G LTE"),
    CELLULAR_LEGACY("3G/2G", "Cellular"),
    WIFI("Wi-Fi", "Wi-Fi"),
    OFFLINE("Offline", "No Connection")
}

enum class SliceStatus(val shortLabel: String, val detailLabel: String) {
    ACTIVE("5G SLICE", "Low-Latency Slice Active"),
    REQUESTING("SLICING...", "Requesting 5G Latency Slice"),
    PURCHASE_AVAILABLE("BOOST READY", "Carrier 5G Slice Available"),
    UNAVAILABLE("DEFAULT NET", "Standard Network Routing"),
    DISABLED("SLICE OFF", "5G Slice Request Disabled")
}

enum class SyncTransportMode(val shortLabel: String, val detailLabel: String) {
    EDGE_WEBSOCKET("EDGE WS", "Direct Edge WebSocket"),
    FIRESTORE_CLOUD("CLOUD", "Firebase Cloud Sync"),
    LOCAL_OFFLINE("OFFLINE", "Local Practice")
}

data class RadioLinkSnapshot(
    val bearer: RadioBearer = RadioBearer.OFFLINE,
    val sliceStatus: SliceStatus = SliceStatus.UNAVAILABLE,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val isUnmetered: Boolean = false,
    val sliceUpsellAvailable: Boolean = false,
    val carrierName: String = "",
    val statusNote: String = "Monitoring active link..."
)

data class LatencyTelemetryState(
    val bearer: RadioBearer = RadioBearer.OFFLINE,
    val sliceStatus: SliceStatus = SliceStatus.UNAVAILABLE,
    val transportMode: SyncTransportMode = SyncTransportMode.FIRESTORE_CLOUD,
    val currentRttMs: Int? = null,
    val avgRttMs: Int? = null,
    val minRttMs: Int? = null,
    val p95RttMs: Int? = null,
    val jitterMs: Int? = null,
    val packetRateHz: Float = 0f,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val isUnmetered: Boolean = false,
    val rttHistoryMs: List<Int> = emptyList(),
    val sliceUpsellAvailable: Boolean = false,
    val sliceBoundToTransport: Boolean = false,
    val carrierName: String = "",
    val edgeServerStatus: String = "Cloud Default",
    val lastMeasuredAtMs: Long = 0L
) {
    val isLowLatencyReady: Boolean
        get() = bearer == RadioBearer.FIVE_G_SA || sliceStatus == SliceStatus.ACTIVE
}

data class RttStatsSnapshot(
    val currentRttMs: Int? = null,
    val avgRttMs: Int? = null,
    val minRttMs: Int? = null,
    val p95RttMs: Int? = null,
    val jitterMs: Int? = null,
    val packetRateHz: Float = 0f,
    val historyMs: List<Int> = emptyList(),
    val lastMeasuredAtMs: Long = 0L
)

/**
 * Pure RFC 3550 inter-arrival jitter and sliding-window RTT calculator.
 * Only consumes genuine measured round-trip timings; never fabricates samples.
 */
class RttJitterCalculator(
    private val maxSamples: Int = 30
) {
    private val rttSamples = ArrayDeque<Int>()
    private val packetTimestampsMs = ArrayDeque<Long>()
    private var previousRttMs: Int? = null
    private var smoothedJitterMs: Double = 0.0
    private var lastSampleTimeMs: Long = 0L

    @Synchronized
    fun reset() {
        rttSamples.clear()
        packetTimestampsMs.clear()
        previousRttMs = null
        smoothedJitterMs = 0.0
        lastSampleTimeMs = 0L
    }

    @Synchronized
    fun recordPacketEvent(nowMs: Long = System.currentTimeMillis()) {
        packetTimestampsMs.addLast(nowMs)
        prunePacketWindow(nowMs)
    }

    @Synchronized
    fun recordRttSample(rttMs: Int, nowMs: Long = System.currentTimeMillis()): RttStatsSnapshot {
        val clamped = rttMs.coerceIn(1, 9999)
        val prev = previousRttMs
        if (prev != null) {
            val delta = abs(clamped - prev).toDouble()
            // RFC 3550 smoothed jitter estimator: J(i) = J(i-1) + (|D(i)| - J(i-1)) / 16
            smoothedJitterMs += (delta - smoothedJitterMs) / 16.0
        }
        previousRttMs = clamped
        lastSampleTimeMs = nowMs

        if (rttSamples.size >= maxSamples) {
            rttSamples.removeFirst()
        }
        rttSamples.addLast(clamped)
        recordPacketEvent(nowMs)
        return snapshot(nowMs)
    }

    @Synchronized
    fun snapshot(nowMs: Long = System.currentTimeMillis()): RttStatsSnapshot {
        prunePacketWindow(nowMs)
        val hz = packetTimestampsMs.size / 5.0f
        if (rttSamples.isEmpty()) {
            return RttStatsSnapshot(packetRateHz = hz)
        }
        val list = rttSamples.toList()
        val sorted = list.sorted()
        val p95Idx = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return RttStatsSnapshot(
            currentRttMs = list.last(),
            avgRttMs = list.average().roundToInt(),
            minRttMs = sorted.first(),
            p95RttMs = sorted[p95Idx],
            jitterMs = if (list.size >= 2) smoothedJitterMs.roundToInt() else 0,
            packetRateHz = hz,
            historyMs = list,
            lastMeasuredAtMs = lastSampleTimeMs
        )
    }

    private fun prunePacketWindow(nowMs: Long) {
        val cutoff = nowMs - 5000L
        while (packetTimestampsMs.isNotEmpty() && packetTimestampsMs.first() < cutoff) {
            packetTimestampsMs.removeFirst()
        }
    }
}
