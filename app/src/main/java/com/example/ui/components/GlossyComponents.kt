package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BOARD_SIZE
import com.example.engine.BoardTile
import com.example.engine.GridPos
import com.example.engine.SpecialTileType
import com.example.model.BossDefinition
import com.example.model.BossRarity
import com.example.model.GameCatalog
import com.example.model.WasteType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topColor: Color = LimePlayTop,
    bottomColor: Color = LimePlayBottom,
    lipColor: Color = LimePlayLip,
    textColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    tag: String? = null
) {
    val actualTop = if (enabled) topColor else Color(0xFF94A3B8)
    val actualBottom = if (enabled) bottomColor else Color(0xFF64748B)
    val actualLip = if (enabled) lipColor else Color(0xFF475569)

    Box(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(height)
            .shadow(elevation = if (enabled) 6.dp else 1.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(actualLip)
            .border(2.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.verticalGradient(listOf(actualTop, actualBottom))),
            contentAlignment = Alignment.Center
        ) {
            // Glossy curved highlight sheen across top edge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.92f)
                    .padding(top = 3.dp)
                    .height(height * 0.30f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.34f))
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlossyIconTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topColor: Color = CeramicBlueTop,
    bottomColor: Color = CeramicBlueBottom,
    lipColor: Color = CeramicBlueLip,
    badgeText: String? = null,
    tag: String? = null
) {
    Column(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(5.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(lipColor)
                    .border(2.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .padding(bottom = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(15.dp))
                        .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.84f)
                            .padding(top = 3.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.32f))
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(50))
                        .background(CarthageCoral)
                        .border(1.5.dp, Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = TextOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WasteCrushLogoWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val fontSize = if (compact) 28.sp else 38.sp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Glossy emerald recycling crest
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 46.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(LimePlayTop, EcoEmeraldDark)))
                .border(2.5.dp, DeepNavyInk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Recycling,
                contentDescription = "WasteCrush Leaf Emblem",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 28.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center) {
            // Bold navy outline shadow layer
            Row(modifier = Modifier.offset(x = 2.dp, y = 2.5.dp)) {
                Text(
                    text = "Waste",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = DeepNavyInk
                )
                Text(
                    text = "Crush",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = DeepNavyInk
                )
            }
            // Vibrant Mediterranean foreground letters
            Row {
                Text(
                    text = "Waste",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFF42E8FF)
                )
                Text(
                    text = "Crush",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color(0xFFFFCA28)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = SaffronGold,
            modifier = Modifier.size(if (compact) 18.dp else 24.dp)
        )
    }
}

@Composable
fun StatPill(
    value: String,
    icon: ImageVector,
    badgeColor: Color,
    iconTint: Color = DeepNavyInk,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(50),
        color = DarkSurfaceCard,
        border = BorderStroke(1.8.dp, DarkBorderSubtle),
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = TextOnDark
            )
        }
    }
}

@Composable
fun RecyclableTileArt(
    tile: BoardTile,
    isSelected: Boolean,
    isHinted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 860f),
        label = "tileScale"
    )
    val needsPulse = isSelected || isHinted || tile.special != SpecialTileType.NONE
    val infiniteTransition = if (needsPulse) rememberInfiniteTransition(label = "tileEffects") else null
    val wiggleAngle = if (isSelected && infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = -4.0f,
            targetValue = 4.0f,
            animationSpec = infiniteRepeatable(tween(200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "selWiggle"
        ).value
    } else 0f
    val hintPulse = if (isHinted && !isSelected && infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hintPulse"
        ).value
    } else 1f
    val specialSpin = if (tile.special == SpecialTileType.ECO_BOMB && infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
            label = "specSpin"
        ).value
    } else 0f
    val specialPulse = if (tile.special != SpecialTileType.NONE && infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 0.70f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "specPulse"
        ).value
    } else 1f

    Box(
        modifier = modifier
            .graphicsLayer {
                val combined = scale * hintPulse
                scaleX = combined
                scaleY = combined
                rotationZ = wiggleAngle
            }
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        // Only allocate a glow Canvas for selected / hinted tiles (eliminates 49 per-frame Canvas draws)
        if (isSelected || isHinted) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    color = (if (isSelected) GlowBeamGold else tile.type.primaryColor).copy(alpha = if (isSelected) 0.42f else 0.30f),
                    radius = w * 0.46f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
                if (isHinted && !isSelected) {
                    drawCircle(
                        color = GlowBeamGold.copy(alpha = 0.65f),
                        radius = w * 0.44f,
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Image(
            painter = painterResource(id = tile.type.spriteRes),
            contentDescription = tile.type.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.5.dp)
        )

        // Luminous special-tile energy effects on top when charged
        if (tile.special != SpecialTileType.NONE) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .graphicsLayer {
                        if (tile.special == SpecialTileType.ECO_BOMB) {
                            rotationZ = specialSpin
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                when (tile.special) {
                    SpecialTileType.LINE_H -> {
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    GlowBeamGold.copy(alpha = specialPulse),
                                    GlowBeamCore,
                                    GlowBeamGold.copy(alpha = specialPulse)
                                )
                            ),
                            start = Offset(w * 0.04f, h * 0.5f),
                            end = Offset(w * 0.96f, h * 0.5f),
                            strokeWidth = w * 0.12f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(GlowBeamGold.copy(alpha = specialPulse), radius = w * 0.13f, center = Offset(w * 0.10f, h * 0.5f))
                        drawCircle(GlowBeamCore, radius = w * 0.06f, center = Offset(w * 0.10f, h * 0.5f))
                        drawCircle(GlowBeamGold.copy(alpha = specialPulse), radius = w * 0.13f, center = Offset(w * 0.90f, h * 0.5f))
                        drawCircle(GlowBeamCore, radius = w * 0.06f, center = Offset(w * 0.90f, h * 0.5f))
                    }
                    SpecialTileType.LINE_V -> {
                        drawLine(
                            brush = Brush.verticalGradient(
                                listOf(
                                    GlowBeamGold.copy(alpha = specialPulse),
                                    GlowBeamCore,
                                    GlowBeamGold.copy(alpha = specialPulse)
                                )
                            ),
                            start = Offset(w * 0.5f, h * 0.04f),
                            end = Offset(w * 0.5f, h * 0.96f),
                            strokeWidth = w * 0.12f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(GlowBeamGold.copy(alpha = specialPulse), radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.10f))
                        drawCircle(GlowBeamCore, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.10f))
                        drawCircle(GlowBeamGold.copy(alpha = specialPulse), radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.90f))
                        drawCircle(GlowBeamCore, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.90f))
                    }
                    SpecialTileType.ECO_BOMB -> {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(GlowBeamGold, CeramicBlueTop, GlowBeamCore, GlowBeamGold)),
                            radius = w * 0.42f,
                            style = Stroke(width = w * 0.10f)
                        )
                        drawCircle(
                            color = GlowBeamCore.copy(alpha = 0.72f * specialPulse),
                            radius = w * 0.15f
                        )
                    }
                    SpecialTileType.NONE -> {}
                }
            }
        }
    }
}

/**
 * Renders the custom uploaded Boss or Mini-Boss PNG sprite with breathing idle bob,
 * rarity aura glow, ground shadow, and crisp physical hit recoil + shake + bright flash on damage.
 */
@Composable
fun BossMonsterAvatar(
    boss: BossDefinition,
    modifier: Modifier = Modifier,
    hitFlash: Boolean = false,
    hitToken: Long = 0L,
    isMiniTrashWave: Boolean = false,
    waveIndex: Int = 3,
    animateIdle: Boolean = true,
    @androidx.annotation.DrawableRes spriteOverride: Int? = null
) {
    val spriteRes = when {
        spriteOverride != null -> spriteOverride
        isMiniTrashWave && waveIndex <= 1 -> GameCatalog.miniWave1Sprite
        isMiniTrashWave -> GameCatalog.miniWave2Sprite
        else -> boss.spriteRes
    }

    // Only run infinite animation ticker when animateIdle is genuinely true
    val idleBob = if (animateIdle) {
        val infiniteTransition = rememberInfiniteTransition(label = "bossIdle")
        infiniteTransition.animateFloat(
            initialValue = -3.0f,
            targetValue = 3.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bossBob"
        ).value
    } else 0f

    // Crisp impulse-based hit squash/recoil/shake keyed on hitToken (reacts cleanly even on rapid combos!)
    val impactPulse = remember { Animatable(0f) }
    LaunchedEffect(hitToken, hitFlash) {
        if (hitToken > 0L || hitFlash) {
            impactPulse.snapTo(1f)
            impactPulse.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }
    val imp = impactPulse.value
    val isActivelyHit = imp > 0.12f || hitFlash

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Ground shadow & rarity energy aura behind sprite (zero per-frame Brush allocation)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawOval(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(w * 0.14f, h * 0.84f),
                size = Size(w * 0.72f, h * 0.14f)
            )
            if ((!isMiniTrashWave && boss.rarity != BossRarity.COMMON) || isActivelyHit) {
                val auraColor = if (isActivelyHit) GlowBeamGold else boss.rarity.frameColor
                val baseAlpha = if (isActivelyHit) 0.42f * imp.coerceAtLeast(0.45f) else 0.18f
                val center = Offset(w * 0.5f, h * 0.48f)
                drawCircle(
                    color = auraColor.copy(alpha = baseAlpha * 0.55f),
                    radius = w * 0.54f,
                    center = center
                )
                drawCircle(
                    color = auraColor.copy(alpha = baseAlpha),
                    radius = w * 0.36f,
                    center = center
                )
            }
        }

        Image(
            painter = painterResource(id = spriteRes),
            contentDescription = if (isMiniTrashWave) "Pollution Mini-Boss" else boss.name,
            contentScale = ContentScale.Fit,
            colorFilter = if (imp > 0.35f || hitFlash) {
                ColorFilter.tint(Color.White.copy(alpha = (imp * 0.58f).coerceIn(0.20f, 0.58f)), BlendMode.SrcAtop)
            } else null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shake = sin(imp * 22f) * 10f * imp
                    translationX = shake
                    translationY = idleBob - (imp * 9f)
                    scaleX = 1f + imp * 0.16f
                    scaleY = 1f - imp * 0.12f
                    rotationZ = -imp * 7.5f
                }
        )
    }
}

@Composable
fun BossCardTile(
    boss: BossDefinition,
    level: Int,
    copies: Int,
    isEquipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = level > 0
    val needed = GameCatalog.copiesNeededForNextLevel(level.coerceAtLeast(1), boss.rarity)
    val canUpgrade = unlocked && copies >= needed && level < 10

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (unlocked) DarkSurfaceCard else Color(0xFF0A162D),
        border = BorderStroke(
            width = if (isEquipped) 3.dp else 1.8.dp,
            color = if (isEquipped) EcoEmeraldBright else if (unlocked) boss.rarity.frameColor else DarkBorderSubtle
        ),
        shadowElevation = if (unlocked) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        if (unlocked) listOf(Color(0xFF162C56), DarkSurfaceCard)
                        else listOf(Color(0xFF0C1A36), Color(0xFF071022))
                    )
                )
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(boss.rarity.frameColor)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = boss.rarity.label.take(4).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                if (unlocked) {
                    Text(
                        text = "Lv.$level",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = SaffronGold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextMutedOnDark,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                boss.bodyColor.copy(alpha = if (unlocked) 0.30f else 0.14f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                BossMonsterAvatar(
                    boss = boss,
                    animateIdle = false,
                    modifier = Modifier
                        .size(58.dp)
                        .alpha(if (unlocked) 1f else 0.55f)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = boss.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (unlocked) TextOnDark else TextMutedOnDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            if (unlocked) {
                val frac = (copies.toFloat() / needed.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF061022))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(if (canUpgrade) EcoEmerald else CeramicBlueBottom)
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canUpgrade) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upgrade ready",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Text(
                            text = "$copies/$needed",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            } else {
                Text(
                    text = "Arena ${boss.arenaId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedOnDark
                )
            }
        }
    }
}

private data class ActivePopBurst(
    val key: Long,
    val tiles: List<Pair<GridPos, WasteType>>,
    val progress: Animatable<Float, AnimationVector1D>
)

private data class PositionedBoardTile(
    val uniqueKey: Long,
    val row: Int,
    val col: Int,
    val tile: BoardTile
)

@Immutable
data class ImmutableBoard(val rows: List<List<BoardTile>>)

@Composable
fun Match3BoardGrid(
    board: List<List<BoardTile>>,
    selectedTile: GridPos?,
    interactive: Boolean,
    onTileTap: (GridPos) -> Unit = {},
    onTileSwipe: (GridPos, GridPos) -> Unit = { _, _ -> },
    useSandstoneTheme: Boolean = true,
    invalidSwapPair: Pair<GridPos, GridPos>? = null,
    invalidSwapToken: Long = 0L,
    popBurstTiles: List<Pair<GridPos, WasteType>> = emptyList(),
    popBurstToken: Long = 0L,
    hintPair: Pair<GridPos, GridPos>? = null,
    feverMode: Boolean = false,
    onBoardBoundsChanged: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Match3BoardGrid(
        board = remember(board) { ImmutableBoard(board) },
        selectedTile = selectedTile,
        interactive = interactive,
        onTileTap = onTileTap,
        onTileSwipe = onTileSwipe,
        useSandstoneTheme = useSandstoneTheme,
        invalidSwapPair = invalidSwapPair,
        invalidSwapToken = invalidSwapToken,
        popBurstTiles = popBurstTiles,
        popBurstToken = popBurstToken,
        hintPair = hintPair,
        feverMode = feverMode,
        onBoardBoundsChanged = onBoardBoundsChanged,
        modifier = modifier
    )
}

@Composable
fun Match3BoardGrid(
    board: ImmutableBoard,
    selectedTile: GridPos?,
    interactive: Boolean,
    onTileTap: (GridPos) -> Unit = {},
    onTileSwipe: (GridPos, GridPos) -> Unit = { _, _ -> },
    useSandstoneTheme: Boolean = true,
    invalidSwapPair: Pair<GridPos, GridPos>? = null,
    invalidSwapToken: Long = 0L,
    popBurstTiles: List<Pair<GridPos, WasteType>> = emptyList(),
    popBurstToken: Long = 0L,
    hintPair: Pair<GridPos, GridPos>? = null,
    feverMode: Boolean = false,
    onBoardBoundsChanged: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rows = board.rows
    val cellLight = Color(0xFF162F61)
    val cellDark = Color(0xFF0F234B)
    val cellRim = Color.White.copy(alpha = 0.08f)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 13.dp.toPx() }
    val seenIds = remember { mutableSetOf<Long>() }
    var boardInitialized by remember { mutableStateOf(false) }
    val activeBursts = remember { mutableStateListOf<ActivePopBurst>() }

    LaunchedEffect(popBurstToken) {
        if (popBurstToken > 0L && popBurstTiles.isNotEmpty()) {
            while (activeBursts.size >= 3) activeBursts.removeAt(0)
            val anim = Animatable(0f)
            val entry = ActivePopBurst(popBurstToken, popBurstTiles, anim)
            activeBursts.add(entry)
            launch {
                try {
                    anim.animateTo(1f, tween(durationMillis = 210, easing = FastOutSlowInEasing))
                } finally {
                    activeBursts.remove(entry)
                }
            }
        }
    }

    val positionedTiles = remember(rows) {
        val used = HashSet<Long>(BOARD_SIZE * BOARD_SIZE)
        buildList(BOARD_SIZE * BOARD_SIZE) {
            for (r in 0 until BOARD_SIZE) {
                for (c in 0 until BOARD_SIZE) {
                    val t = rows.getOrNull(r)?.getOrNull(c) ?: BoardTile(-(r * 100L + c + 1L), WasteType.PLASTIC_BLUE)
                    val k = if (used.add(t.id)) t.id else -(r * 100L + c + 1L)
                    add(PositionedBoardTile(k, r, c, t))
                }
            }
        }
    }

    SideEffect {
        boardInitialized = true
        positionedTiles.forEach { seenIds.add(it.uniqueKey) }
    }

    val frameBrush = remember(feverMode) {
        if (feverMode) {
            Brush.linearGradient(listOf(EcoEmeraldBright, GlowBeamGold, LimePlayTop, GlowBeamGold))
        } else {
            Brush.linearGradient(listOf(GlowBeamGold, SaffronGold, Color(0xFF9E6B14), SaffronGold))
        }
    }
    val innerBgBrush = remember {
        Brush.verticalGradient(listOf(Color(0xFF10244D), Color(0xFF060E20)))
    }

    // Zero shadowElevation on the animated board container prevents expensive per-frame RenderThread shadow redraws
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("match3_board_grid"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF060E20),
        border = BorderStroke(if (feverMode) 4.dp else 3.5.dp, frameBrush),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(innerBgBrush)
                .padding(4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF060E20))
                .border(1.2.dp, (if (feverMode) EcoEmeraldBright else SaffronGold).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(5.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        onBoardBoundsChanged?.invoke(coords.boundsInRoot())
                    }
            ) {
                val cellW = constraints.maxWidth / BOARD_SIZE.toFloat()
                val cellH = constraints.maxHeight / BOARD_SIZE.toFloat()
                val cellWDp = maxWidth / BOARD_SIZE
                val cellHDp = maxHeight / BOARD_SIZE

                // Layer 1: Single lightweight Canvas draws all 49 recessed cell wells (eliminates 57 layout nodes!)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pad = 1.4.dp.toPx()
                    val cr = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    val rimStroke = Stroke(width = 0.8.dp.toPx())
                    val selStroke = Stroke(width = 2.dp.toPx())
                    val hintStroke = Stroke(width = 1.5.dp.toPx())

                    for (r in 0 until BOARD_SIZE) {
                        val top = r * cellH + pad
                        val h = (cellH - pad * 2f).coerceAtLeast(1f)
                        for (c in 0 until BOARD_SIZE) {
                            val left = c * cellW + pad
                            val w = (cellW - pad * 2f).coerceAtLeast(1f)
                            val isSel = selectedTile != null && selectedTile.row == r && selectedTile.col == c
                            val isHint = hintPair != null && (
                                (hintPair.first.row == r && hintPair.first.col == c) ||
                                    (hintPair.second.row == r && hintPair.second.col == c)
                                )
                            val bgColor = if ((r + c) % 2 == 0) cellLight else cellDark
                            val tl = Offset(left, top)
                            val sz = Size(w, h)
                            drawRoundRect(color = bgColor, topLeft = tl, size = sz, cornerRadius = cr)
                            if (isSel) {
                                drawRoundRect(color = GlowBeamGold.copy(alpha = 0.28f), topLeft = tl, size = sz, cornerRadius = cr)
                                drawRoundRect(color = GlowBeamGold, topLeft = tl, size = sz, cornerRadius = cr, style = selStroke)
                            } else if (isHint) {
                                drawRoundRect(color = EcoEmeraldBright.copy(alpha = 0.22f), topLeft = tl, size = sz, cornerRadius = cr)
                                drawRoundRect(color = EcoEmeraldBright.copy(alpha = 0.85f), topLeft = tl, size = sz, cornerRadius = cr, style = hintStroke)
                            } else {
                                drawRoundRect(color = cellRim, topLeft = tl, size = sz, cornerRadius = cr, style = rimStroke)
                            }
                        }
                    }
                }

                // Layer 2: GPU-Accelerated Spring-Physics Tiles (zero relayout via graphicsLayer)
                positionedTiles.forEach { item ->
                    key(item.uniqueKey) {
                        val targetX = item.col * cellW
                        val targetY = item.row * cellH
                        val isNewSpawn = boardInitialized && item.uniqueKey !in seenIds

                        val animX = remember { Animatable(targetX) }
                        val animY = remember { Animatable(if (isNewSpawn) targetY - cellH * 1.0f else targetY) }
                        val alphaAnim = remember { Animatable(if (isNewSpawn) 0f else 1f) }
                        val recoilX = remember { Animatable(0f) }
                        val recoilY = remember { Animatable(0f) }
                        // Zero-coroutine direct float drag offset for instantaneous 120Hz finger tracking
                        var dragOffX by remember { mutableFloatStateOf(0f) }
                        var dragOffY by remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(Unit) {
                            if (isNewSpawn) {
                                if (item.col > 0) delay(item.col * 4L)
                                launch { alphaAnim.animateTo(1f, tween(65)) }
                                launch {
                                    animY.animateTo(
                                        targetY,
                                        spring(dampingRatio = 0.72f, stiffness = 820f)
                                    )
                                }
                            }
                        }

                        LaunchedEffect(item.row, item.col, cellW, cellH) {
                            if (abs(animX.targetValue - targetX) > 0.5f) {
                                launch {
                                    animX.animateTo(
                                        targetX,
                                        spring(dampingRatio = 0.80f, stiffness = 980f)
                                    )
                                }
                            }
                            if (abs(animY.targetValue - targetY) > 0.5f) {
                                launch {
                                    animY.animateTo(
                                        targetY,
                                        spring(dampingRatio = 0.72f, stiffness = 840f)
                                    )
                                }
                            }
                        }

                        val pos = GridPos(item.row, item.col)
                        LaunchedEffect(invalidSwapToken) {
                            val pair = invalidSwapPair
                            if (invalidSwapToken > 0L && pair != null && (pair.first == pos || pair.second == pos)) {
                                val partner = if (pair.first == pos) pair.second else pair.first
                                val dx = (partner.col - pos.col) * cellW * 0.38f
                                val dy = (partner.row - pos.row) * cellH * 0.38f
                                launch {
                                    recoilX.animateTo(dx, tween(60, easing = FastOutSlowInEasing))
                                    recoilX.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 900f))
                                }
                                launch {
                                    recoilY.animateTo(dy, tween(60, easing = FastOutSlowInEasing))
                                    recoilY.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 900f))
                                }
                            }
                        }

                        val isSel = selectedTile == pos
                        val isHint = hintPair?.let { it.first == pos || it.second == pos } == true
                        val maxRubberBand = cellW * 0.40f
                        val interactionSource = remember { MutableInteractionSource() }

                        var rawDragX by remember(item.row, item.col, item.uniqueKey) { mutableFloatStateOf(0f) }
                        var rawDragY by remember(item.row, item.col, item.uniqueKey) { mutableFloatStateOf(0f) }
                        var swipeTriggered by remember(item.row, item.col, item.uniqueKey) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .size(cellWDp, cellHDp)
                                .graphicsLayer {
                                    translationX = animX.value + recoilX.value + dragOffX
                                    translationY = animY.value + recoilY.value + dragOffY
                                    alpha = alphaAnim.value
                                }
                                .padding(1.3.dp)
                                .then(
                                    if (interactive) {
                                        Modifier
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null
                                            ) { onTileTap(pos) }
                                            .pointerInput(item.row, item.col, item.uniqueKey) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        rawDragX = 0f
                                                        rawDragY = 0f
                                                        swipeTriggered = false
                                                    },
                                                    onDragCancel = {
                                                        dragOffX = 0f
                                                        dragOffY = 0f
                                                    },
                                                    onDragEnd = {
                                                        dragOffX = 0f
                                                        dragOffY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (swipeTriggered) return@detectDragGestures
                                                        rawDragX += dragAmount.x
                                                        rawDragY += dragAmount.y
                                                        if (abs(rawDragX) >= abs(rawDragY)) {
                                                            dragOffX = rawDragX.coerceIn(-maxRubberBand, maxRubberBand)
                                                            dragOffY = 0f
                                                        } else {
                                                            dragOffY = rawDragY.coerceIn(-maxRubberBand, maxRubberBand)
                                                            dragOffX = 0f
                                                        }
                                                        if (abs(rawDragX) > swipeThresholdPx || abs(rawDragY) > swipeThresholdPx) {
                                                            swipeTriggered = true
                                                            val target = if (abs(rawDragX) > abs(rawDragY)) {
                                                                val dc = if (rawDragX > 0) 1 else -1
                                                                GridPos(item.row, (item.col + dc).coerceIn(0, BOARD_SIZE - 1))
                                                            } else {
                                                                val dr = if (rawDragY > 0) 1 else -1
                                                                GridPos((item.row + dr).coerceIn(0, BOARD_SIZE - 1), item.col)
                                                            }
                                                            dragOffX = 0f
                                                            dragOffY = 0f
                                                            if (target != pos) onTileSwipe(pos, target)
                                                        }
                                                    }
                                                )
                                            }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            RecyclableTileArt(
                                tile = item.tile,
                                isSelected = isSel,
                                isHinted = isHint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Layer 3: Fast, Crisp Match Pop Particle Bursts & Shockwaves
                if (activeBursts.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        activeBursts.forEach { burst ->
                            val t = burst.progress.value
                            val inv = (1f - t).coerceIn(0f, 1f)
                            burst.tiles.forEach { (pos, wasteType) ->
                                val cx = (pos.col + 0.5f) * cellW
                                val cy = (pos.row + 0.5f) * cellH
                                val center = Offset(cx, cy)
                                val baseCol = wasteType.primaryColor

                                // Expanding colored + gold shockwave ring
                                val ringRadius = cellW * (0.20f + 0.56f * t)
                                drawCircle(
                                    color = baseCol.copy(alpha = 0.78f * inv),
                                    radius = ringRadius,
                                    center = center,
                                    style = Stroke(width = (3.5f * inv + 1f).dp.toPx())
                                )

                                // 6 crisp sparkle droplets flying outward
                                val dropletR = (4.2f - 3.6f * t).coerceAtLeast(0.6f).dp.toPx()
                                val dist = cellW * 0.64f * t
                                for (i in 0 until 6) {
                                    val ang = i * (Math.PI / 3.0) + pos.row * 0.35
                                    val px = cx + (cos(ang) * dist).toFloat()
                                    val py = cy + (sin(ang) * dist).toFloat() - (1f - t) * 3.dp.toPx()
                                    val dotCol = if (i % 2 == 0) GlowBeamGold else baseCol
                                    drawCircle(
                                        color = dotCol.copy(alpha = inv),
                                        radius = dropletR,
                                        center = Offset(px, py)
                                    )
                                }

                                // Crisp 4-point white starburst flash in early pop phase
                                if (t < 0.38f) {
                                    val flashAlpha = ((0.38f - t) / 0.38f).coerceIn(0f, 1f)
                                    val ray = cellW * 0.42f * (1f - t)
                                    drawLine(
                                        color = Color.White.copy(alpha = flashAlpha),
                                        start = Offset(cx - ray, cy),
                                        end = Offset(cx + ray, cy),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = Color.White.copy(alpha = flashAlpha),
                                        start = Offset(cx, cy - ray),
                                        end = Offset(cx, cy + ray),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RarityChip(rarity: BossRarity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(rarity.frameColor)
            .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = rarity.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
fun EcoBrandBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = EcoEmerald.copy(alpha = 0.22f),
        border = BorderStroke(1.5.dp, EcoEmeraldBright)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Recycling,
                contentDescription = null,
                tint = EcoEmeraldBright,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "TUNISIA ECO-ARENA",
                style = MaterialTheme.typography.labelSmall,
                color = TextOnDark
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = SaffronGold,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
