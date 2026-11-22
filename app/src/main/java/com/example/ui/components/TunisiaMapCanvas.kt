package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameCatalog
import com.example.model.TunisianPlace
import com.example.model.TunisiaGeo
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.min

/**
 * Interactive geographic Map of Tunisia rendered on a luxury dark Mediterranean chart.
 * The country is drawn from its real outline (TunisiaGeo, Natural Earth); pins use the same
 * projection, so (mapX, mapY) are TunisiaGeo.project(lon, lat) of each place.
 */
@Composable
fun TunisiaMapCanvas(
    places: List<TunisianPlace>,
    selectedPlaceId: String?,
    userTrophies: Int,
    purifiedPlaceIds: Set<String> = emptySet(),
    onPlaceSelected: (TunisianPlace) -> Unit,
    modifier: Modifier = Modifier,
    previewMode: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tapRadiusPx = with(density) { 44.dp.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "tunisiaMapPulse")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )

    var lastSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!previewMode) {
                        Modifier.pointerInput(places, lastSize) {
                            detectTapGestures { offset ->
                                val bounds = computeMapBounds(lastSize, previewMode)
                                var bestPlace: TunisianPlace? = null
                                var bestDist = Float.MAX_VALUE
                                for (place in places) {
                                    val px = bounds.left + place.mapX * bounds.width
                                    val py = bounds.top + place.mapY * bounds.height
                                    val dist = hypot(offset.x - px, offset.y - py)
                                    if (dist <= tapRadiusPx && dist < bestDist) {
                                        bestDist = dist
                                        bestPlace = place
                                    }
                                }
                                bestPlace?.let(onPlaceSelected)
                            }
                        }
                    } else Modifier
                )
        ) {
            lastSize = size
            val w = size.width
            val h = size.height
            val bounds = computeMapBounds(size, previewMode)

            // 1. Deep Mediterranean Ocean Marine Chart Backdrop
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0E2958), Color(0xFF071633), Color(0xFF030A18)),
                    center = Offset(w * 0.52f, h * 0.42f),
                    radius = maxOf(w, h) * 0.85f
                )
            )

            // Subtle luxury marine-chart latitude & longitude grid lines
            val gridColor = CeramicBlueTop.copy(alpha = 0.09f)
            val cols = 8
            val rows = 8
            for (i in 1 until cols) {
                val x = w * i / cols.toFloat()
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            }
            for (j in 1 until rows) {
                val y = h * j / rows.toFloat()
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            // 2. Real outline of Tunisia (mainland + Djerba + Kerkennah)
            val tunisiaPath = buildTunisiaPath(bounds)

            // Coastal turquoise Mediterranean outer glow around Tunisia
            drawPath(
                path = tunisiaPath,
                color = CeramicBlueTop.copy(alpha = 0.18f),
                style = Stroke(width = 14.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
            )

            // 3-zone terrain fill (Northern Coast -> Central Heritage -> Saharan South)
            drawPath(
                path = tunisiaPath,
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFF14536F),
                        0.28f to Color(0xFF1A5B5A),
                        0.52f to Color(0xFF5B4526),
                        0.76f to Color(0xFF7A3F1A),
                        1.00f to Color(0xFF5E2B12)
                    ),
                    startY = bounds.top,
                    endY = bounds.top + bounds.height
                )
            )

            // Chott el-Djerid salt lake shimmer, at its real position
            val (chottX, chottY) = TunisiaGeo.project(8.45f, 33.78f)
            drawOval(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF5EEAD4).copy(alpha = 0.25f), Color(0xFFE0F2FE).copy(alpha = 0.35f))
                ),
                topLeft = Offset(bounds.left + (chottX - 0.11f) * bounds.width, bounds.top + (chottY - 0.02f) * bounds.height),
                size = Size(bounds.width * 0.22f, bounds.height * 0.04f)
            )

            // Metallic gold Tunisia border outline
            drawPath(
                path = tunisiaPath,
                brush = Brush.verticalGradient(listOf(GlowBeamGold, SaffronGold, SaffronGoldDark)),
                style = Stroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
            )

            // 3. Landmark Pins
            val currentArena = GameCatalog.arenaForTrophies(userTrophies)
            val previewLabelIds = setOf("place_hammamet", "place_djerba", "place_tozeur")

            // Draw unselected pins first, then selected pin on top
            val orderedPlaces = places.sortedBy { it.id == selectedPlaceId }
            orderedPlaces.forEach { place ->
                val unlocked = place.id in purifiedPlaceIds
                val isSelected = place.id == selectedPlaceId
                val isPreviewPulse = previewMode && (unlocked || place.arenaId == currentArena.id)

                val pinColor = when (place.arenaId) {
                    0 -> CeramicBlueTop
                    1 -> SaffronGold
                    else -> GlowBeamOrange
                }
                val center = Offset(
                    x = bounds.left + place.mapX * bounds.width,
                    y = bounds.top + place.mapY * bounds.height
                )

                // Pulsing halo for selected pin or current arena pins in preview
                if (isSelected || isPreviewPulse) {
                    val baseR = if (isSelected) 12.dp.toPx() else 8.dp.toPx()
                    drawCircle(
                        color = pinColor.copy(alpha = haloAlpha),
                        radius = baseR * haloScale,
                        center = center
                    )
                }

                val pinRadius = when {
                    isSelected -> 9.5.dp.toPx()
                    previewMode -> 5.5.dp.toPx()
                    else -> 7.dp.toPx()
                }

                // Outer glow ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = pinRadius + 2.dp.toPx(),
                    center = Offset(center.x, center.y + 1.5.dp.toPx())
                )
                drawCircle(
                    color = if (isSelected) Color.White else if (unlocked) Color.White.copy(alpha = 0.9f) else TextMutedOnDark.copy(alpha = 0.55f),
                    radius = pinRadius + if (isSelected) 2.2.dp.toPx() else 1.4.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = if (unlocked) pinColor else Color(0xFF475569),
                    radius = pinRadius,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = pinRadius * 0.35f,
                    center = Offset(center.x - pinRadius * 0.25f, center.y - pinRadius * 0.25f)
                )

                // Lock dot on locked arenas
                if (!unlocked && !previewMode) {
                    drawCircle(
                        color = DeepNavyInk,
                        radius = 3.2.dp.toPx(),
                        center = Offset(center.x + pinRadius * 0.7f, center.y - pinRadius * 0.7f)
                    )
                    drawCircle(
                        color = SaffronGold,
                        radius = 2.dp.toPx(),
                        center = Offset(center.x + pinRadius * 0.7f, center.y - pinRadius * 0.7f)
                    )
                }

                // City pill label on selected pin or select landmarks in preview
                val showLabel = isSelected || (previewMode && place.id in previewLabelIds)
                if (showLabel) {
                    drawCityLabelPill(
                        textMeasurer = textMeasurer,
                        label = place.name,
                        pinCenter = center,
                        pinRadius = pinRadius,
                        highlight = isSelected,
                        accentColor = pinColor,
                        canvasWidth = w
                    )
                }
            }
        }
    }
}

private data class MapBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun computeMapBounds(canvasSize: Size, previewMode: Boolean = false): MapBounds {
    val paddingH = canvasSize.width * 0.08f
    // Home preview: keep the north coast clear of the wordmark drawn over the top of the card.
    val paddingTop = canvasSize.height * if (previewMode) 0.21f else 0.07f
    val paddingBottom = canvasSize.height * if (previewMode) 0.02f else 0.07f
    val availW = (canvasSize.width - paddingH * 2f).coerceAtLeast(1f)
    val availH = (canvasSize.height - paddingTop - paddingBottom).coerceAtLeast(1f)
    // Aspect of the projected map window (real proportions of the country)
    val aspect = TunisiaGeo.ASPECT
    val boxH = min(availH, availW / aspect)
    val boxW = boxH * aspect
    val left = (canvasSize.width - boxW) * 0.5f
    val top = paddingTop + (availH - boxH) * 0.5f
    return MapBounds(left, top, boxW, boxH)
}

private fun buildTunisiaPath(b: MapBounds): Path = Path().apply {
    for (ring in TunisiaGeo.RINGS) {
        var i = 0
        while (i + 1 < ring.size) {
            val (nx, ny) = TunisiaGeo.project(ring[i], ring[i + 1])
            val x = b.left + nx * b.width
            val y = b.top + ny * b.height
            if (i == 0) moveTo(x, y) else lineTo(x, y)
            i += 2
        }
        close()
    }
}

private fun DrawScope.drawCityLabelPill(
    textMeasurer: TextMeasurer,
    label: String,
    pinCenter: Offset,
    pinRadius: Float,
    highlight: Boolean,
    accentColor: Color,
    canvasWidth: Float
) {
    val style = TextStyle(
        color = if (highlight) DeepNavyInk else TextOnDark,
        fontSize = if (highlight) 10.sp else 8.5.sp,
        fontWeight = FontWeight.ExtraBold
    )
    val measured = textMeasurer.measure(label, style)
    val padH = 6.dp.toPx()
    val padV = 2.5.dp.toPx()
    val pillW = measured.size.width + padH * 2f
    val pillH = measured.size.height + padV * 2f

    val rawLeft = pinCenter.x - pillW / 2f
    val pillLeft = rawLeft.coerceIn(6.dp.toPx(), (canvasWidth - pillW - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
    val pillTop = (pinCenter.y - pinRadius - pillH - 4.dp.toPx()).coerceAtLeast(4.dp.toPx())

    drawRoundRect(
        color = if (highlight) SaffronGold else Color(0xFF0A1732).copy(alpha = 0.88f),
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f)
    )
    drawRoundRect(
        color = if (highlight) Color.White else accentColor.copy(alpha = 0.85f),
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillH / 2f, pillH / 2f),
        style = Stroke(width = 1.2.dp.toPx())
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(pillLeft + padH, pillTop + padV)
    )
}
