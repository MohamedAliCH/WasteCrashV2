package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BattleRules
import com.example.engine.MobState
import com.example.model.BossDefinition
import com.example.model.GameCatalog
import com.example.model.TunisianPlace
import com.example.ui.theme.*
import kotlin.random.Random

/**
 * The place being fought over, reacting to the fight (team "Tounes Nadhifa" style):
 * - while polluted: cartoon version of the place (or a desaturated photo) under a litter layer;
 * - litter / smog fade out as [pollution] drops with every hit;
 * - the real photo is revealed once the place is purified (Boss beaten).
 */
@Composable
fun EnvironmentScene(
    place: TunisianPlace,
    pollution: Float,
    purified: Boolean,
    modifier: Modifier = Modifier
) {
    val animPollution by animateFloatAsState(pollution.coerceIn(0f, 1f), tween(450), label = "pollution")
    val reveal by animateFloatAsState(if (purified) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "reveal")

    Box(modifier = modifier.testTag("environment_scene")) {
        // Real photo (always underneath, fully visible once purified).
        Image(
            painter = painterResource(place.imageRes),
            contentDescription = place.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Polluted look on top, cross-faded away on purification via GPU graphicsLayer.
        if (reveal < 1f) {
            val toon = place.toonRes
            if (toon != null) {
                Image(
                    painter = painterResource(toon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - reveal }
                )
            } else {
                // No cartoon art: the photo itself loses its colours as pollution rises.
                val matrix = remember(animPollution) {
                    ColorMatrix().apply { setToSaturation(1f - 0.75f * animPollution) }
                }
                Image(
                    painter = painterResource(place.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(matrix),
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - reveal }
                )
            }
            val litterAlpha = (animPollution * (1f - reveal)).coerceIn(0f, 1f)
            val dirty = place.dirtyRes
            if (dirty != null) {
                Image(
                    painter = painterResource(dirty),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = (litterAlpha * 0.95f).coerceIn(0f, 0.95f) }
                )
            } else {
                LitterLayer(pollution = litterAlpha, seed = place.id.hashCode(), modifier = Modifier.fillMaxSize())
            }
            // Brown smog haze
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2E271E).copy(alpha = 0.45f * litterAlpha),
                                Color(0xFF42372A).copy(alpha = 0.25f * litterAlpha),
                                Color(0xFF211C15).copy(alpha = 0.40f * litterAlpha)
                            )
                        )
                    )
            )
        }
    }
}

/** Procedural litter (bottles, cans, bags) scattered over places that have no painted overlay. */
@Composable
private fun LitterLayer(pollution: Float, seed: Int, modifier: Modifier = Modifier) {
    data class Piece(val x: Float, val y: Float, val w: Float, val h: Float, val rot: Float, val color: Color, val threshold: Float)
    val pieces = remember(seed) {
        val rng = Random(seed)
        val palette = listOf(
            Color(0xFF3D8FE0), Color(0xFF2F9E44), Color(0xFFF0B92A), Color(0xFFD8453B),
            Color(0xFFC98A52), Color(0xFFE5E7EB), Color(0xFF6B7280)
        )
        List(34) {
            Piece(
                x = rng.nextFloat(),
                y = 0.45f + rng.nextFloat() * 0.55f, // litter lies on the ground half of the scene
                w = 0.018f + rng.nextFloat() * 0.022f,
                h = 0.008f + rng.nextFloat() * 0.010f,
                rot = rng.nextFloat() * 180f,
                color = palette[rng.nextInt(palette.size)],
                threshold = rng.nextFloat()
            )
        }
    }
    Canvas(modifier = modifier) {
        pieces.forEach { p ->
            // Each piece disappears once pollution drops under its own threshold, so the
            // place visibly gets cleaner piece by piece instead of just fading.
            if (pollution <= p.threshold * 0.95f) return@forEach
            val cx = p.x * size.width
            val cy = p.y * size.height
            val w = p.w * size.width
            val h = p.h * size.height * 2.2f
            rotate(p.rot, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = Color(0xFF3B2A1A).copy(alpha = 0.85f),
                    topLeft = Offset(cx - w / 2 - 1.5f, cy - h / 2 - 1.5f),
                    size = Size(w + 3f, h + 3f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                )
                drawRoundRect(
                    color = p.color,
                    topLeft = Offset(cx - w / 2, cy - h / 2),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                )
            }
        }
    }
}

/**
 * Waves 1-4: the mini trash monsters of the current wave in a row (target first, defeated ones
 * greyed out). Wave 5: the shared Boss (with ENRAGED shield under 35% HP).
 */
@Composable
fun EnemyStage(
    waveIndex: Int,
    mobs: List<MobState>,
    boss: BossDefinition,
    bossHp: Int,
    bossMaxHp: Int,
    hitFlash: Boolean,
    hitToken: Long = 0L,
    defeatedToken: Long,
    onTargetCenterChanged: ((Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBossStage = waveIndex >= BattleRules.BOSS_WAVE_INDEX
    val isEnraged = BattleRules.isBossEnraged(waveIndex, bossHp, bossMaxHp)
    val target = mobs.firstOrNull { it.alive }

    // Green "purified" puff when a monster falls
    val puff = remember { Animatable(0f) }
    LaunchedEffect(defeatedToken) {
        if (defeatedToken > 0L) {
            puff.snapTo(0f)
            puff.animateTo(1f, tween(460))
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (isBossStage) {
                BossMonsterAvatar(
                    boss = boss,
                    hitFlash = hitFlash,
                    hitToken = hitToken,
                    modifier = Modifier
                        .size(116.dp)
                        .onGloballyPositioned { coords ->
                            onTargetCenterChanged?.invoke(coords.boundsInRoot().center)
                        }
                )
            } else {
                val denseWave = mobs.size >= 4
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (denseWave) 2.dp else 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    mobs.forEach { mob ->
                        val def = GameCatalog.miniMobById(mob.id)
                        val isTarget = mob === target
                        val size: Dp = when {
                            isTarget -> if (denseWave) 76.dp else 88.dp
                            mob.alive -> if (denseWave) 52.dp else 62.dp
                            else -> if (denseWave) 40.dp else 48.dp
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = if (isTarget) {
                                Modifier.onGloballyPositioned { coords ->
                                    onTargetCenterChanged?.invoke(coords.boundsInRoot().center)
                                }
                            } else Modifier
                        ) {
                            BossMonsterAvatar(
                                boss = boss,
                                hitFlash = hitFlash && isTarget,
                                hitToken = if (isTarget) hitToken else 0L,
                                isMiniTrashWave = true,
                                spriteOverride = def.spriteRes,
                                animateIdle = mob.alive,
                                modifier = Modifier
                                    .size(size)
                                    .graphicsLayer {
                                        alpha = if (mob.alive) 1f else 0.28f
                                        if (!mob.alive) {
                                            scaleX = 0.85f
                                            scaleY = 0.85f
                                        }
                                    }
                            )
                            if (!mob.alive) {
                                Text("✓", color = LimePlayTop, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                            }
                        }
                    }
                }
            }
            if (puff.value in 0.01f..0.99f) {
                Canvas(modifier = Modifier.size(136.dp)) {
                    val v = puff.value
                    val inv = 1f - v
                    val r = size.minDimension * 0.5f * (0.3f + v * 0.7f)
                    drawCircle(
                        color = EcoEmeraldBright.copy(alpha = 0.26f * inv),
                        radius = r,
                        center = center
                    )
                    drawCircle(
                        color = EcoEmeraldBright.copy(alpha = 0.50f * inv),
                        radius = r * 0.6f,
                        center = center
                    )
                    for (i in 0 until 8) {
                        val a = i * (Math.PI * 2 / 8)
                        val d = r * 0.9f
                        drawCircle(
                            color = Color.White.copy(alpha = inv),
                            radius = 3.8f,
                            center = Offset(center.x + (kotlin.math.cos(a) * d).toFloat(), center.y + (kotlin.math.sin(a) * d).toFloat())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        val name: String
        val hp: Int
        val maxHp: Int
        val weakness = if (isBossStage) boss.preferredWaste else target?.let { GameCatalog.miniMobById(it.id).weakness }
        if (isBossStage) {
            name = boss.name; hp = bossHp; maxHp = bossMaxHp
        } else {
            name = target?.let { GameCatalog.miniMobById(it.id).name } ?: "Wave cleared"
            hp = target?.hp ?: 0
            maxHp = target?.maxHp ?: 1
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(DeepNavyInk.copy(alpha = 0.84f))
                .border(1.5.dp, if (isBossStage) SaffronGold else Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("HP", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
            val hpFrac = (hp.toFloat() / maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            // Fast primary HP bar + lagging white/gold damage drain bar for visceral hit feel!
            val animFrac by animateFloatAsState(hpFrac, tween(110, easing = FastOutSlowInEasing), label = "hpFrac")
            val trailFrac by animateFloatAsState(hpFrac, tween(540, easing = FastOutSlowInEasing), label = "hpTrail")
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BossHpTrack)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))
            ) {
                if (trailFrac > animFrac) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(trailFrac)
                            .background(GlowBeamGold.copy(alpha = 0.88f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animFrac)
                        .background(
                            Brush.verticalGradient(
                                if (isBossStage) listOf(BossHpRedTop, BossHpRedBottom) else listOf(SaffronGold, SaharanAmber)
                            )
                        )
                )
                Text(
                    text = "$name • $hp",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (weakness != null) {
            Spacer(Modifier.height(3.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isEnraged) CarthageCoralDark.copy(alpha = 0.94f) else weakness.darkColor.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, if (isEnraged) GlowBeamGold else weakness.primaryColor)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Image(
                        painter = painterResource(weakness.spriteRes),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    val waveLabel = when {
                        isEnraged -> "ENRAGED BOSS"
                        isBossStage -> "FINAL BOSS"
                        else -> "Wave $waveIndex/${BattleRules.MINI_WAVE_COUNT} • ${mobs.count { it.alive }} left"
                    }
                    Text(
                        text = "$waveLabel • Weak to ${weakness.shortLabel} x${if (isBossStage) "1.5" else "1.85"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
            }
        }
    }
}
