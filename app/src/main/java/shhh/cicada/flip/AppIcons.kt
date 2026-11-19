package shhh.cicada.flip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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

    val AutoMode: ImageVector by lazy {
        ImageVector.Builder(
            name = "AutoMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            // Solid arc on the right-bottom (from -75 deg to +65 deg)
            moveTo(14.33f, 3.31f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.8f, 20.16f)
            // Segmented dashes on the left side
            moveTo(11.2f, 20.96f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.5f, 19.8f)
            moveTo(4.86f, 17.4f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.22f, 14f)
            moveTo(3.22f, 10f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.86f, 6.6f)
            moveTo(7.5f, 4.2f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11.2f, 3.04f)
        }.path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            // Centered 'A' glyph
            moveTo(11.25f, 8f)
            lineTo(12.75f, 8f)
            lineTo(15.2f, 16f)
            lineTo(13.6f, 16f)
            lineTo(13.0f, 14f)
            lineTo(11.0f, 14f)
            lineTo(10.4f, 16f)
            lineTo(8.8f, 16f)
            close()
            moveTo(12.0f, 10.1f)
            lineTo(11.35f, 12.6f)
            lineTo(12.65f, 12.6f)
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

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineToRelative(-1.42f, 1.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineToRelative(-1.41f, -1.41f)
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
            reflectiveCurveTo(7f, 3.24f, 7f, 6f)
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
            reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
            reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
            reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
            close()
            moveTo(15.1f, 8f)
            lineTo(8.9f, 8f)
            lineTo(8.9f, 6f)
            curveTo(8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f)
            reflectiveCurveToRelative(3.1f, 1.39f, 3.1f, 3.1f)
            verticalLineToRelative(2f)
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
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
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
}
