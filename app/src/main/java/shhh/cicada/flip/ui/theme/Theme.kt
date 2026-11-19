package shhh.cicada.flip.ui.theme

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import org.json.JSONObject

/**
 * Material 3 Expressive (M3E) Theme Provider for Flip to Shhh.
 *
 * Configures:
 *  - Dynamic color support (Material You Monet) with 3-tier OEM-aware fallback
 *  - 5-level Surface Containers
 *  - Expressive Typography with Emphasized hierarchy
 *  - M3E Shape Scale (20dp large, 32dp extraLarge, 48dp extraExtraLarge)
 *  - Spring Physics across all components via ExpressiveMotionTokens
 *  - Transparent Edge-to-Edge System Bars
 */
@Composable
fun SystemBarsColorEffect(darkTheme: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(darkTheme, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applySystemBarAppearance(view, context, darkTheme)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        applySystemBarAppearance(view, context, darkTheme)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun applySystemBarAppearance(view: android.view.View, context: Context, darkTheme: Boolean) {
    val targetWindow = (view.parent as? DialogWindowProvider)?.window
        ?: (context as? Activity)?.window ?: return
    val insetsController = WindowCompat.getInsetsController(targetWindow, targetWindow.decorView)
    insetsController.isAppearanceLightStatusBars = !darkTheme
    insetsController.isAppearanceLightNavigationBars = !darkTheme
}

/**
 * 3-Tier Monet & OEM Dynamic Theme Resolver.
 *
 * 1. Pixel / AOSP / 类原生:
 *    Directly uses Android native dynamicDarkColorScheme / dynamicLightColorScheme.
 * 2. Samsung One UI (including One UI 9 / Android 17):
 *    One UI overrides API 34+ @android:color/system_primary_light/dark with Samsung Blue
 *    (#387AFF / #94B8FF) even when Color Palette is enabled, while correctly updating the
 *    API 31-33 tonal palette (@android:color/system_accent1..3_* & system_neutral1..2_*)
 *    and theme_customization_overlay_packages.
 *    Therefore, on Samsung:
 *      (a) When Color Palette is active, build the M3 ColorScheme directly from the
 *          system tonal palette (@android:color/system_accent* / system_neutral*).
 *      (b) If tonal palette is unavailable, fall back to HCT generation via MaterialKolor
 *          using overlaySeed or WallpaperManager wallpaper colors.
 * 3. Incompatible OEMs or unavailable seed (e.g. live/video wallpaper with palette off):
 *    Falls back cleanly to the canonical M3E purple baseline palette.
 */
object MonetThemeResolver {
    private const val TAG = "MonetThemeResolver"

    val isPixel: Boolean by lazy {
        Build.MANUFACTURER.equals("google", ignoreCase = true) ||
            Build.BRAND.equals("google", ignoreCase = true)
    }

    val isSamsung: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    // Known unmodified AOSP, Compose, and Samsung One UI static default primary colors
    private val StaticDefaultPrimaries = setOf(
        0xFF6650A4.toInt(), // Compose M3 Light baseline purple
        0xFF6750A4.toInt(), // M3 baseline seed purple
        0xFFD0BCFF.toInt(), // Compose M3 Dark baseline purple
        0xFF475D92.toInt(), // AOSP API 34+ system_primary_light / system_accent1_600 static default
        0xFFB0C6FF.toInt(), // AOSP API 34+ system_primary_dark / system_accent1_200 static default
        0xFF006493.toInt(), // Legacy AOSP API 31-33 light accent1 static default
        0xFF8ECDFF.toInt(), // Legacy AOSP API 31-33 dark accent1 static default
        0xFF387AFF.toInt(), // Samsung One UI light static brand blue override
        0xFF94B8FF.toInt(), // Samsung One UI dark static brand blue override
        0xFF0381FE.toInt(), // Samsung One UI legacy static brand blue override
    )

    private fun hex(argb: Int?): String =
        if (argb == null) "null" else "#%08X".format(argb)

    private fun sysColor(context: Context, resId: Int): Color =
        Color(context.getColor(resId))

    private fun readOverlaySetting(context: Context): String? {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                "theme_customization_overlay_packages"
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun isOverlayActive(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw == "{}") return false
        return raw.contains("system_palette") ||
            raw.contains("accent_color") ||
            raw.contains("color_source")
    }

    private fun parseOverlaySeedArgb(raw: String?): Int? {
        if (raw.isNullOrBlank() || raw == "{}") return null
        return try {
            val json = JSONObject(raw)
            val hexValue = listOf(
                "android.theme.customization.system_palette",
                "android.theme.customization.accent_color",
            ).firstNotNullOfOrNull { key ->
                json.optString(key).takeIf { it.isNotBlank() }
            } ?: return null
            val cleaned = hexValue.trim().removePrefix("#")
            val argbHex = when (cleaned.length) {
                6 -> "FF$cleaned"
                8 -> cleaned
                else -> return null
            }
            argbHex.toLong(16).toInt()
        } catch (_: Throwable) {
            null
        }
    }

    private fun readSystemAccent1Check(context: Context, darkTheme: Boolean): Int? {
        return try {
            val checkRes = if (darkTheme) {
                android.R.color.system_accent1_200
            } else {
                android.R.color.system_accent1_600
            }
            context.getColor(checkRes)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Builds a full Material 3 ColorScheme directly from Android's API 31+ tonal palette
     * resources (@android:color/system_accent1..3_* & system_neutral1..2_*).
     * Bypasses One UI's #387AFF / #94B8FF override on API 34+ system_primary_light/dark.
     */
    private fun buildSchemeFromSystemTonalPalette(context: Context, darkTheme: Boolean): ColorScheme {
        val n1_0 = sysColor(context, android.R.color.system_neutral1_0)
        val n1_10 = sysColor(context, android.R.color.system_neutral1_10)
        val n1_50 = sysColor(context, android.R.color.system_neutral1_50)
        val n1_100 = sysColor(context, android.R.color.system_neutral1_100)
        val n1_200 = sysColor(context, android.R.color.system_neutral1_200)
        val n1_700 = sysColor(context, android.R.color.system_neutral1_700)
        val n1_800 = sysColor(context, android.R.color.system_neutral1_800)
        val n1_900 = sysColor(context, android.R.color.system_neutral1_900)
        val n1_1000 = sysColor(context, android.R.color.system_neutral1_1000)

        return if (darkTheme) {
            darkColorScheme(
                primary = sysColor(context, android.R.color.system_accent1_200),
                onPrimary = sysColor(context, android.R.color.system_accent1_800),
                primaryContainer = sysColor(context, android.R.color.system_accent1_700),
                onPrimaryContainer = sysColor(context, android.R.color.system_accent1_100),
                inversePrimary = sysColor(context, android.R.color.system_accent1_600),
                secondary = sysColor(context, android.R.color.system_accent2_200),
                onSecondary = sysColor(context, android.R.color.system_accent2_800),
                secondaryContainer = sysColor(context, android.R.color.system_accent2_700),
                onSecondaryContainer = sysColor(context, android.R.color.system_accent2_100),
                tertiary = sysColor(context, android.R.color.system_accent3_200),
                onTertiary = sysColor(context, android.R.color.system_accent3_800),
                tertiaryContainer = sysColor(context, android.R.color.system_accent3_700),
                onTertiaryContainer = sysColor(context, android.R.color.system_accent3_100),
                background = n1_900,
                onBackground = n1_100,
                surface = n1_900,
                onSurface = n1_100,
                surfaceVariant = sysColor(context, android.R.color.system_neutral2_700),
                onSurfaceVariant = sysColor(context, android.R.color.system_neutral2_200),
                surfaceTint = sysColor(context, android.R.color.system_accent1_200),
                inverseSurface = n1_100,
                inverseOnSurface = n1_800,
                outline = sysColor(context, android.R.color.system_neutral2_400),
                outlineVariant = sysColor(context, android.R.color.system_neutral2_700),
                scrim = n1_1000,
                surfaceBright = n1_700,
                surfaceDim = n1_900,
                surfaceContainerLowest = n1_1000,
                surfaceContainerLow = n1_900,
                surfaceContainer = lerp(n1_900, n1_800, 0.25f),
                surfaceContainerHigh = lerp(n1_900, n1_800, 0.65f),
                surfaceContainerHighest = n1_800,
            )
        } else {
            lightColorScheme(
                primary = sysColor(context, android.R.color.system_accent1_600),
                onPrimary = sysColor(context, android.R.color.system_accent1_0),
                primaryContainer = sysColor(context, android.R.color.system_accent1_100),
                onPrimaryContainer = sysColor(context, android.R.color.system_accent1_900),
                inversePrimary = sysColor(context, android.R.color.system_accent1_200),
                secondary = sysColor(context, android.R.color.system_accent2_600),
                onSecondary = sysColor(context, android.R.color.system_accent2_0),
                secondaryContainer = sysColor(context, android.R.color.system_accent2_100),
                onSecondaryContainer = sysColor(context, android.R.color.system_accent2_900),
                tertiary = sysColor(context, android.R.color.system_accent3_600),
                onTertiary = sysColor(context, android.R.color.system_accent3_0),
                tertiaryContainer = sysColor(context, android.R.color.system_accent3_100),
                onTertiaryContainer = sysColor(context, android.R.color.system_accent3_900),
                background = n1_10,
                onBackground = n1_900,
                surface = n1_10,
                onSurface = n1_900,
                surfaceVariant = sysColor(context, android.R.color.system_neutral2_100),
                onSurfaceVariant = sysColor(context, android.R.color.system_neutral2_700),
                surfaceTint = sysColor(context, android.R.color.system_accent1_600),
                inverseSurface = n1_800,
                inverseOnSurface = n1_50,
                outline = sysColor(context, android.R.color.system_neutral2_500),
                outlineVariant = sysColor(context, android.R.color.system_neutral2_200),
                scrim = n1_1000,
                surfaceBright = n1_10,
                surfaceDim = lerp(n1_100, n1_200, 0.3f),
                surfaceContainerLowest = n1_0,
                surfaceContainerLow = lerp(n1_10, n1_50, 0.4f),
                surfaceContainer = n1_50,
                surfaceContainerHigh = lerp(n1_50, n1_100, 0.5f),
                surfaceContainerHighest = n1_100,
            )
        }
    }

    private fun extractWallpaperSeedArgb(context: Context): Int? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val wallpaperColors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
            (wallpaperColors?.primaryColor
                ?: wallpaperColors?.secondaryColor
                ?: wallpaperColors?.tertiaryColor)?.toArgb()
        } catch (_: Throwable) {
            null
        }
    }

    fun resolveColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
        val overlayRaw = readOverlaySetting(context)
        val hasOverlay = isOverlayActive(overlayRaw)
        val overlaySeed = parseOverlaySeedArgb(overlayRaw)
        val accent1Check = readSystemAccent1Check(context, darkTheme)

        var nativeScheme: ColorScheme? = null
        var nativePrimary: Int? = null
        try {
            val s = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            nativeScheme = s
            nativePrimary = s.primary.toArgb()
        } catch (t: Throwable) {
            Log.w(TAG, "Tier 1 (Native Monet) query failed", t)
        }

        // Tier 1: Pixel & AOSP / 类原生 -> native Monet dynamic color scheme
        val nativeIsCustom = nativePrimary != null && nativePrimary !in StaticDefaultPrimaries
        if ((isPixel || (!isSamsung && nativeIsCustom)) && nativeScheme != null) {
            Log.d(
                TAG,
                "Tier 1 (Native Monet) | mfg=${Build.MANUFACTURER} | primary=${hex(nativePrimary)} | overlay=$hasOverlay"
            )
            return nativeScheme
        }

        // Tier 2: Samsung One UI adaptation
        if (isSamsung) {
            // 2a: Color Palette is active -> directly use Android's system_accent* / system_neutral* tonal palette
            val accent1IsCustom = hasOverlay && accent1Check != null && accent1Check !in StaticDefaultPrimaries
            if (accent1IsCustom) {
                try {
                    val tonalScheme = buildSchemeFromSystemTonalPalette(context, darkTheme)
                    Log.d(
                        TAG,
                        "Tier 2a (Samsung Tonal Palette) | accent1=${hex(accent1Check)} | overlaySeed=${hex(overlaySeed)}"
                    )
                    return tonalScheme
                } catch (t: Throwable) {
                    Log.w(TAG, "Tier 2a (Samsung Tonal Palette) failed", t)
                }
            }

            // 2b: Overlay seed or WallpaperManager seed -> HCT generation via MaterialKolor
            val wpSeed = extractWallpaperSeedArgb(context)
            val (seedSource, chosenSeed) = when {
                overlaySeed != null && overlaySeed !in StaticDefaultPrimaries -> "overlaySeed" to overlaySeed
                wpSeed != null -> "wallpaperColors" to wpSeed
                else -> "none" to null
            }
            if (chosenSeed != null) {
                try {
                    Log.d(
                        TAG,
                        "Tier 2b (Samsung HCT: $seedSource) | seed=${hex(chosenSeed)} | dark=$darkTheme"
                    )
                    return dynamicColorScheme(
                        seedColor = Color(chosenSeed),
                        isDark = darkTheme,
                        isAmoled = false,
                        style = PaletteStyle.TonalSpot,
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "Tier 2b (Samsung HCT) failed", t)
                }
            }
        }

        // Tier 3: Incompatible OEMs or unavailable seed -> canonical M3E purple fallback
        Log.d(
            TAG,
            "Tier 3 (Purple Fallback) | mfg=${Build.MANUFACTURER} | overlay=$hasOverlay | dark=$darkTheme"
        )
        return if (darkTheme) DefaultDarkColorScheme else DefaultLightColorScheme
    }
}

@Composable
fun FlipToShhhTheme(
    updateKey: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()

    // Force re-resolution whenever darkTheme or updateKey (e.g. Activity onResume) changes
    val colorScheme = remember(darkTheme, updateKey) {
        MonetThemeResolver.resolveColorScheme(context, darkTheme)
    }

    SystemBarsColorEffect(darkTheme = darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppExpressiveTypography,
        shapes = AppExpressiveShapes,
        content = content
    )
}
