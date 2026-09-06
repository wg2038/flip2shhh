package shhh.cicada.flip

import android.app.Activity
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject


// ════════════════════════════════════════════════════════════════════════
// Samsung One UI Typography Standard Specs
// ════════════════════════════════════════════════════════════════════════

object OneUiTypography {
    val TitleLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    )
    val HeroTitle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    )
    val ItemTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    )
    val ItemSubtitle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )
    val ButtonText = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )
}

// ════════════════════════════════════════════════════════════════════════
// AppStrings i18n Provider (Simplified, Traditional, English)
// ════════════════════════════════════════════════════════════════════════

object AppStrings {
    fun get(context: Context, key: String, langMode: Int): String {
        val lang = Localization.resolve(context, langMode)
        val isEng = lang == AppLanguage.ENGLISH
        val isTrad = lang == AppLanguage.TRADITIONAL

        return when (key) {
            "app_name" -> "Flip to Shhh"

            "perm_dnd_title" -> if (isEng) "Do Not Disturb Access" else if (isTrad) "勿擾模式存取權限" else "勿扰模式权限"
            "perm_dnd_required" -> if (isEng) "Tap to grant DND access" else if (isTrad) "未授權 · 點擊前往設定" else "未授权 · 点击前往设置"
            "perm_dnd_required_banner" -> if (isEng) "DND permission required to enable mute" else if (isTrad) "勿擾模式權限未授予，無法開啟靜音" else "勿扰模式权限未授予，无法开启静音"
            "perm_dnd_desc" -> if (isEng) "Allow app to automatically toggle DND & mute" else if (isTrad) "允許應用程式自動開啟勿擾模式" else "允许应用自动开启勿扰模式"
            "perm_state_granted" -> if (isEng) "Granted" else if (isTrad) "已授權" else "已授权"
            "perm_state_missing" -> if (isEng) "Not granted" else if (isTrad) "未授權" else "未授权"

            "perm_battery_title" -> if (isEng) "Background Battery Optimization" else if (isTrad) "背景電池最佳化" else "后台电池优化"
            "perm_battery_desc" -> if (isEng) "Keep service running stably in background" else if (isTrad) "允許背景無限制執行，避免被系統清除" else "允许后台无限制运行，避免被系统清理"

            "perm_access_title" -> if (isEng) "Accessibility Lock Service" else if (isTrad) "無障礙螢幕鎖定服務" else "无障碍锁屏服务"

            "grant_btn" -> if (isEng) "Grant" else if (isTrad) "去授權" else "去授权"

            // 3. Settings Panel & Options
            "settings_title" -> if (isEng) "Settings" else if (isTrad) "設定" else "设置"
            "autostart_title" -> if (isEng) "Auto-start on Boot" else if (isTrad) "開機自動啟動" else "开机自动启动"
            "autolock_title" -> if (isEng) "Flip to Lock Screen" else if (isTrad) "翻轉自動鎖定螢幕" else "翻转自动锁屏"
            "lang_title" -> if (isEng) "Language" else if (isTrad) "語言" else "语言"
            "setting_about", "about_app_title" -> if (isEng) "About" else if (isTrad) "關於" else "关于"

            "group_permissions" -> if (isEng) "Core Permissions" else if (isTrad) "核心權限" else "核心权限"
            "group_features" -> if (isEng) "Features & Behaviors" else if (isTrad) "功能與行為" else "功能与行为"
            "group_appearance" -> if (isEng) "Appearance & Language" else if (isTrad) "外觀與語言" else "外观与语言"
            "group_about" -> if (isEng) "About" else if (isTrad) "關於" else "关于"

            // 4. Hero Texts
            "hero_tap_to_start" -> if (isEng) "Tap to Start" else if (isTrad) "點擊啟動服務" else "点击启动服务"
            "hero_running_title" -> if (isEng) "Flip to Shhh Ready" else if (isTrad) "翻轉靜音已就緒" else "翻转静音已就绪"
            "hero_dnd_active_title" -> if (isEng) "Do Not Disturb Active" else if (isTrad) "勿擾模式已生效" else "勿扰模式已生效"

            "theme_title" -> if (isEng) "Theme Mode" else if (isTrad) "主題模式" else "外观与主题"
            "sys_default" -> if (isEng) "System" else if (isTrad) "跟隨系統" else "跟随系统"
            "theme_dark" -> if (isEng) "Dark" else if (isTrad) "深色" else "深色"
            "theme_light" -> if (isEng) "Light" else if (isTrad) "淺色" else "浅色"

            "lang_sim_cn" -> "简体中文"
            "lang_trad_cn" -> "繁體中文"
            "lang_english" -> "English"

            // 5. About Screen
            "nav_back" -> if (isEng) "Back" else if (isTrad) "返回" else "返回"
            "about_developer" -> if (isEng) "Flip to enable Do Not Disturb" else if (isTrad) "翻轉手機開啟勿擾模式" else "翻转手机开启勿扰模式"
            "about_privacy_title" -> if (isEng) "Privacy" else if (isTrad) "隱私承諾" else "隐私承诺"
            "about_privacy_body" -> if (isEng) "Offline · Zero data collected" else if (isTrad) "完全離線 · 零資料收集" else "完全离线 · 零数据收集"
            "about_license_title" -> if (isEng) "Open Source License" else if (isTrad) "開放原始碼授權" else "开源许可"
            "about_license_body" -> "MIT License"
            "about_version_title" -> if (isEng) "Version" else if (isTrad) "版本號" else "版本号"

            // 6. Onboarding Screen
            "onboarding_welcome_title" -> if (isEng) "Flip to Shhh" else if (isTrad) "翻轉靜音" else "翻转静音"
            "onboarding_welcome_desc" -> if (isEng) "Place your phone face down to automatically enable Do Not Disturb & mute.\nUltra-low power precision sensor solution."
                                          else if (isTrad) "將手機螢幕朝下放置，自動開啟勿擾與靜音。\n超低功耗高精度感應器方案。"
                                          else "将手机翻转面朝下放置，自动开启勿扰与静音。\n超低功耗高精度传感器方案。"
            "onboarding_swipe_hint" -> if (isEng) "Swipe left to set permissions" else if (isTrad) "向左滑動設定權限" else "向左滑动设置权限"
            "onboarding_perm_title" -> if (isEng) "Set Permissions" else if (isTrad) "設定權限" else "设置权限"
            "onboarding_perm_desc" -> if (isEng) "Grant the following permissions to ensure normal operation"
                                        else if (isTrad) "開啟以下權限以確保功能正常運作"
                                        else "开启以下权限以确保功能正常运行"
            "onboarding_start_btn" -> if (isEng) "Get Started" else if (isTrad) "開始使用" else "开始使用"
            "onboarding_setup_required_btn" -> if (isEng) "Please set required permissions" else if (isTrad) "請先完成權限設定" else "请先完成权限设置"
            "btn_cancel" -> if (isEng) "Cancel" else if (isTrad) "取消" else "取消"

            else -> key
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Activity
// ════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            }
            var themeMode by rememberSaveable { mutableStateOf(prefs.getInt(PrefsKeys.KEY_THEME_MODE, 0)) }
            var languageMode by rememberSaveable { mutableStateOf(prefs.getInt(PrefsKeys.KEY_LANGUAGE_MODE, 0)) }
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            FlipToShhhTheme(themeMode = themeMode) {
                var onboardingComplete by remember {
                    mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_ONBOARDING_COMPLETE, false))
                }

                AnimatedContent(
                    targetState = onboardingComplete,
                    label = "onboarding",
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                    }
                ) { done ->
                    if (!done) {
                        OnboardingScreen(
                            languageMode = languageMode,
                            onComplete = {
                                prefs.edit()
                                    .putBoolean(PrefsKeys.KEY_ONBOARDING_COMPLETE, true)
                                    .putBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
                                    .apply()
                                onboardingComplete = true
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            // POST_NOTIFICATIONS is a runtime permission on API 33+ (this app's minSdk);
                            // without it the foreground service status notification is suppressed.
                            if (ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val isUserEnabled = prefs.getBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
                            if (isUserEnabled && checkDndPermission(context) && !FlipToShhhService.isRunning.value) {
                                startFlipService(context)
                            }
                        }
                        FlipToShhhScreen(
                            languageMode = languageMode,
                            onLanguageModeChanged = { newLang ->
                                languageMode = newLang
                            },
                            onThemeModeChanged = { newTheme ->
                                themeMode = newTheme
                            }
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Theme
// ════════════════════════════════════════════════════════════════════════

private fun extractSystemSeedColor(context: Context): Color? {
    // 1. Try reading system theme customization overlay settings (Samsung One UI, AOSP, Pixel)
    try {
        val themeJson = Settings.Secure.getString(
            context.contentResolver,
            "theme_customization_overlay_packages"
        )
        if (!themeJson.isNullOrEmpty()) {
            val json = JSONObject(themeJson)
            val hexColor = when {
                json.has("android.theme.customization.accent_color") ->
                    json.optString("android.theme.customization.accent_color")
                json.has("android.theme.customization.system_palette") ->
                    json.optString("android.theme.customization.system_palette")
                else -> null
            }
            if (!hexColor.isNullOrEmpty()) {
                val cleanHex = hexColor.trim().removePrefix("#")
                val parsedLong = cleanHex.toLongOrNull(16)
                if (parsedLong != null) {
                    val argb = if (cleanHex.length <= 6) (0xFF000000 or parsedLong).toInt() else parsedLong.toInt()
                    return Color(argb)
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Try WallpaperManager getWallpaperColors (Android 8.1+)
    try {
        val wm = WallpaperManager.getInstance(context)
        val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        if (colors != null) {
            val primary = colors.primaryColor.toArgb()
            return Color(primary)
        }
    } catch (_: Exception) {}

    return null
}

private fun dynamicColorSchemeFromSeed(seedColor: Color, isDark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedColor.toArgb(), hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.30f, 0.90f)

    fun color(hue: Float, sat: Float, light: Float): Color {
        val intColor = ColorUtils.HSLToColor(
            floatArrayOf(
                (hue % 360f + 360f) % 360f,
                sat.coerceIn(0f, 1f),
                light.coerceIn(0f, 1f)
            )
        )
        return Color(intColor)
    }

    return if (isDark) {
        darkColorScheme(
            primary = color(h, s, 0.78f),
            onPrimary = color(h, s * 0.7f, 0.15f),
            primaryContainer = color(h, s * 0.80f, 0.32f),
            onPrimaryContainer = color(h, s * 0.5f, 0.92f),
            secondary = color(h, s * 0.35f, 0.72f),
            onSecondary = color(h, s * 0.35f, 0.18f),
            secondaryContainer = color(h, s * 0.30f, 0.25f),
            onSecondaryContainer = color(h, s * 0.30f, 0.90f),
            background = Color(0xFF111318),
            onBackground = Color(0xFFE2E2E9),
            surface = Color(0xFF191C22),
            surfaceContainer = color(h, s * 0.30f, 0.13f),
            onSurface = Color(0xFFE2E2E9),
            surfaceContainerLow = Color(0xFF1D2026),
            surfaceContainerHigh = Color(0xFF282B32),
            surfaceContainerHighest = Color(0xFF33363E),
            onSurfaceVariant = Color(0xFFC3C6CF),
            outline = Color(0xFF8D9199),
            outlineVariant = Color(0xFF43474E)
        )
    } else {
        lightColorScheme(
            primary = color(h, s * 0.95f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = color(h, s * 0.65f, 0.88f),
            onPrimaryContainer = color(h, s * 0.9f, 0.12f),
            secondary = color(h, s * 0.30f, 0.42f),
            onSecondary = Color.White,
            secondaryContainer = color(h, s * 0.35f, 0.90f),
            onSecondaryContainer = color(h, s * 0.50f, 0.15f),
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            surfaceContainer = color(h, s * 0.25f, 0.95f),
            onSurface = Color(0xFF0F172A),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFF1F5F9),
            surfaceContainerHighest = Color(0xFFE2E8F0),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFE2E8F0),
            outlineVariant = Color(0xFFE2E8F0)
        )
    }
}

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
    // Compose hosts bottom sheets in their own dialog window whose DialogWindowProvider is
    // the content view's direct parent and is NOT reachable through the View parent chain.
    // The appearance must go to that dialog window: writing the activity's from inside a
    // sheet leaves the dialog on default (light icons), which One UI then keeps applying
    // even after the sheet is dismissed.
    val targetWindow = (view.parent as? DialogWindowProvider)?.window
        ?: (context as? Activity)?.window ?: return
    val insetsController = WindowCompat.getInsetsController(targetWindow, targetWindow.decorView)
    insetsController.isAppearanceLightStatusBars = !darkTheme
    insetsController.isAppearanceLightNavigationBars = !darkTheme
}

@Composable
fun FlipToShhhTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var systemSeedColor by remember { mutableStateOf(extractSystemSeedColor(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemSeedColor = extractSystemSeedColor(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val colorScheme = remember(darkTheme, systemSeedColor, context) {
        val seed = systemSeedColor
        if (seed != null) {
            dynamicColorSchemeFromSeed(seed, darkTheme)
        } else {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
    }

    SystemBarsColorEffect(darkTheme = darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ════════════════════════════════════════════════════════════════════════
// Onboarding
// ════════════════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(
    languageMode: Int,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }

    val pagerState = rememberPagerState(pageCount = { 2 })

    var hasDndPermission by remember { mutableStateOf(checkDndPermission(context)) }
    var isIgnoringBatteryOptimization by remember { mutableStateOf(checkBatteryOptimization(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasDndPermission = checkDndPermission(context)
                isIgnoringBatteryOptimization = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allGranted = hasDndPermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingWelcomePage(languageMode = languageMode)
                1 -> OnboardingPermissionsPage(
                    hasDndPermission = hasDndPermission,
                    isIgnoringBatteryOptimization = isIgnoringBatteryOptimization,
                    onGrantDnd = { openDndPermissionSettings(context) },
                    onRequestBatteryOptimization = { requestIgnoreBatteryOptimization(context) },
                    allGranted = allGranted,
                    languageMode = languageMode,
                    onComplete = onComplete
                )
            }
        }

        // Page Indicator & Swipe Hint
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pagerState.currentPage == 0) {
                Text(
                    text = AppStrings.get(context, "onboarding_swipe_hint", languageMode) + " →",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 2 Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingWelcomePage(languageMode: Int) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.DoNotDisturbOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = AppStrings.get(context, "onboarding_welcome_title", languageMode),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = AppStrings.get(context, "onboarding_welcome_desc", languageMode),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun OnboardingPermissionsPage(
    hasDndPermission: Boolean,
    isIgnoringBatteryOptimization: Boolean,
    onGrantDnd: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    allGranted: Boolean,
    languageMode: Int,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = AppStrings.get(context, "onboarding_perm_title", languageMode),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = AppStrings.get(context, "onboarding_perm_desc", languageMode),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OnboardingPermissionItem(
            title = AppStrings.get(context, "perm_dnd_title", languageMode),
            desc = AppStrings.get(context, "perm_dnd_desc", languageMode),
            isGranted = hasDndPermission,
            languageMode = languageMode,
            onAction = onGrantDnd
        )
        Spacer(modifier = Modifier.height(12.dp))
        OnboardingPermissionItem(
            title = AppStrings.get(context, "perm_battery_title", languageMode),
            desc = AppStrings.get(context, "perm_battery_desc", languageMode),
            isGranted = isIgnoringBatteryOptimization,
            languageMode = languageMode,
            onAction = onRequestBatteryOptimization
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onComplete,
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(
                text = if (allGranted) AppStrings.get(context, "onboarding_start_btn", languageMode) else AppStrings.get(context, "onboarding_setup_required_btn", languageMode),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun OnboardingPermissionItem(
    title: String,
    desc: String,
    isGranted: Boolean,
    languageMode: Int,
    onAction: () -> Unit
) {
    val context = LocalContext.current
    // Follow the ACTIVE app theme (which may override the system setting), not the system theme.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val warningColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val warningBgColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else warningBgColor
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) AppIcons.CheckCircle else AppIcons.Warning,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else warningColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isGranted) {
                TextButton(onClick = onAction) { Text(AppStrings.get(context, "grant_btn", languageMode), fontSize = 13.sp) }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Main Screen
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipToShhhScreen(
    languageMode: Int,
    onLanguageModeChanged: (Int) -> Unit = {},
    onThemeModeChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }

    val isServiceRunning by FlipToShhhService.isRunning.collectAsState()
    val isFlippedDown by FlipToShhhService.isFlippedDown.collectAsState()
    val isDndActive by FlipToShhhService.isDndActive.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var hasDndPermission by remember { mutableStateOf(true) }
    var isIgnoringBatteryOptimization by remember { mutableStateOf(true) }
    var hasAccessibilityPermission by remember { mutableStateOf(true) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var showAboutScreen by rememberSaveable { mutableStateOf(false) }

    val updatePermissions: suspend () -> Unit = remember(context) {
        {
            val dnd = withContext(Dispatchers.IO) { checkDndPermission(context) }
            val batt = withContext(Dispatchers.IO) { checkBatteryOptimization(context) }
            val access = withContext(Dispatchers.IO) { FlipLockAccessibilityService.isAccessibilityServiceEnabled(context) }
            hasDndPermission = dnd
            isIgnoringBatteryOptimization = batt
            hasAccessibilityPermission = access
        }
    }

    LaunchedEffect(Unit) {
        updatePermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    updatePermissions()
                    onLanguageModeChanged(prefs.getInt(PrefsKeys.KEY_LANGUAGE_MODE, 0))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(AppStrings.get(context, "perm_dnd_title", languageMode), style = OneUiTypography.ItemTitle) },
            text = { Text(AppStrings.get(context, "perm_dnd_required", languageMode), style = OneUiTypography.ItemSubtitle) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    openDndPermissionSettings(context)
                }) { Text(AppStrings.get(context, "grant_btn", languageMode), style = OneUiTypography.ButtonText) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text(AppStrings.get(context, "btn_cancel", languageMode), style = OneUiTypography.ButtonText) }
            }
        )
    }

    AnimatedContent(
        targetState = showAboutScreen,
        label = "about_screen",
        transitionSpec = {
            if (targetState) {
                (slideInVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } + fadeIn(tween(350, easing = FastOutSlowInEasing)))
                    .togetherWith(fadeOut(tween(250, easing = FastOutSlowInEasing)))
            } else {
                fadeIn(tween(300, easing = FastOutSlowInEasing))
                    .togetherWith(slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } + fadeOut(tween(250, easing = FastOutSlowInEasing)))
            }
        }
    ) { showAbout ->
        if (showAbout) {
            AboutScreen(
                languageMode = languageMode,
                onBack = { showAboutScreen = false }
            )
        } else {
    AnimatedContent(
        targetState = showSettingsScreen,
        label = "settings_screen",
        transitionSpec = {
            if (targetState) {
                (slideInVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } + fadeIn(tween(350, easing = FastOutSlowInEasing)))
                    .togetherWith(fadeOut(tween(250, easing = FastOutSlowInEasing)))
            } else {
                fadeIn(tween(300, easing = FastOutSlowInEasing))
                    .togetherWith(slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight } + fadeOut(tween(250, easing = FastOutSlowInEasing)))
            }
        }
    ) { isSettingsOpen ->
        if (isSettingsOpen) {
            SettingsScreen(
                languageMode = languageMode,
                onLanguageModeChanged = onLanguageModeChanged,
                onThemeModeChanged = onThemeModeChanged,
                onShowAbout = { showAboutScreen = true },
                onDismiss = { showSettingsScreen = false }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 20.dp)
                            .consumeWindowInsets(WindowInsets.navigationBars),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. One UI Large Title Header
                        OneUiHeader(
                            languageMode = languageMode,
                            onOpenSettings = { showSettingsScreen = true }
                        )

                        // 2. Missing DND permission warning banner (only when DND not granted)
                        if (!hasDndPermission) {
                            // Follow the ACTIVE app theme (which may override the system setting).
                            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openDndPermissionSettings(context) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.5f) else Color(0xFFFFFBEB)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Warning,
                                            contentDescription = null,
                                            tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = AppStrings.get(context, "perm_dnd_required_banner", languageMode),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { openDndPermissionSettings(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(AppStrings.get(context, "grant_btn", languageMode), style = OneUiTypography.ButtonText)
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            PureMinimalistHeroCenterpiece(
                                isRunning = isServiceRunning,
                                isFlippedDown = isFlippedDown,
                                isDndActive = isDndActive,
                                languageMode = languageMode,
                                onToggle = {
                                    if (!isServiceRunning) {
                                        if (!hasDndPermission) {
                                            showPermissionDialog = true
                                        } else {
                                            prefs.edit().putBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true).apply()
                                            startFlipService(context)
                                        }
                                    } else {
                                        prefs.edit().putBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, false).apply()
                                        stopFlipService(context)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                    }
                }
            }
    }
}

}
@Composable
fun OneUiHeader(
    languageMode: Int,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Flip to Shhh",
                style = OneUiTypography.TitleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = AppStrings.get(context, "settings_title", languageMode),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PureMinimalistHeroCenterpiece(
    isRunning: Boolean,
    isFlippedDown: Boolean,
    isDndActive: Boolean,
    languageMode: Int,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val heroActive = isRunning && isFlippedDown && isDndActive

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "buttonScale"
    )

    val breathingTransition = rememberInfiniteTransition(label = "etherealBreathing")

    val pulseScale1 by breathingTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heroActive) 4800 else 3200,
                easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale1"
    )
    val pulseAlpha1 by breathingTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heroActive) 4800 else 3200,
                easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha1"
    )

    val pulseScale2 by breathingTransition.animateFloat(
        initialValue = 1.05f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heroActive) 5600 else 4200,
                easing = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale2"
    )
    val pulseAlpha2 by breathingTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heroActive) 5600 else 4200,
                easing = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha2"
    )

    val coreBreathingScale by breathingTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (heroActive) 4800 else 3200,
                easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreathingScale"
    )

    val haloColor = when {
        heroActive -> MaterialTheme.colorScheme.primary
        isRunning -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    val buttonBgColor by animateColorAsState(
        targetValue = when {
            heroActive -> MaterialTheme.colorScheme.primary
            isRunning -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(350),
        label = "buttonBgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            heroActive -> MaterialTheme.colorScheme.onPrimary
            isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(350),
        label = "iconColor"
    )

    val heroIcon = when {
        heroActive -> AppIcons.Bedtime
        isRunning -> AppIcons.DoNotDisturbOn
        else -> AppIcons.PowerSettingsNew
    }

    val statusHeadline = when {
        heroActive -> AppStrings.get(context, "hero_dnd_active_title", languageMode)
        isRunning -> AppStrings.get(context, "hero_running_title", languageMode)
        else -> AppStrings.get(context, "hero_tap_to_start", languageMode)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val centerPt = this.center
                    val baseRadius = size.minDimension / 2f
                    val r2 = (baseRadius * 0.96f) * pulseScale2
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to haloColor.copy(alpha = pulseAlpha2 * 0.42f),
                            0.45f to haloColor.copy(alpha = pulseAlpha2 * 0.22f),
                            0.80f to haloColor.copy(alpha = pulseAlpha2 * 0.05f),
                            1.0f to Color.Transparent,
                            center = centerPt,
                            radius = r2
                        ),
                        radius = r2,
                        center = centerPt
                    )
                    val r1 = (baseRadius * 0.78f) * pulseScale1
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to haloColor.copy(alpha = pulseAlpha1 * 0.65f),
                            0.50f to haloColor.copy(alpha = pulseAlpha1 * 0.35f),
                            0.85f to haloColor.copy(alpha = pulseAlpha1 * 0.08f),
                            1.0f to Color.Transparent,
                            center = centerPt,
                            radius = r1
                        ),
                        radius = r1,
                        center = centerPt
                    )
                    val r0 = (baseRadius * 0.60f) * pulseScale1
                    drawCircle(
                        color = haloColor.copy(alpha = pulseAlpha1 * 0.20f),
                        radius = r0,
                        center = centerPt
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        val s = buttonScale * (if (isRunning) coreBreathingScale else 1.0f)
                        scaleX = s
                        scaleY = s
                        shape = CircleShape
                        clip = true
                    }
                    .background(buttonBgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = statusHeadline,
            style = OneUiTypography.HeroTitle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// Helpers
// ════════════════════════════════════════════════════════════════════════

private fun checkDndPermission(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
}

private fun openDndPermissionSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
        }
    }

    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

// ════════════════════════════════════════════════════════════════════════
// Option Picker Sheets. Single selections open in sheets, configuration lives on the page
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionPickerSheet(
    title: String,
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        // ModalBottomSheet hosts its own dialog window; its bar appearance must follow the
        // app theme, which can disagree with the system theme the fallback would read.
        val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        SystemBarsColorEffect(darkTheme = darkTheme)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp, start = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { (value, label) ->
                    val isSelected = selectedKey == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Settings. A Material 3 destination page. The page manages configuration;
// bottom sheets handle single selections (language / theme pickers below).
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    languageMode: Int,
    onLanguageModeChanged: (Int) -> Unit,
    onThemeModeChanged: (Int) -> Unit,
    onShowAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var hasDndPermission by remember { mutableStateOf(false) }
    var isIgnoringBatteryOptimization by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var autoStartEnabled by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_AUTO_START_BOOT, true)) }
    var autoLockEnabled by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, true)) }
    var themeMode by remember { mutableStateOf(prefs.getInt(PrefsKeys.KEY_THEME_MODE, 0)) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    val refreshPermissions: () -> Unit = remember(context) {
        {
            hasDndPermission = checkDndPermission(context)
            isIgnoringBatteryOptimization = checkBatteryOptimization(context)
            hasAccessibilityPermission = FlipLockAccessibilityService.isAccessibilityServiceEnabled(context)
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { refreshPermissions() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { withContext(Dispatchers.IO) { refreshPermissions() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { onDismiss() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Full immersion: the Scaffold keeps no system-bar insets, so the page
        // background extends behind the navigation bar. The scroll content leaves
        // its own navigationBarsPadding at the end instead.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.get(context, "settings_title", languageMode)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.get(context, "nav_back", languageMode)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionLabel(AppStrings.get(context, "group_permissions", languageMode))
            SettingsGroup {
                SettingsItemRow(
                    icon = AppIcons.NotificationsActive,
                    title = AppStrings.get(context, "perm_dnd_title", languageMode),
                    supporting = AppStrings.get(
                        context,
                        if (hasDndPermission) "perm_state_granted" else "perm_state_missing",
                        languageMode
                    ),
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); openDndPermissionSettings(context) }
                ) {
                    if (hasDndPermission) SettingsStatusCheck()
                }
                SettingsItemRow(
                    icon = AppIcons.BatterySaver,
                    title = AppStrings.get(context, "perm_battery_title", languageMode),
                    supporting = AppStrings.get(
                        context,
                        if (isIgnoringBatteryOptimization) "perm_state_granted" else "perm_state_missing",
                        languageMode
                    ),
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); requestIgnoreBatteryOptimization(context) }
                ) {
                    if (isIgnoringBatteryOptimization) SettingsStatusCheck()
                    else SettingsGrantButton(AppStrings.get(context, "grant_btn", languageMode)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        requestIgnoreBatteryOptimization(context)
                    }
                }
                SettingsItemRow(
                    icon = AppIcons.Lock,
                    title = AppStrings.get(context, "perm_access_title", languageMode),
                    supporting = AppStrings.get(
                        context,
                        if (hasAccessibilityPermission) "perm_state_granted" else "perm_state_missing",
                        languageMode
                    ),
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); openAccessibilitySettings(context) }
                ) {
                    if (hasAccessibilityPermission) SettingsStatusCheck()
                }
            }

            SettingsSectionLabel(AppStrings.get(context, "group_features", languageMode))
            SettingsGroup {
                SettingsItemRow(
                    icon = AppIcons.PowerSettingsNew,
                    title = AppStrings.get(context, "autostart_title", languageMode),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        autoStartEnabled = !autoStartEnabled
                        prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_START_BOOT, autoStartEnabled).apply()
                    }
                ) {
                    Switch(
                        checked = autoStartEnabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoStartEnabled = enabled
                            prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_START_BOOT, enabled).apply()
                        }
                    )
                }
                SettingsItemRow(
                    icon = AppIcons.Lock,
                    title = AppStrings.get(context, "autolock_title", languageMode),
                    // Effective state: the preference alone does nothing while the
                    // accessibility lock service is disabled.
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val enabled = !(autoLockEnabled && hasAccessibilityPermission)
                        autoLockEnabled = enabled
                        prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, enabled).apply()
                        if (enabled && !hasAccessibilityPermission) openAccessibilitySettings(context)
                    }
                ) {
                    Switch(
                        checked = autoLockEnabled && hasAccessibilityPermission,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            autoLockEnabled = enabled
                            prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, enabled).apply()
                            if (enabled && !FlipLockAccessibilityService.isAccessibilityServiceEnabled(context)) {
                                openAccessibilitySettings(context)
                            }
                        }
                    )
                }
            }

            SettingsSectionLabel(AppStrings.get(context, "group_appearance", languageMode))
            SettingsGroup {
                SettingsItemRow(
                    icon = AppIcons.Palette,
                    title = AppStrings.get(context, "theme_title", languageMode),
                    supporting = when (themeMode) {
                        1 -> AppStrings.get(context, "theme_dark", languageMode)
                        2 -> AppStrings.get(context, "theme_light", languageMode)
                        else -> AppStrings.get(context, "sys_default", languageMode)
                    },
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showThemePicker = true }
                ) {
                    SettingsChevron()
                }
                SettingsItemRow(
                    icon = AppIcons.Translate,
                    title = AppStrings.get(context, "lang_title", languageMode),
                    supporting = when (languageMode) {
                        1 -> AppStrings.get(context, "lang_sim_cn", languageMode)
                        2 -> AppStrings.get(context, "lang_trad_cn", languageMode)
                        3 -> AppStrings.get(context, "lang_english", languageMode)
                        else -> AppStrings.get(context, "sys_default", languageMode)
                    },
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showLanguagePicker = true }
                ) {
                    SettingsChevron()
                }
            }

            SettingsSectionLabel(AppStrings.get(context, "group_about", languageMode))
            SettingsGroup {
                SettingsItemRow(
                    icon = AppIcons.Info,
                    title = AppStrings.get(context, "setting_about", languageMode),
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onShowAbout() }
                ) {
                    SettingsChevron()
                }
            }

            val versionName = remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
                } catch (e: Exception) {
                    BuildConfig.VERSION_NAME
                }
            }
            Text(
                text = "Flip to Shhh v$versionName · © 2026 Cicada",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            )

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLanguagePicker) {
        OptionPickerSheet(
            title = AppStrings.get(context, "lang_title", languageMode),
            options = listOf(
                0 to AppStrings.get(context, "sys_default", languageMode),
                1 to AppStrings.get(context, "lang_sim_cn", languageMode),
                2 to AppStrings.get(context, "lang_trad_cn", languageMode),
                3 to AppStrings.get(context, "lang_english", languageMode)
            ),
            selectedKey = languageMode,
            onSelect = { mode ->
                prefs.edit().putInt(PrefsKeys.KEY_LANGUAGE_MODE, mode).apply()
                onLanguageModeChanged(mode)
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showThemePicker) {
        OptionPickerSheet(
            title = AppStrings.get(context, "theme_title", languageMode),
            options = listOf(
                0 to AppStrings.get(context, "sys_default", languageMode),
                1 to AppStrings.get(context, "theme_dark", languageMode),
                2 to AppStrings.get(context, "theme_light", languageMode)
            ),
            selectedKey = themeMode,
            onSelect = { mode ->
                themeMode = mode
                prefs.edit().putInt(PrefsKeys.KEY_THEME_MODE, mode).apply()
                onThemeModeChanged(mode)
            },
            onDismiss = { showThemePicker = false }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) { content() }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) 56.dp else 72.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(supporting, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsChevron() {
    Icon(
        imageVector = AppIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun SettingsStatusCheck() {
    Icon(
        imageVector = AppIcons.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun SettingsGrantButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        onClick = onClick
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}


// ════════════════════════════════════════════════════════════════════════
// About page
// ════════════════════════════════════════════════════════════════════════

@Composable
fun AboutScreen(
    languageMode: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // 1. Top bar with back navigation and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get(context, "nav_back", languageMode),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = AppStrings.get(context, "about_app_title", languageMode),
                style = OneUiTypography.TitleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 2. Brand section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.DoNotDisturbOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = AppStrings.get(context, "app_name", languageMode),
                style = OneUiTypography.HeroTitle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AppStrings.get(context, "about_developer", languageMode),
                style = OneUiTypography.ItemSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val versionName = remember(context) {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
            } catch (e: Exception) {
                BuildConfig.VERSION_NAME
            }
        }

        // 3. Privacy, License & Version card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: Privacy
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = AppIcons.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get(context, "about_privacy_title", languageMode),
                            style = OneUiTypography.ItemTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = AppStrings.get(context, "about_privacy_body", languageMode),
                            style = OneUiTypography.ItemSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Row 2: License
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = AppIcons.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get(context, "about_license_title", languageMode),
                            style = OneUiTypography.ItemTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = AppStrings.get(context, "about_license_body", languageMode),
                            style = OneUiTypography.ItemSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Row 3: Version
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = AppIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.get(context, "about_version_title", languageMode),
                                style = OneUiTypography.ItemTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v$versionName",
                                style = OneUiTypography.ItemSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Footer
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "Copyright © 2026 Cicada",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun startFlipService(context: Context) {
    ContextCompat.startForegroundService(context, Intent(context, FlipToShhhService::class.java))
}

private fun stopFlipService(context: Context) {
    context.stopService(Intent(context, FlipToShhhService::class.java))
}
