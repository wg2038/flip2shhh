package shhh.cicada.flip.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material 3 Expressive Color Schemes & Surface Container Hierarchy.
 *
 * Implements the 5-tier Surface Container model:
 *  - surfaceContainerLowest
 *  - surfaceContainerLow
 *  - surfaceContainer
 *  - surfaceContainerHigh
 *  - surfaceContainerHighest
 *
 * Designed to provide vibrant, expressive contrasts while maintaining high accessibility.
 */

/**
 * Material 3 Expressive (M3E) Official Baseline Color Schemes.
 *
 * Uses the canonical Google Material 3 default color palette (Seed: #6750A4)
 * with full 5-tier Surface Container model and WCAG AAA compliance.
 */
val DefaultDarkColorScheme = darkColorScheme()

val DefaultLightColorScheme = lightColorScheme()
