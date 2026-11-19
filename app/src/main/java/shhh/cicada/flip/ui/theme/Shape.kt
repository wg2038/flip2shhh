package shhh.cicada.flip.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive (M3E) Shape Scale Specifications.
 */
val AppExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object ExpressiveShapeTokens {
    val Medium = RoundedCornerShape(14.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val ExtraExtraLarge = RoundedCornerShape(48.dp)
    val Full = CircleShape

    // M3E Segmented List Group Shapes (Android 16 / M3 Expressive standard)
    val SegmentedTop = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 6.dp,
        bottomEnd = 6.dp
    )
    val SegmentedBottom = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )
}
