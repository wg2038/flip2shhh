package shhh.cicada.flip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import shhh.cicada.flip.AppIcons
import shhh.cicada.flip.R
import shhh.cicada.flip.ui.theme.ExpressiveMotionTokens

/**
 * Material 3 Expressive (M3E) Horizontal Floating Toolbar.
 *
 * Follows M3E FloatingToolbar specifications:
 *  - Borderless tonal surface (`surfaceContainerHigh`) with soft elevation (`shadowElevation = 3.dp`)
 *    so it floats naturally above the background without heavy outline borders.
 *  - Icon-only 48dp touch-target actions:
 *      1. Auto-start on Boot (AutoMode shape-morphing toggle)
 *      2. Flip to Lock Screen (Lock shape-morphing toggle)
 *      3. Version & MIT License (Info action button)
 */
@Composable
fun ExpressiveFloatingToolbar(
    autoStartEnabled: Boolean,
    autoLockEnabled: Boolean,
    onToggleAutoStart: (Boolean) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIconToggle(
                icon = AppIcons.AutoMode,
                description = stringResource(R.string.toolbar_autostart),
                checked = autoStartEnabled,
                onToggle = { onToggleAutoStart(!autoStartEnabled) }
            )

            ToolbarIconToggle(
                icon = AppIcons.Lock,
                description = stringResource(R.string.toolbar_autolock),
                checked = autoLockEnabled,
                onToggle = { onToggleAutoLock(!autoLockEnabled) }
            )

            ToolbarIconAction(
                icon = AppIcons.Info,
                description = stringResource(R.string.toolbar_about),
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
private fun ToolbarIconToggle(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 24.dp else 14.dp,
        animationSpec = ExpressiveMotionTokens.fastSpatial(),
        label = "toolbarToggleCorner"
    )

    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotionTokens.fastEffects(),
        label = "toolbarToggleBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotionTokens.fastEffects(),
        label = "toolbarToggleContent"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .semantics { contentDescription = description }
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ToolbarIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
