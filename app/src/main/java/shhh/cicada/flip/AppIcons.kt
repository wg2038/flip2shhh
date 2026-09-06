package shhh.cicada.flip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight custom Vector Icons for Flip to Shhh.
 * Replaces the 40MB+ material-icons-extended dependency with lightweight vector paths.
 */
object AppIcons {

    val DoNotDisturbOn: ImageVector by lazy {
        ImageVector.Builder(
            name = "DoNotDisturbOn",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(17f, 13f)
            lineTo(7f, 13f)
            lineTo(7f, 11f)
            lineTo(17f, 11f)
            lineTo(17f, 13f)
            close()
        }.build()
    }

    val Bedtime: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bedtime",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12.3f, 2f)
            curveTo(6.58f, 2f, 2f, 6.58f, 2f, 12.3f)
            curveTo(2f, 17.75f, 6.17f, 22.19f, 11.5f, 22.5f)
            curveTo(10.53f, 20.91f, 10f, 19.04f, 10f, 17f)
            curveTo(10f, 11.48f, 14.48f, 7f, 20f, 7f)
            curveTo(20.73f, 7f, 21.43f, 7.08f, 22.12f, 7.23f)
            curveTo(20.7f, 4.14f, 16.82f, 2f, 12.3f, 2f)
            close()
        }.build()
    }

    val PowerSettingsNew: ImageVector by lazy {
        ImageVector.Builder(
            name = "PowerSettingsNew",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(10f)
            horizontalLineToRelative(2f)
            lineTo(13f, 3f)
            close()
            moveTo(17.83f, 5.17f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(17.99f, 7.86f, 19f, 9.81f, 19f, 12f)
            curveTo(19f, 15.87f, 15.87f, 19f, 12f, 19f)
            curveTo(8.13f, 19f, 5f, 15.87f, 5f, 12f)
            curveTo(5f, 9.81f, 6.01f, 7.86f, 7.58f, 6.59f)
            lineTo(6.17f, 5.17f)
            curveTo(4.23f, 6.82f, 3f, 9.26f, 3f, 12f)
            curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
            curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
            curveTo(21f, 9.26f, 19.77f, 6.82f, 17.83f, 5.17f)
            close()
        }.build()
    }

    val NotificationsActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "NotificationsActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(7.58f, 4.08f)
            lineTo(6.15f, 2.65f)
            curveTo(3.75f, 4.48f, 2.17f, 7.3f, 2.03f, 10.5f)
            horizontalLineToRelative(2f)
            curveTo(4.16f, 7.86f, 5.48f, 5.54f, 7.58f, 4.08f)
            close()
            moveTo(16.42f, 4.08f)
            curveTo(18.52f, 5.54f, 19.84f, 7.86f, 19.97f, 10.5f)
            horizontalLineToRelative(2f)
            curveTo(21.83f, 7.3f, 20.25f, 4.48f, 17.85f, 2.65f)
            lineToRelative(-1.43f, 1.43f)
            close()
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            horizontalLineToRelative(-4f)
            curveTo(10f, 21.1f, 10.89f, 22f, 12f, 22f)
            close()
            moveTo(18f, 16f)
            verticalLineToRelative(-5f)
            curveTo(18f, 7.93f, 15.36f, 5.36f, 12f, 4.5f)
            curveTo(8.64f, 5.36f, 6f, 7.92f, 6f, 11f)
            verticalLineToRelative(5f)
            lineToRelative(-2f, 2f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-1f)
            lineToRelative(-2f, -2f)
            close()
        }.build()
    }

    val BatterySaver: ImageVector by lazy {
        ImageVector.Builder(
            name = "BatterySaver",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 4f)
            horizontalLineToRelative(-2f)
            lineTo(14f, 2f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(2f)
            lineTo(8f, 4f)
            curveTo(6.9f, 4f, 6f, 4.9f, 6f, 6f)
            verticalLineToRelative(14f)
            curveTo(6f, 21.1f, 6.9f, 22f, 8f, 22f)
            horizontalLineToRelative(8f)
            curveTo(17.1f, 22f, 18f, 21.1f, 18f, 20f)
            lineTo(18f, 6f)
            curveTo(18f, 4.9f, 17.1f, 4f, 16f, 4f)
            close()
            moveTo(15f, 14f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            lineTo(9f, 14f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineToRelative(-4.58f, 4.59f)
            lineTo(10f, 18f)
            lineToRelative(6f, -6f)
            close()
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineToRelative(6f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
            lineTo(21f, 5f)
            lineTo(12f, 1f)
            close()
        }.build()
    }

    val Code: ImageVector by lazy {
        ImageVector.Builder(
            name = "Code",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(9.4f, 16.6f)
            lineTo(4.8f, 12f)
            lineToRelative(4.6f, -4.6f)
            lineTo(8f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(1.4f, -1.4f)
            close()
            moveTo(14.6f, 16.6f)
            lineTo(19.2f, 12f)
            lineToRelative(-4.6f, -4.6f)
            lineTo(16f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(-6f, 6f)
            lineToRelative(-1.4f, -1.4f)
            close()
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(10f, 17f)
            lineTo(5f, 12f)
            lineToRelative(1.41f, -1.41f)
            lineTo(10f, 14.17f)
            lineToRelative(7.59f, -7.59f)
            lineTo(19f, 8f)
            lineToRelative(-9f, 9f)
            close()
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 8f)
            horizontalLineToRelative(-1f)
            lineTo(17f, 6f)
            curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f)
            verticalLineToRelative(2f)
            lineTo(6f, 8f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f)
            verticalLineToRelative(10f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineToRelative(12f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            lineTo(20f, 10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f)
            close()
            moveTo(12f, 17f)
            curveTo(10.9f, 17f, 10f, 16.1f, 10f, 15f)
            curveTo(10f, 13.9f, 10.9f, 13f, 12f, 13f)
            curveTo(13.1f, 13f, 14f, 13.9f, 14f, 15f)
            curveTo(14f, 16.1f, 13.1f, 17f, 12f, 17f)
            close()
            moveTo(15.1f, 8f)
            lineTo(8.9f, 8f)
            lineTo(8.9f, 6f)
            curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
            curveTo(13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f)
            verticalLineToRelative(2f)
            close()
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "Warning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(1f, 21f)
            horizontalLineToRelative(22f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 14f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            close()
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(13f, 17f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            close()
            moveTo(13f, 9f)
            horizontalLineToRelative(-2f)
            lineTo(11f, 7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f)
            curveTo(2f, 17.51f, 6.49f, 22f, 12f, 22f)
            curveTo(13.38f, 22f, 14.5f, 20.88f, 14.5f, 19.5f)
            curveTo(14.5f, 18.89f, 14.27f, 18.3f, 13.86f, 17.83f)
            curveTo(13.78f, 17.73f, 13.73f, 17.62f, 13.73f, 17.5f)
            curveTo(13.73f, 17.22f, 13.95f, 17f, 14.23f, 17f)
            lineTo(16f, 17f)
            curveTo(19.31f, 17f, 22f, 14.31f, 22f, 11f)
            curveTo(22f, 6.04f, 17.51f, 2f, 12f, 2f)
            close()
            moveTo(17.5f, 13f)
            curveTo(16.67f, 13f, 16f, 12.33f, 16f, 11.5f)
            curveTo(16f, 10.67f, 16.67f, 10f, 17.5f, 10f)
            curveTo(18.33f, 10f, 19f, 10.67f, 19f, 11.5f)
            curveTo(19f, 12.33f, 18.33f, 13f, 17.5f, 13f)
            close()
            moveTo(14.5f, 9f)
            curveTo(13.67f, 9f, 13f, 8.33f, 13f, 7.5f)
            curveTo(13f, 6.67f, 13.67f, 6f, 14.5f, 6f)
            curveTo(15.33f, 6f, 16f, 6.67f, 16f, 7.5f)
            curveTo(16f, 8.33f, 15.33f, 9f, 14.5f, 9f)
            close()
            moveTo(8.5f, 9f)
            curveTo(7.67f, 9f, 7f, 8.33f, 7f, 7.5f)
            curveTo(7f, 6.67f, 7.67f, 6f, 8.5f, 6f)
            curveTo(9.33f, 6f, 10f, 6.67f, 10f, 7.5f)
            curveTo(10f, 8.33f, 9.33f, 9f, 8.5f, 9f)
            close()
        }.build()
    }

    val Translate: ImageVector by lazy {
        ImageVector.Builder(
            name = "Translate",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(18.92f, 8f)
            horizontalLineToRelative(-2.95f)
            curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
            curveTo(16.43f, 5.07f, 17.96f, 6.35f, 18.92f, 8f)
            close()
            moveTo(12f, 4.04f)
            curveTo(12.83f, 5.24f, 13.48f, 6.57f, 13.91f, 8f)
            lineTo(10.09f, 8f)
            curveTo(10.52f, 6.57f, 11.17f, 5.24f, 12f, 4.04f)
            close()
            moveTo(4.26f, 14f)
            curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
            curveTo(4f, 11.31f, 4.1f, 10.64f, 4.26f, 10f)
            horizontalLineToRelative(3.38f)
            curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
            curveTo(7.5f, 12.68f, 7.56f, 13.34f, 7.64f, 14f)
            lineTo(4.26f, 14f)
            close()
            moveTo(5.08f, 16f)
            horizontalLineToRelative(2.95f)
            curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
            curveTo(7.57f, 18.93f, 6.04f, 17.65f, 5.08f, 16f)
            close()
            moveTo(8.03f, 8f)
            horizontalLineTo(5.08f)
            curveTo(6.04f, 6.34f, 7.57f, 5.07f, 9.41f, 4.44f)
            curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
            close()
            moveTo(12f, 19.96f)
            curveTo(11.17f, 18.76f, 10.52f, 17.43f, 10.09f, 16f)
            horizontalLineToRelative(3.82f)
            curveTo(13.48f, 17.43f, 12.83f, 18.76f, 12f, 19.96f)
            close()
            moveTo(14.34f, 14f)
            lineTo(9.66f, 14f)
            curveTo(9.57f, 13.34f, 9.5f, 12.68f, 9.5f, 12f)
            curveTo(9.5f, 11.32f, 9.57f, 10.65f, 9.66f, 10f)
            horizontalLineToRelative(4.68f)
            curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
            curveTo(14.5f, 12.68f, 14.43f, 13.34f, 14.34f, 14f)
            close()
            moveTo(14.59f, 19.56f)
            curveTo(15.19f, 18.45f, 15.65f, 17.25f, 15.97f, 16f)
            horizontalLineToRelative(2.95f)
            curveTo(17.96f, 17.65f, 16.43f, 18.93f, 14.59f, 19.56f)
            close()
            moveTo(16.36f, 14f)
            curveTo(16.44f, 13.34f, 16.5f, 12.68f, 16.5f, 12f)
            curveTo(16.5f, 11.32f, 16.44f, 10.65f, 16.36f, 10f)
            horizontalLineToRelative(3.38f)
            curveTo(19.9f, 10.64f, 20f, 11.31f, 20f, 12f)
            curveTo(20f, 12.69f, 19.9f, 13.36f, 19.74f, 14f)
            lineTo(16.36f, 14f)
            close()
        }.build()
    }
}
