package shhh.cicada.flip.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive (M3E) Spring Physics Specifications.
 */
object ExpressiveMotionTokens {

    // ── Spatial Springs (with Overshoot) ─────────────────────────────
    fun <T> fastSpatial(): SpringSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = 1400f
    )

    fun <T> defaultSpatial(): SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = 800f
    )

    // ── Effects Springs (No Overshoot) ──────────────────────────────
    fun <T> fastEffects(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f
    )

    fun <T> defaultEffects(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 800f
    )

    // Dedicated specs for UI layout transitions (Zero bounce, critically damped)
    val FadeIn: SpringSpec<Float> = spring(Spring.DampingRatioNoBouncy, 800f)
    val FadeOut: SpringSpec<Float> = spring(Spring.DampingRatioNoBouncy, 1400f)
}
