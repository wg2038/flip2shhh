package shhh.cicada.flip

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shhh.cicada.flip.ui.screens.HomeScreen
import shhh.cicada.flip.ui.screens.OnboardingScreen
import shhh.cicada.flip.ui.theme.ExpressiveMotionTokens
import shhh.cicada.flip.ui.theme.ExpressiveShapeTokens
import shhh.cicada.flip.ui.theme.ExpressiveTypographyTokens
import shhh.cicada.flip.ui.theme.FlipToShhhTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val coroutineScope = rememberCoroutineScope()
            val prefs = remember {
                context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            }

            var resumeTrigger by remember { mutableIntStateOf(0) }

            FlipToShhhTheme(updateKey = resumeTrigger) {
                var onboardingComplete by remember {
                    mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_ONBOARDING_COMPLETE, false))
                }

                var hasDndPermission by remember { mutableStateOf(checkDndPermission(context)) }
                var isIgnoringBatteryOptimization by remember { mutableStateOf(checkBatteryOptimization(context)) }
                var hasAccessibilityPermission by remember { mutableStateOf(FlipLockAccessibilityService.isAccessibilityServiceEnabled(context)) }

                val refreshPermissions: suspend () -> Unit = remember(context) {
                    {
                        val dnd = withContext(Dispatchers.IO) { checkDndPermission(context) }
                        val batt = withContext(Dispatchers.IO) { checkBatteryOptimization(context) }
                        val access = withContext(Dispatchers.IO) { FlipLockAccessibilityService.isAccessibilityServiceEnabled(context) }
                        hasDndPermission = dnd
                        isIgnoringBatteryOptimization = batt
                        hasAccessibilityPermission = access

                        // If user just granted Accessibility from the "Flip to Lock Screen" toggle, activate it automatically
                        if (access && prefs.getBoolean(PrefsKeys.KEY_PENDING_AUTO_LOCK_AUTH, false)) {
                            prefs.edit()
                                .putBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, true)
                                .putBoolean(PrefsKeys.KEY_PENDING_AUTO_LOCK_AUTH, false)
                                .apply()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    refreshPermissions()
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            resumeTrigger++
                            coroutineScope.launch {
                                refreshPermissions()
                                val dndGranted = checkDndPermission(context)
                                val isUserEnabled = prefs.getBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
                                if (onboardingComplete && isUserEnabled && dndGranted && !FlipToShhhService.isRunning.value) {
                                    startFlipService(context)
                                } else if (!dndGranted && FlipToShhhService.isRunning.value) {
                                    stopFlipService(context)
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AnimatedContent(
                    targetState = onboardingComplete,
                    label = "onboarding_transition",
                    transitionSpec = {
                        fadeIn(ExpressiveMotionTokens.FadeIn) togetherWith fadeOut(ExpressiveMotionTokens.FadeOut)
                    }
                ) { isDone ->
                    if (!isDone) {
                        OnboardingScreen(
                            hasDndPermission = hasDndPermission,
                            isIgnoringBatteryOptimization = isIgnoringBatteryOptimization,
                            onGrantDnd = { openDndPermissionSettings(context) },
                            onRequestBatteryOptimization = { requestIgnoreBatteryOptimization(context) },
                            onComplete = {
                                prefs.edit()
                                    .putBoolean(PrefsKeys.KEY_ONBOARDING_COMPLETE, true)
                                    .putBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
                                    .apply()
                                onboardingComplete = true

                                if (checkDndPermission(context) && !FlipToShhhService.isRunning.value) {
                                    startFlipService(context)
                                }
                            }
                        )
                    } else {
                        MainAppContent(
                            hasDndPermission = hasDndPermission,
                            hasAccessibilityPermission = hasAccessibilityPermission,
                            onOpenDndSettings = { openDndPermissionSettings(context) },
                            onOpenAccessibilitySettings = { openAccessibilitySettings(context) }
                        )

                        LaunchedEffect(Unit) {
                            val isUserEnabled = prefs.getBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
                            if (isUserEnabled && checkDndPermission(context) && !FlipToShhhService.isRunning.value) {
                                startFlipService(context)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAppContent(
    hasDndPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onOpenDndSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE) }

    val isServiceRunning by FlipToShhhService.isRunning.collectAsState()
    val isFlippedDown by FlipToShhhService.isFlippedDown.collectAsState()
    val isDndActive by FlipToShhhService.isDndActive.collectAsState()

    var autoStartEnabled by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_AUTO_START_BOOT, true)) }
    var autoLockPref by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, false)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showToolbarHint by remember {
        mutableStateOf(!prefs.getBoolean(PrefsKeys.KEY_TOOLBAR_HINT_SEEN, false))
    }

    // Keep local state synced if MainActivity auto-enabled KEY_AUTO_LOCK_SCREEN upon Accessibility grant
    LaunchedEffect(hasAccessibilityPermission) {
        autoLockPref = prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, false)
    }

    val effectiveAutoLock = autoLockPref && hasAccessibilityPermission

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            shape = ExpressiveShapeTokens.ExtraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = stringResource(R.string.perm_dnd_title),
                    style = ExpressiveTypographyTokens.ItemTitle
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.perm_dnd_required),
                    style = ExpressiveTypographyTokens.ItemSubtitle
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onOpenDndSettings()
                }) {
                    Text(
                        text = stringResource(R.string.grant_btn),
                        style = ExpressiveTypographyTokens.ButtonText
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        style = ExpressiveTypographyTokens.ButtonText
                    )
                }
            }
        )
    }

    HomeScreen(
        isServiceRunning = isServiceRunning,
        isFlippedDown = isFlippedDown,
        isDndActive = isDndActive,
        hasDndPermission = hasDndPermission,
        autoStartEnabled = autoStartEnabled,
        autoLockEnabled = effectiveAutoLock,
        showToolbarHint = showToolbarHint,
        onToggleService = {
            val isEffectiveRunning = isServiceRunning && hasDndPermission
            if (!isEffectiveRunning) {
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
        },
        onToggleAutoStart = { enabled ->
            autoStartEnabled = enabled
            prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_START_BOOT, enabled).apply()
        },
        onToggleAutoLock = { wantEnabled ->
            if (wantEnabled) {
                if (hasAccessibilityPermission) {
                    autoLockPref = true
                    prefs.edit()
                        .putBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, true)
                        .putBoolean(PrefsKeys.KEY_PENDING_AUTO_LOCK_AUTH, false)
                        .apply()
                } else {
                    prefs.edit().putBoolean(PrefsKeys.KEY_PENDING_AUTO_LOCK_AUTH, true).apply()
                    onOpenAccessibilitySettings()
                }
            } else {
                autoLockPref = false
                prefs.edit()
                    .putBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, false)
                    .putBoolean(PrefsKeys.KEY_PENDING_AUTO_LOCK_AUTH, false)
                    .apply()
            }
        },
        onDismissToolbarHint = {
            showToolbarHint = false
            prefs.edit().putBoolean(PrefsKeys.KEY_TOOLBAR_HINT_SEEN, true).apply()
        }
    )
}

// ════════════════════════════════════════════════════════════════════════
// Service Control Helpers
// ════════════════════════════════════════════════════════════════════════

private fun startFlipService(context: Context) {
    context.startForegroundService(Intent(context, FlipToShhhService::class.java))
}

private fun stopFlipService(context: Context) {
    context.stopService(Intent(context, FlipToShhhService::class.java))
}

// ════════════════════════════════════════════════════════════════════════
// Permission Helpers
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
        } catch (_: Exception) {
        }
    }

    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
