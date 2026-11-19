package shhh.cicada.flip.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import shhh.cicada.flip.AppIcons
import shhh.cicada.flip.R
import shhh.cicada.flip.ui.components.ExpressiveFloatingToolbar
import shhh.cicada.flip.ui.components.ExpressiveHeroCenterpiece
import shhh.cicada.flip.ui.theme.ExpressiveMotionTokens
import shhh.cicada.flip.ui.theme.ExpressiveShapeTokens
import shhh.cicada.flip.ui.theme.ExpressiveTypographyTokens

private const val MIT_LICENSE_TEXT = """MIT License

Copyright (c) 2026 Cicada

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

/**
 * Material 3 Expressive (M3E) Single-Screen Home Dashboard.
 *
 * Uses independent 3-layer Box anchoring:
 *  - TopStart: left-aligned M3E headline directly beneath the status bar (single statusBarsPadding)
 *  - Center: morphing hero centerpiece optically centered (-8dp) on the full viewport
 *  - BottomCenter: borderless M3E floating toolbar & one-time compact tooltip above navigation bar
 */
@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    isFlippedDown: Boolean,
    isDndActive: Boolean,
    hasDndPermission: Boolean,
    autoStartEnabled: Boolean,
    autoLockEnabled: Boolean,
    showToolbarHint: Boolean,
    onToggleService: () -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onDismissToolbarHint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEffectiveRunning = isServiceRunning && hasDndPermission
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .widthIn(max = 360.dp),
            shape = ExpressiveShapeTokens.ExtraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = ExpressiveTypographyTokens.ItemTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_version),
                        style = ExpressiveTypographyTokens.ItemSubtitle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.about_developer),
                        style = ExpressiveTypographyTokens.ItemSubtitle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = MIT_LICENSE_TEXT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(
                        text = stringResource(R.string.btn_close),
                        style = ExpressiveTypographyTokens.ButtonText
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // 1. Top-left M3E headline directly beneath status bar (no double inset)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            )

            // 2. Optically centered Hero centerpiece (-8dp upward compensation)
            ExpressiveHeroCenterpiece(
                isRunning = isEffectiveRunning,
                isFlippedDown = isFlippedDown,
                isDndActive = isDndActive,
                onToggle = onToggleService,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-8).dp)
            )

            // 3. Bottom M3E Horizontal Floating Toolbar
            ExpressiveFloatingToolbar(
                autoStartEnabled = autoStartEnabled,
                autoLockEnabled = autoLockEnabled,
                onToggleAutoStart = {
                    if (showToolbarHint) onDismissToolbarHint()
                    onToggleAutoStart(it)
                },
                onToggleAutoLock = {
                    if (showToolbarHint) onDismissToolbarHint()
                    onToggleAutoLock(it)
                },
                onOpenAbout = { showAboutDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )

            // 4. First-launch compact M3E card tooltip anchored above the toolbar
            AnimatedVisibility(
                visible = showToolbarHint,
                enter = scaleIn(
                    animationSpec = ExpressiveMotionTokens.defaultSpatial(),
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ) + fadeIn(ExpressiveMotionTokens.fastEffects()),
                exit = scaleOut(
                    animationSpec = ExpressiveMotionTokens.fastEffects(),
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ) + fadeOut(ExpressiveMotionTokens.fastEffects()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            ) {
                ToolbarOnboardingTooltip(onDismiss = onDismissToolbarHint)
            }
        }
    }
}

@Composable
private fun ToolbarOnboardingTooltip(onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TooltipLegendRow(
                icon = AppIcons.AutoMode,
                label = stringResource(R.string.toolbar_autostart)
            )
            TooltipLegendRow(
                icon = AppIcons.Lock,
                label = stringResource(R.string.toolbar_autolock)
            )
        }
    }
}

@Composable
private fun TooltipLegendRow(
    icon: ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = ExpressiveTypographyTokens.ItemSubtitle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
