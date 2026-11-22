package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.example.engine.BOARD_SIZE
import com.example.engine.DamageProjectileVolley
import com.example.ui.theme.BossHpRedTop
import com.example.ui.theme.DeepNavyInk
import com.example.ui.theme.EcoEmeraldBright
import com.example.ui.theme.GlowBeamCore
import com.example.ui.theme.GlowBeamGold
import com.example.ui.theme.SaffronGold
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private class PrecomputedBolt(
    val startX: Float,
    val startY: Float,
    val ctrlX: Float,
    val ctrlY: Float,
    val color: Color
)

private class ActiveVolleyAnim(
    val volley: DamageProjectileVolley,
    val bolts: Array<PrecomputedBolt>,
    val avgStartX: Float,
    val avgStartY: Float,
    val targetX: Float,
    val targetY: Float,
    val accentColor: Color,
    val pillBgColor: Color,
    val pillTextColor: Int,
    val label: String,
    val isBigLabel: Boolean,
    val driftX: Float,
    val rayCos: FloatArray,
    val raySin: FloatArray,
    val progress: Animatable<Float, AnimationVector1D>
)

/**
 * Locked-60fps zero-recomposition combat VFX overlay.
 *
 * Performance guarantees:
 * - Single `Canvas` with ZERO child composables and ZERO `Surface` shadow rebuilds.
 * - Never reads `Animatable.value` inside Composition — only inside `Canvas.onDraw`.
 * - Zero `Brush` / Shader allocations inside `onDraw` (uses layered solid alpha `Color` draws).
 * - Pre-computes Bézier control points, trig tables, and damage labels once per volley.
 * - Renders crisp floating damage pills directly via `nativeCanvas` with cached `Paint` objects.
 */
@Composable
fun BattleVfxOverlay(
    volleys: List<DamageProjectileVolley>,
    boardBoundsInRoot: Rect,
    targetCenterInRoot: Offset,
    rootOriginInRoot: Offset,
    onVolleyImpact: (DamageProjectileVolley) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = remember { mutableStateListOf<ActiveVolleyAnim>() }
    val seenIds = remember { mutableSetOf<Long>() }
    val density = LocalDensity.current

    // Cached Android Paint instances (allocated once, reused across every frame at 0 GC overhead)
    val smallTextPaint = remember(density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * density.density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val bigTextPaint = remember(density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.5f * density.density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    LaunchedEffect(volleys) {
        if (boardBoundsInRoot.isEmpty || targetCenterInRoot == Offset.Unspecified) return@LaunchedEffect
        val cellW = boardBoundsInRoot.width / BOARD_SIZE.toFloat()
        val cellH = boardBoundsInRoot.height / BOARD_SIZE.toFloat()
        val localTargetX = targetCenterInRoot.x - rootOriginInRoot.x
        val localTargetY = targetCenterInRoot.y - rootOriginInRoot.y
        val boardLeft = boardBoundsInRoot.left - rootOriginInRoot.x
        val boardTop = boardBoundsInRoot.top - rootOriginInRoot.y

        for (v in volleys) {
            if (!seenIds.add(v.id)) continue
            if (seenIds.size > 48) {
                val keep = volleys.map { it.id }.toSet()
                seenIds.retainAll(keep)
            }

            // Cap concurrent rendered volleys to 4 so bursts stay locked at 60fps even on mid-range GPUs
            while (active.size >= 4) {
                active.removeAt(0)
            }

            val rawOrigins = v.origins.take(5)
            val boltList = if (rawOrigins.isNotEmpty()) {
                val count = rawOrigins.size
                Array(count) { idx ->
                    val (pos, waste) = rawOrigins[idx]
                    val sx = boardLeft + (pos.col + 0.5f) * cellW
                    val sy = boardTop + (pos.row + 0.5f) * cellH
                    val spread = (idx - (count - 1) * 0.5f) * 38f
                    PrecomputedBolt(
                        startX = sx,
                        startY = sy,
                        ctrlX = (sx + localTargetX) * 0.5f + spread,
                        ctrlY = (sy + localTargetY) * 0.5f - 22f,
                        color = waste.primaryColor
                    )
                }
            } else {
                val cx = boardLeft + boardBoundsInRoot.width * 0.5f
                val cy = boardTop + boardBoundsInRoot.height * 0.5f
                arrayOf(
                    PrecomputedBolt(
                        startX = cx,
                        startY = cy,
                        ctrlX = (cx + localTargetX) * 0.5f,
                        ctrlY = (cy + localTargetY) * 0.5f - 22f,
                        color = v.abilityWasteType?.primaryColor ?: GlowBeamGold
                    )
                )
            }

            var sumX = 0f
            var sumY = 0f
            for (b in boltList) {
                sumX += b.startX
                sumY += b.startY
            }
            val avgX = sumX / boltList.size
            val avgY = sumY / boltList.size

            val accent = v.abilityWasteType?.primaryColor ?: boltList[0].color
            val label = when {
                v.isAbility -> "SKILL -${v.damageAmount}!"
                v.comboIndex >= 3 -> "${v.comboIndex}x -${v.damageAmount}!"
                v.isWeakHit -> "WEAK -${v.damageAmount}!"
                v.isCritical -> "CRIT -${v.damageAmount}!"
                else -> "-${v.damageAmount}"
            }
            val pillBg = when {
                v.isAbility -> EcoEmeraldBright
                v.isWeakHit -> SaffronGold
                v.isCritical -> BossHpRedTop
                else -> DeepNavyInk
            }
            val pillTxt = (if (v.isWeakHit || v.isAbility) DeepNavyInk else Color.White).toArgb()

            val rayCount = if (v.isAbility) 10 else 6
            val angleOffset = (v.id % 5) * 0.25
            val rayCos = FloatArray(rayCount) { i ->
                cos(i * (2.0 * PI / rayCount) + angleOffset).toFloat()
            }
            val raySin = FloatArray(rayCount) { i ->
                sin(i * (2.0 * PI / rayCount) + angleOffset).toFloat()
            }

            val anim = Animatable(0f)
            val entry = ActiveVolleyAnim(
                volley = v,
                bolts = boltList,
                avgStartX = avgX,
                avgStartY = avgY,
                targetX = localTargetX,
                targetY = localTargetY,
                accentColor = accent,
                pillBgColor = pillBg,
                pillTextColor = pillTxt,
                label = label,
                isBigLabel = v.isAbility || v.isCritical,
                driftX = ((v.id % 3) - 1) * 24f * density.density,
                rayCos = rayCos,
                raySin = raySin,
                progress = anim
            )
            active.add(entry)

            launch {
                try {
                    // Phase 1: snappy flight from matched tiles into the target mob (185ms)
                    anim.animateTo(0.44f, tween(durationMillis = 185, easing = LinearEasing))
                    onVolleyImpact(v)
                    // Phase 2: impact shockwave + sparks + rising damage pill (320ms)
                    anim.animateTo(1f, tween(durationMillis = 320, easing = LinearEasing))
                } finally {
                    active.remove(entry)
                }
            }
        }
    }

    if (active.isEmpty()) return

    val strokeThin = 1.6f * density.density
    val pillPadH = 10f * density.density
    val pillHSmall = 22f * density.density
    val pillHBig = 25f * density.density

    Canvas(modifier = modifier.fillMaxSize()) {
        val count = active.size
        for (eIdx in 0 until count) {
            val entry = active[eIdx]
            val p = entry.progress.value
            val tx = entry.targetX
            val ty = entry.targetY
            val target = Offset(tx, ty)
            val volley = entry.volley
            val accent = entry.accentColor

            // 1. Flight phase (0.0 -> 0.50) — zero Shader/Brush allocations!
            if (p < 0.50f) {
                val flightRaw = (p / 0.44f).coerceIn(0f, 1f)
                val u = flightRaw * flightRaw * (3f - 2f * flightRaw)
                val fadeOut = if (p > 0.44f) (1f - (p - 0.44f) / 0.06f).coerceIn(0f, 1f) else 1f

                if (volley.isAbility) {
                    val ox = entry.avgStartX
                    val oy = entry.avgStartY
                    val tipX = ox + (tx - ox) * u
                    val tipY = oy + (ty - oy) * u
                    val origin = Offset(ox, oy)
                    val tip = Offset(tipX, tipY)
                    drawLine(
                        color = accent.copy(alpha = 0.45f * fadeOut),
                        start = origin,
                        end = tip,
                        strokeWidth = 22f * fadeOut + 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GlowBeamGold.copy(alpha = 0.88f * fadeOut),
                        start = origin,
                        end = tip,
                        strokeWidth = 12f * fadeOut + 2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GlowBeamCore.copy(alpha = 0.96f * fadeOut),
                        start = origin,
                        end = tip,
                        strokeWidth = 5f * fadeOut + 1.5f,
                        cap = StrokeCap.Round
                    )
                }

                val tailU1 = (u - 0.14f).coerceAtLeast(0f)
                val tailU2 = (u - 0.28f).coerceAtLeast(0f)
                val headR = if (volley.isCritical || volley.isAbility) 13.5f else 10.5f
                val wTrail1 = if (volley.isCritical) 9f else 6.5f
                val wTrail2 = if (volley.isCritical) 12.5f else 9f

                val bolts = entry.bolts
                for (bIdx in bolts.indices) {
                    val b = bolts[bIdx]
                    val px = quadBezier1D(b.startX, b.ctrlX, tx, u)
                    val py = quadBezier1D(b.startY, b.ctrlY, ty, u)
                    val t1x = quadBezier1D(b.startX, b.ctrlX, tx, tailU1)
                    val t1y = quadBezier1D(b.startY, b.ctrlY, ty, tailU1)
                    val t2x = quadBezier1D(b.startX, b.ctrlX, tx, tailU2)
                    val t2y = quadBezier1D(b.startY, b.ctrlY, ty, tailU2)

                    val headPos = Offset(px, py)
                    val t1Pos = Offset(t1x, t1y)
                    val t2Pos = Offset(t2x, t2y)

                    drawLine(
                        color = b.color.copy(alpha = 0.38f * fadeOut),
                        start = t2Pos,
                        end = t1Pos,
                        strokeWidth = wTrail1,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GlowBeamGold.copy(alpha = 0.85f * fadeOut),
                        start = t1Pos,
                        end = headPos,
                        strokeWidth = wTrail2,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = b.color.copy(alpha = 0.50f * fadeOut),
                        radius = headR * 1.4f,
                        center = headPos
                    )
                    drawCircle(
                        color = GlowBeamGold.copy(alpha = 0.92f * fadeOut),
                        radius = headR,
                        center = headPos
                    )
                    drawCircle(
                        color = GlowBeamCore.copy(alpha = fadeOut),
                        radius = headR * 0.5f,
                        center = headPos
                    )
                }
            }

            // 2. Impact shockwave, flash, sparks & Canvas-drawn damage pill (0.43 -> 1.0)
            if (p >= 0.43f) {
                val imp = ((p - 0.43f) / 0.57f).coerceIn(0f, 1f)
                val easeOut = 1f - (1f - imp).pow(3)
                val inv = (1f - imp).coerceIn(0f, 1f)
                val scaleBoost = if (volley.isAbility) 1.38f else if (volley.isCritical || volley.isWeakHit) 1.18f else 1.0f

                // Expanding shockwave ring
                val ringRadius = (20f + 102f * easeOut) * scaleBoost
                drawCircle(
                    color = accent.copy(alpha = 0.82f * inv),
                    radius = ringRadius,
                    center = target,
                    style = Stroke(width = 8.5f * inv + 1.5f)
                )
                if (volley.isCritical || volley.isAbility) {
                    drawCircle(
                        color = GlowBeamGold.copy(alpha = 0.72f * inv),
                        radius = ringRadius * 0.72f,
                        center = target,
                        style = Stroke(width = 5f * inv + 1f)
                    )
                }

                // Crisp core impact flash (zero Brush.radialGradient allocation!)
                if (imp < 0.36f) {
                    val flash = ((0.36f - imp) / 0.36f).coerceIn(0f, 1f)
                    drawCircle(
                        color = GlowBeamGold.copy(alpha = 0.38f * flash),
                        radius = 62f * scaleBoost,
                        center = target
                    )
                    drawCircle(
                        color = GlowBeamCore.copy(alpha = 0.82f * flash),
                        radius = 30f * scaleBoost,
                        center = target
                    )
                }

                // Radial impact sparks using pre-computed cos/sin tables
                val innerD = (12f + 30f * easeOut) * scaleBoost
                val outerD = (26f + 82f * easeOut) * scaleBoost
                val rayW = 5.5f * inv + 1.2f
                val sparkR = 3.8f * inv + 1f
                val cosArr = entry.rayCos
                val sinArr = entry.raySin
                for (i in cosArr.indices) {
                    val c = cosArr[i]
                    val s = sinArr[i]
                    val pStart = Offset(tx + c * innerD, ty + s * innerD)
                    val pEnd = Offset(tx + c * outerD, ty + s * outerD)
                    drawLine(
                        color = (if (i % 2 == 0) GlowBeamGold else accent).copy(alpha = inv),
                        start = pStart,
                        end = pEnd,
                        strokeWidth = rayW,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = GlowBeamCore.copy(alpha = inv),
                        radius = sparkR,
                        center = pEnd
                    )
                }

                // Floating Damage Number Pill rendered directly on the Canvas (zero recomposition!)
                if (volley.damageAmount > 0) {
                    val alphaVal = if (imp < 0.65f) 1f else (1f - (imp - 0.65f) / 0.35f).coerceIn(0f, 1f)
                    val popScale = when {
                        imp < 0.20f -> 0.65f + (imp / 0.20f) * 0.50f
                        imp < 0.36f -> 1.15f - ((imp - 0.20f) / 0.16f) * 0.15f
                        else -> 1.0f
                    }
                    val cx = tx + entry.driftX
                    val cy = ty - (24f + imp * 72f) * density.density
                    val paint = if (entry.isBigLabel) bigTextPaint else smallTextPaint
                    val basePillH = if (entry.isBigLabel) pillHBig else pillHSmall
                    val textW = paint.measureText(entry.label)
                    val pillW = (textW + pillPadH * 2f) * popScale
                    val pillH = basePillH * popScale
                    val topLeft = Offset(cx - pillW * 0.5f, cy - pillH * 0.5f)
                    val pillSize = Size(pillW, pillH)
                    val radius = CornerRadius(pillH * 0.5f, pillH * 0.5f)

                    drawRoundRect(
                        color = entry.pillBgColor.copy(alpha = 0.94f * alphaVal),
                        topLeft = topLeft,
                        size = pillSize,
                        cornerRadius = radius
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f * alphaVal),
                        topLeft = topLeft,
                        size = pillSize,
                        cornerRadius = radius,
                        style = Stroke(width = strokeThin)
                    )

                    paint.color = entry.pillTextColor
                    paint.alpha = (alphaVal * 255f).toInt().coerceIn(0, 255)
                    val baseline = cy - (paint.descent() + paint.ascent()) * 0.5f
                    drawContext.canvas.nativeCanvas.drawText(entry.label, cx, baseline, paint)
                }
            }
        }
    }
}

private inline fun quadBezier1D(p0: Float, p1: Float, p2: Float, t: Float): Float {
    val inv = 1f - t
    return inv * inv * p0 + 2f * inv * t * p1 + t * t * p2
}
