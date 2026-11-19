package shhh.cicada.flip.ui.components

import android.graphics.Path as AndroidPath
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import shhh.cicada.flip.AppIcons
import shhh.cicada.flip.ui.theme.ExpressiveMotionTokens
import shhh.cicada.flip.ui.theme.M3ExpressiveShapes

/**
 * Material 3 Expressive (M3E) Hero Centerpiece.
 *
 * Implements:
 *  - Official M3E 7-stage shape morphing sequence:
 *      1. SoftBurst (柔边星爆形)
 *      2. Cookie9Sided (9 边波浪曲奇形)
 *      3. Pentagon (圆角五边形)
 *      4. Pill (胶囊形)
 *      5. Sunny (多齿太阳形)
 *      6. Cookie4Sided (4 边波浪曲奇形)
 *      7. Oval (椭圆形)
 *  - Official M3E rotating kinematics: quarter-turn spring step + continuous smooth rotation
 *  - Spatial Spring Physics: tactile overshoot bounce on press / release
 *  - 4-Stage Geometric Shape Morphing for center trigger button
 *  - Full localized status headlines and subtitles
 */
@Composable
fun ExpressiveHeroCenterpiece(
    isRunning: Boolean,
    isFlippedDown: Boolean,
    isDndActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val heroActive = isRunning && isFlippedDown && isDndActive

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1. Spatial Spring Physics: Press Scale with Natural Overshoot Bounce
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = ExpressiveMotionTokens.fastSpatial(),
        label = "heroButtonScale"
    )

    // 2. M3E 7-Stage Official Shape Morphing & Rotation Sequence
    val morphSequence = remember { M3ExpressiveShapes.buildMorphSequence() }
    val shapesScaleFactor = remember {
        M3ExpressiveShapes.calculateScaleFactor(M3ExpressiveShapes.sequence) * 0.92f
    }

    val morphProgress = remember { Animatable(0f) }
    var morphRotationTargetAngle by remember { mutableFloatStateOf(90f) }
    val globalRotation = remember { Animatable(0f) }
    var currentMorphIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val morphSpec = spring<Float>(
            dampingRatio = 0.62f,
            stiffness = 180f,
            visibilityThreshold = 0.005f
        )
        // Morph sequence loop
        launch {
            while (true) {
                val deferred = async {
                    val result = morphProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = morphSpec
                    )
                    if (result.endReason == AnimationEndReason.Finished) {
                        currentMorphIndex = (currentMorphIndex + 1) % morphSequence.size
                        morphProgress.snapTo(0f)
                        morphRotationTargetAngle = (morphRotationTargetAngle + 90f) % 360f
                    }
                }
                delay(850L) // Morph interval dwell
                deferred.await()
            }
        }
        // Continuous global rotation loop
        launch {
            globalRotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    val cookieFillColor by animateColorAsState(
        targetValue = when {
            heroActive -> MaterialTheme.colorScheme.tertiary
            isRunning -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = ExpressiveMotionTokens.defaultEffects(),
        label = "cookieFillColor"
    )

    // 4. Effects Springs: Smooth Color Transitions
    val iconColor by animateColorAsState(
        targetValue = when {
            heroActive -> MaterialTheme.colorScheme.onTertiary
            isRunning -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = ExpressiveMotionTokens.fastEffects(),
        label = "heroIconColor"
    )

    val heroIcon = when {
        heroActive -> AppIcons.Bedtime
        isRunning -> AppIcons.DoNotDisturbOn
        else -> AppIcons.PowerSettingsNew
    }


    val androidPath = remember { AndroidPath() }
    val composePath = remember(androidPath) { androidPath.asComposePath() }
    val scaleMatrix = remember { Matrix() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // M3E 7-Stage Official Morphing Shape Silhouette
            Canvas(modifier = Modifier.size(260.dp)) {
                val centerPt = this.center

                // Render official M3E morphing shape
                val progress = morphProgress.value.coerceIn(0f, 1f)
                morphSequence[currentMorphIndex].toPath(
                    progress = progress,
                    path = androidPath
                )
                M3ExpressiveShapes.processPath(
                    path = composePath,
                    size = size,
                    scaleFactor = shapesScaleFactor,
                    matrix = scaleMatrix
                )

                // Combined rotation: quarter-turn morph swivel + continuous global spin
                val currentAngle = (progress * 90f + morphRotationTargetAngle + globalRotation.value) % 360f

                rotate(degrees = currentAngle, pivot = centerPt) {
                    drawPath(
                        path = composePath,
                        color = cookieFillColor,
                        style = Fill
                    )
                }
            }

            // Software Icon directly in the center (without any rounded rectangle container)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = heroIcon,
                    transitionSpec = {
                        (scaleIn(animationSpec = ExpressiveMotionTokens.fastSpatial(), initialScale = 0.75f) +
                                fadeIn(animationSpec = ExpressiveMotionTokens.fastEffects()))
                            .togetherWith(
                                scaleOut(animationSpec = ExpressiveMotionTokens.fastEffects(), targetScale = 0.75f) +
                                        fadeOut(animationSpec = ExpressiveMotionTokens.fastEffects())
                            )
                    },
                    label = "heroIconTransition"
                ) { targetIcon ->
                    Icon(
                        imageVector = targetIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}
