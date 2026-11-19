package shhh.cicada.flip.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import shhh.cicada.flip.AppIcons
import shhh.cicada.flip.R
import shhh.cicada.flip.ui.theme.ExpressiveMotionTokens
import shhh.cicada.flip.ui.theme.ExpressiveShapeTokens
import shhh.cicada.flip.ui.theme.ExpressiveTypographyTokens

/**
 * Material 3 Expressive (M3E) One-Time Onboarding Flow.
 *
 * Centralizes initial DND & Unrestricted Battery permissions so that once
 * onboarding completes, permission items stay hidden from the daily UI.
 */
@Composable
fun OnboardingScreen(
    hasDndPermission: Boolean,
    isIgnoringBatteryOptimization: Boolean,
    onGrantDnd: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val allGranted = hasDndPermission

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingWelcomePage(
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 1,
                                animationSpec = ExpressiveMotionTokens.defaultSpatial()
                            )
                        }
                    }
                )
                1 -> OnboardingPermissionsPage(
                    hasDndPermission = hasDndPermission,
                    isIgnoringBatteryOptimization = isIgnoringBatteryOptimization,
                    onGrantDnd = onGrantDnd,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                    allGranted = allGranted,
                    onComplete = onComplete
                )
            }
        }

        // M3E Dynamic Morphing Page Indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    val isSelected = pagerState.currentPage == index
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 32.dp else 8.dp,
                        animationSpec = ExpressiveMotionTokens.fastSpatial(),
                        label = "indicatorWidth"
                    )
                    val indicatorColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        animationSpec = ExpressiveMotionTokens.fastEffects(),
                        label = "indicatorColor"
                    )

                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(indicatorWidth)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage(
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(ExpressiveShapeTokens.ExtraExtraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.DoNotDisturbOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        FilledTonalButton(
            onClick = onNext,
            shape = ExpressiveShapeTokens.Full,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_next_btn),
                style = ExpressiveTypographyTokens.ButtonText
            )
        }
    }
}

@Composable
private fun OnboardingPermissionsPage(
    hasDndPermission: Boolean,
    isIgnoringBatteryOptimization: Boolean,
    onGrantDnd: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    allGranted: Boolean,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_perm_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // M3E Segmented Permission Cards (2dp gap, 28dp outer / 6dp inner corners)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OnboardingPermissionItem(
                icon = AppIcons.DoNotDisturbOn,
                title = stringResource(R.string.perm_dnd_title),
                isGranted = hasDndPermission,
                grantLabel = stringResource(R.string.grant_btn),
                shape = ExpressiveShapeTokens.SegmentedTop,
                onGrant = onGrantDnd
            )

            OnboardingPermissionItem(
                icon = AppIcons.BatterySaver,
                title = stringResource(R.string.perm_battery_title),
                isGranted = isIgnoringBatteryOptimization,
                grantLabel = stringResource(R.string.grant_btn),
                shape = ExpressiveShapeTokens.SegmentedBottom,
                onGrant = onRequestBatteryOptimization
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onComplete,
            enabled = allGranted,
            shape = ExpressiveShapeTokens.Full,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_start_btn),
                style = ExpressiveTypographyTokens.ButtonText
            )
        }
    }
}

@Composable
private fun OnboardingPermissionItem(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    grantLabel: String,
    shape: Shape,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(ExpressiveShapeTokens.Medium)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ExpressiveTypographyTokens.ItemTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Button(
                onClick = onGrant,
                shape = ExpressiveShapeTokens.Full,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = grantLabel,
                    style = ExpressiveTypographyTokens.ButtonText
                )
            }
        }
    }
}
