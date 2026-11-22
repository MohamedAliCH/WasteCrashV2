package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.*
import com.example.ui.theme.*
import java.util.Locale

private val FiveGCyan = Color(0xFF22D3EE)

@Composable
fun bearerAccentColor(bearer: RadioBearer): Color = when (bearer) {
    RadioBearer.FIVE_G_SA -> LimePlayTop
    RadioBearer.FIVE_G_NSA -> FiveGCyan
    RadioBearer.LTE_4G -> SaffronGold
    RadioBearer.WIFI -> CeramicBlueTop
    RadioBearer.CELLULAR_LEGACY -> Color(0xFFFFB74D)
    RadioBearer.OFFLINE -> CarthageCoral
}

@Composable
fun latencyAccentColor(rttMs: Int?): Color = when {
    rttMs == null -> TextMutedOnDark
    rttMs <= 30 -> LimePlayTop
    rttMs <= 80 -> SaffronGold
    else -> CarthageCoral
}

/**
 * Compact clickable network status pill for HomeScreen and Matchmaking headers.
 * Displays real live radio bearer (5G SA / 5G NSA / 4G LTE / Wi-Fi) and 5G slice status.
 */
@Composable
fun HomeNetworkBadge(
    link: RadioLinkSnapshot,
    hasEdgeUrl: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = bearerAccentColor(link.bearer)
    val sliceActive = link.sliceStatus == SliceStatus.ACTIVE
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("home_5g_network_badge"),
        shape = RoundedCornerShape(50),
        color = DeepNavyInk.copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, if (sliceActive) LimePlayTop else accent.copy(alpha = 0.75f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = link.bearer.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp),
                color = accent,
                maxLines = 1
            )
            if (sliceActive) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = EcoEmerald.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, LimePlayTop)
                ) {
                    Text(
                        text = "SLICE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
                        color = LimePlayTop,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            } else if (hasEdgeUrl) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = FiveGCyan.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, FiveGCyan)
                ) {
                    Text(
                        text = "EDGE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.sp),
                        color = FiveGCyan,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Ultra-compact real-time 5G SA + RTT/Jitter telemetry strip for BattleScreen.
 * Tap to toggle the detailed live RTT sparkline card.
 */
@Composable
fun BattleTelemetryStrip(
    telemetry: LatencyTelemetryState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bearerColor = bearerAccentColor(telemetry.bearer)
    val rttColor = latencyAccentColor(telemetry.currentRttMs)
    val sliceActive = telemetry.sliceStatus == SliceStatus.ACTIVE

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleExpand)
            .testTag("battle_5g_telemetry_strip"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF07132A).copy(alpha = 0.94f),
        border = BorderStroke(
            1.2.dp,
            if (sliceActive || telemetry.bearer == RadioBearer.FIVE_G_SA) LimePlayTop.copy(alpha = 0.85f)
            else CeramicBlueTop.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Radio Bearer + Slice Pill + Transport
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (telemetry.bearer == RadioBearer.WIFI) Icons.Default.Wifi else Icons.Default.CellTower,
                    contentDescription = "Network bearer",
                    tint = bearerColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = telemetry.bearer.shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                    color = bearerColor,
                    maxLines = 1
                )
                TelemetryMiniChip(
                    text = telemetry.sliceStatus.shortLabel,
                    tint = if (sliceActive) LimePlayTop else TextMutedOnDark
                )
                TelemetryMiniChip(
                    text = telemetry.transportMode.shortLabel,
                    tint = if (telemetry.transportMode == SyncTransportMode.EDGE_WEBSOCKET) FiveGCyan else SidiSkyLight
                )
            }

            // Right: Live RTT + Jitter + Expand indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "RTT ${telemetry.currentRttMs?.let { "${it}ms" } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                    color = rttColor,
                    maxLines = 1
                )
                Text(
                    text = "JIT ${telemetry.jitterMs?.let { "${it}ms" } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = SidiSkyLight,
                    maxLines = 1
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.Insights,
                    contentDescription = "Toggle latency chart",
                    tint = SaffronGold,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryMiniChip(
    text: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(0.8.dp, tint.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * Floating live RTT sparkline & 5G SA slice diagnostics card shown on top of the upper battlefield
 * when the player taps [BattleTelemetryStrip].
 */
@Composable
fun LiveLatencySparklineCard(
    telemetry: LatencyTelemetryState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bearerColor = bearerAccentColor(telemetry.bearer)
    val rttColor = latencyAccentColor(telemetry.currentRttMs)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_rtt_sparkline_card"),
        shape = RoundedCornerShape(16.dp),
        color = DeepNavyInk.copy(alpha = 0.96f),
        border = BorderStroke(1.8.dp, if (telemetry.isLowLatencyReady) LimePlayTop else SaffronGold),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = bearerColor, modifier = Modifier.size(16.dp))
                    Text(
                        text = "${telemetry.bearer.detailLabel} • ${telemetry.edgeServerStatus}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close telemetry", tint = SidiSkyLight, modifier = Modifier.size(16.dp))
                }
            }

            // Live RTT Sparkline (Canvas sized dynamically via DrawScope)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF040B1A))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (telemetry.rttHistoryMs.size < 2) {
                    Text(
                        text = "Measuring live round-trip packets...",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedOnDark,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val samples = telemetry.rttHistoryMs
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val maxVal = (samples.maxOrNull() ?: 100).coerceAtLeast(40).toFloat()
                        val minVal = 0f

                        // 30ms ultra-low-latency reference line
                        val ref30Y = h - ((30f / maxVal).coerceIn(0f, 1f) * h)
                        drawLine(
                            color = LimePlayTop.copy(alpha = 0.25f),
                            start = Offset(0f, ref30Y),
                            end = Offset(w, ref30Y),
                            strokeWidth = 1.5f
                        )

                        val path = Path()
                        val stepX = w / (samples.size - 1).coerceAtLeast(1)
                        samples.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val norm = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                            val y = h - (norm * (h - 6f)) - 3f
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(FiveGCyan, rttColor)),
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Metric Grid Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell("RTT", telemetry.currentRttMs?.let { "${it}ms" } ?: "--", rttColor)
                MetricCell("AVG", telemetry.avgRttMs?.let { "${it}ms" } ?: "--", Color.White)
                MetricCell("P95", telemetry.p95RttMs?.let { "${it}ms" } ?: "--", SaffronGold)
                MetricCell("JITTER", telemetry.jitterMs?.let { "${it}ms" } ?: "--", FiveGCyan)
                MetricCell("RATE", String.format(Locale.US, "%.1fHz", telemetry.packetRateHz), SidiSkyLight)
            }

            // Link capability footer
            val downMbps = telemetry.downstreamKbps / 1000
            val upMbps = telemetry.upstreamKbps / 1000
            val carrierPart = telemetry.carrierName.takeIf { it.isNotBlank() }?.let { "$it • " } ?: ""
            Text(
                text = "${carrierPart}${telemetry.sliceStatus.detailLabel} • ↓${downMbps} / ↑${upMbps} Mbps",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextMutedOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            color = TextMutedOnDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp),
            color = color
        )
    }
}
