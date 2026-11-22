package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsStore
import com.example.model.TunisianPlace
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure horizontal reel math (unit-testable) guaranteeing exact landing on [targetIndex]. */
object RouletteReelMath {
    /**
     * Computes the target integer slot coordinate strictly ahead of [currentSlot] so that at least
     * [minCardsTravelled] cards glide past the center pointers and the reel stops dead-center on [targetIndex].
     */
    fun targetSlotForIndex(
        currentSlot: Float,
        targetIndex: Int,
        itemCount: Int,
        minCardsTravelled: Int = 20
    ): Float {
        if (itemCount <= 0) return currentSlot
        val safeIdx = targetIndex.coerceIn(0, itemCount - 1)
        val baseSlot = ceil(currentSlot).toInt() + minCardsTravelled.coerceAtLeast(itemCount)
        val currentMod = ((baseSlot % itemCount) + itemCount) % itemCount
        var delta = safeIdx - currentMod
        if (delta < 0) delta += itemCount
        return (baseSlot + delta).toFloat()
    }

    /** Returns the item index (0 until [itemCount]) currently aligned between the top & bottom pointers. */
    fun indexAtSlot(slot: Float, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val nearest = slot.roundToInt()
        return ((nearest % itemCount) + itemCount) % itemCount
    }
}

private val ReelBgTop = Color(0xFF142A54)
private val ReelBgBottom = Color(0xFF09142A)
private val ReelCardSurface = Color(0xFF10244A)
private val ReelCardSurfaceCenter = Color(0xFF173264)
private val ReelDecelEasing = CubicBezierEasing(0.05f, 0.92f, 0.14f, 1.0f)

/**
 * Silky 60fps Horizontal Card-Strip Place Roulette styled in WasteCrush's Mediterranean Navy,
 * Saffron Gold & Eco-Emerald palette:
 * - Symmetrical top (▼) and bottom (▲) glowing pointers framing the active Tunisian landmark card.
 * - Zero per-frame composition churn: `scrollSlot.value` is read strictly inside `graphicsLayer`
 *   (GPU draw phase) while only integer slot transitions rebind the 5 pre-cached card slots.
 * - Pre-caches all landmark `Painter`s up front for butter-smooth card gliding with synced audio & haptics.
 */
@Composable
fun PlaceRouletteReel(
    places: List<TunisianPlace>,
    targetPlaceId: String,
    spinToken: Long,
    title: String = "Place Selection",
    spinDurationMs: Int = 3000,
    onTick: () -> Unit = {},
    onSpinFinished: (TunisianPlace) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (places.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val initialIdx = remember(places) {
        places.indexOfFirst { it.id == targetPlaceId }.coerceAtLeast(0)
    }
    val scrollSlot = remember(places) { Animatable(initialIdx.toFloat()) }
    var isSpinning by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    // Pre-resolve painters once so gliding cards never trigger painter allocation during a 60fps spin
    val placePainters = places.map { painterResource(id = it.imageRes) }

    val itemCount = places.size
    // Integer slot only updates ~20 times across a full spin (avoiding 60fps composition churn!)
    val baseSlotInt by remember {
        derivedStateOf { scrollSlot.value.roundToInt() }
    }
    val currentIndex by remember(itemCount) {
        derivedStateOf { RouletteReelMath.indexAtSlot(baseSlotInt.toFloat(), itemCount) }
    }
    val highlightedPlace = places[currentIndex.coerceIn(0, places.lastIndex)]

    // Crisp pointer kick + audio + haptic tick whenever a card crosses center
    val pointerBounce = remember { Animatable(0f) }

    LaunchedEffect(baseSlotInt, isSpinning) {
        if (isSpinning) {
            onTick()
            if (SettingsStore.current.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            pointerBounce.snapTo(1f)
            pointerBounce.animateTo(0f, tween(95, easing = FastOutSlowInEasing))
        }
    }

    // Trigger smooth long-tail deceleration reel glide whenever spinToken updates
    LaunchedEffect(spinToken, targetPlaceId, places) {
        if (spinToken <= 0L) return@LaunchedEffect
        val targetIdx = places.indexOfFirst { it.id == targetPlaceId }.coerceAtLeast(0)
        val minTravel = maxOf(18, itemCount * 3)
        val destSlot = RouletteReelMath.targetSlotForIndex(
            currentSlot = scrollSlot.value,
            targetIndex = targetIdx,
            itemCount = itemCount,
            minCardsTravelled = minTravel
        )
        isLocked = false
        isSpinning = true
        try {
            scrollSlot.animateTo(
                targetValue = destSlot,
                animationSpec = tween(durationMillis = spinDurationMs, easing = ReelDecelEasing)
            )
            isSpinning = false
            isLocked = true
            if (SettingsStore.current.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onSpinFinished(places[targetIdx])
        } finally {
            isSpinning = false
        }
    }

    val pointerColor = if (isLocked) EcoEmeraldBright else GlowBeamGold
    val lockedPopScale by animateFloatAsState(
        targetValue = if (isLocked) 1.045f else 1f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "reelLockedPop"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("place_roulette_reel"),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(
            width = if (isLocked) 2.8.dp else 2.2.dp,
            brush = Brush.linearGradient(
                if (isLocked) {
                    listOf(EcoEmeraldBright, GlowBeamGold, LimePlayTop)
                } else {
                    listOf(GlowBeamGold, SaffronGold, CeramicBlueTop)
                }
            )
        ),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(ReelBgTop, ReelBgBottom)))
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // WasteCrush Theme Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isLocked) LimePlayTop else SaffronGold,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isLocked) "PLACE LOCKED!" else title.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = if (isLocked) LimePlayTop else GlowBeamGold,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isLocked) LimePlayTop else SaffronGold,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Top Golden/Emerald Downward Triangle Pointer (▼)
            ReelTrianglePointer(
                pointingDown = true,
                color = pointerColor,
                modifier = Modifier.graphicsLayer {
                    translationY = pointerBounce.value * 3.5.dp.toPx()
                }
            )

            Spacer(Modifier.height(8.dp))

            // 60fps GPU-Layer Horizontal Card Strip Viewport
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(198.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val cardWidthDp = (maxWidth * 0.56f).coerceIn(166.dp, 208.dp)
                val cardSpacingDp = 12.dp
                val stepPx = with(density) { (cardWidthDp + cardSpacingDp).toPx() }
                val lockedScaleVal = lockedPopScale

                // Subtle recessed horizontal track band behind cards
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF060E1F).copy(alpha = 0.45f),
                                    Color(0xFF0B1C3D).copy(alpha = 0.72f),
                                    Color(0xFF060E1F).copy(alpha = 0.45f)
                                )
                            )
                        )
                )

                // Render only 5 visible card slots; read scrollSlot.value strictly inside graphicsLayer
                for (offset in -2..2) {
                    val slotIdx = baseSlotInt + offset
                    val placeIdx = ((slotIdx % itemCount) + itemCount) % itemCount
                    val place = places[placeIdx]
                    val painter = placePainters[placeIdx]
                    val isCenterSlot = offset == 0

                    key(offset) {
                        PlaceReelCard(
                            place = place,
                            painter = painter,
                            isCenter = isCenterSlot,
                            isLocked = isLocked && isCenterSlot,
                            modifier = Modifier
                                .width(cardWidthDp)
                                .height(182.dp)
                                .graphicsLayer {
                                    // GPU-phase continuous position read -> 60fps silky motion, zero recomposition!
                                    val distFromCenter = slotIdx - scrollSlot.value
                                    val absDist = abs(distFromCenter)
                                    translationX = distFromCenter * stepPx
                                    val depthScale = (1f - (absDist * 0.13f)).coerceIn(0.80f, 1f)
                                    val finalScale = if (isLocked && isCenterSlot) depthScale * lockedScaleVal else depthScale
                                    scaleX = finalScale
                                    scaleY = finalScale
                                    alpha = (1f - (absDist * 0.40f)).coerceIn(0.24f, 1f)
                                }
                        )
                    }
                }

                // Soft dark-navy horizontal edge fade so cards glide effortlessly into view
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(46.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(ReelBgBottom, ReelBgBottom.copy(alpha = 0f))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(46.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(ReelBgBottom.copy(alpha = 0f), ReelBgBottom)
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))

            // Bottom Golden/Emerald Upward Triangle Pointer (▲)
            ReelTrianglePointer(
                pointingDown = false,
                color = pointerColor,
                modifier = Modifier.graphicsLayer {
                    translationY = -pointerBounce.value * 3.5.dp.toPx()
                }
            )

            Spacer(Modifier.height(10.dp))

            // Theme-matched Destination Readout Pill
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isLocked) EcoEmeraldDark.copy(alpha = 0.90f) else Color(0xFF0A1733),
                border = BorderStroke(
                    width = 1.4.dp,
                    color = if (isLocked) GlowBeamGold else SaffronGold.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("roulette_selected_place_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.CheckCircle else Icons.Default.Place,
                        contentDescription = null,
                        tint = if (isLocked) LimePlayTop else SaffronGold,
                        modifier = Modifier.size(14.dp)
                    )
                    AnimatedContent(
                        targetState = highlightedPlace,
                        transitionSpec = { fadeIn(tween(75)) togetherWith fadeOut(tween(75)) },
                        label = "reelRegionSubtitle"
                    ) { p ->
                        Text(
                            text = if (isLocked) "${p.regionTag} • Ready to Purify!" else p.regionTag,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isLocked) Color.White else SidiSkyLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceReelCard(
    place: TunisianPlace,
    painter: Painter,
    isCenter: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val borderBrush = when {
        isLocked -> Brush.linearGradient(listOf(EcoEmeraldBright, GlowBeamGold, LimePlayTop))
        isCenter -> Brush.linearGradient(listOf(GlowBeamGold, SaffronGold))
        else -> Brush.linearGradient(listOf(CeramicBlueTop.copy(alpha = 0.45f), DarkBorderSubtle))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (isCenter) ReelCardSurfaceCenter else ReelCardSurface,
        border = BorderStroke(width = if (isLocked) 2.6.dp else if (isCenter) 2.dp else 1.2.dp, brush = borderBrush),
        shadowElevation = if (isCenter) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (isLocked) GlowBeamGold.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Image(
                    painter = painter,
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, EcoEmerald.copy(alpha = 0.24f))
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    ),
                    color = if (isLocked) GlowBeamGold else Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = place.arabicName.ifBlank { place.regionTag },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = if (isCenter) SidiSkyLight else TextMutedOnDark,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReelTrianglePointer(
    pointingDown: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 14.dp)) {
        val path = Path().apply {
            if (pointingDown) {
                moveTo(2f, 1.5f)
                lineTo(size.width - 2f, 1.5f)
                lineTo(size.width * 0.5f, size.height - 1.5f)
                close()
            } else {
                moveTo(size.width * 0.5f, 1.5f)
                lineTo(size.width - 2f, size.height - 1.5f)
                lineTo(2f, size.height - 1.5f)
                close()
            }
        }
        // Subtle outer glow + crisp dark-navy outline matching WasteCrush arcade craft
        drawPath(path = path, color = color.copy(alpha = 0.28f), style = Stroke(width = 5.dp.toPx()))
        drawPath(path = path, color = color)
        drawPath(path = path, color = DeepNavyInk, style = Stroke(width = 1.6.dp.toPx()))
    }
}
