package shhh.cicada.flip.ui.theme

import android.graphics.Matrix as AndroidMatrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Official Material 3 Expressive (M3E) Shape System & Sequence.
 *
 * Implements the 7 official normalized RoundedPolygon shapes:
 * 1. SoftBurst (柔边星爆形)
 * 2. Cookie9Sided (9 边波浪曲奇形)
 * 3. Pentagon (圆角五边形)
 * 4. Pill (胶囊形)
 * 5. Sunny (多齿太阳形)
 * 6. Cookie4Sided (4 边波浪曲奇形)
 * 7. Oval (椭圆形)
 */
object M3ExpressiveShapes {

    private val cornerRound15 = CornerRounding(radius = 0.15f)
    private val cornerRound50 = CornerRounding(radius = 0.5f)

    private fun rotateMatrix(degrees: Float): AndroidMatrix =
        AndroidMatrix().apply { preRotate(degrees) }

    val SoftBurst: RoundedPolygon by lazy {
        customPolygon(
            listOf(
                PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
                PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f))
            ),
            reps = 10
        ).normalized()
    }

    val Cookie9Sided: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 9,
            innerRadius = 0.8f,
            rounding = cornerRound50
        ).transformed(rotateMatrix(-90f)).normalized()
    }

    val Pentagon: RoundedPolygon by lazy {
        customPolygon(
            listOf(
                PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
                PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
                PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f))
            ),
            reps = 1,
            mirroring = true
        ).normalized()
    }

    val Pill: RoundedPolygon by lazy {
        customPolygon(
            listOf(
                PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
                PointNRound(Offset(1.001f, 0.428f)),
                PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f))
            ),
            reps = 2,
            mirroring = true
        ).normalized()
    }

    val Sunny: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.8f,
            rounding = cornerRound15
        ).normalized()
    }

    val Cookie4Sided: RoundedPolygon by lazy {
        customPolygon(
            listOf(
                PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
                PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f))
            ),
            reps = 4
        ).normalized()
    }

    val Oval: RoundedPolygon by lazy {
        val scaleAndRotate = AndroidMatrix().apply {
            setScale(1f, 0.64f)
            postRotate(-45f)
        }
        RoundedPolygon.circle().transformed(scaleAndRotate).normalized()
    }

    val sequence: List<RoundedPolygon> by lazy {
        listOf(
            SoftBurst,
            Cookie9Sided,
            Pentagon,
            Pill,
            Sunny,
            Cookie4Sided,
            Oval
        )
    }

    fun buildMorphSequence(): List<Morph> {
        val list = sequence
        return List(list.size) { i ->
            Morph(list[i], list[(i + 1) % list.size])
        }
    }

    fun calculateScaleFactor(polygons: List<RoundedPolygon>): Float {
        var scaleFactor = 1f
        val bounds = FloatArray(4)
        val maxBounds = FloatArray(4)
        for (polygon in polygons) {
            polygon.calculateBounds(bounds)
            polygon.calculateMaxBounds(maxBounds)
            val scaleX = bounds.width() / maxBounds.width()
            val scaleY = bounds.height() / maxBounds.height()
            scaleFactor = min(scaleFactor, max(scaleX, scaleY))
        }
        return scaleFactor
    }

    fun processPath(
        path: Path,
        size: Size,
        scaleFactor: Float,
        matrix: Matrix
    ): Path {
        matrix.reset()
        matrix.scale(size.width * scaleFactor, size.height * scaleFactor)
        path.transform(matrix)
        val pathBounds = path.getBounds()
        val centerOffset = size.center - pathBounds.center
        path.translate(centerOffset)
        return path
    }

    private fun FloatArray.width(): Float = this[2] - this[0]
    private fun FloatArray.height(): Float = this[3] - this[1]

    private data class PointNRound(
        val o: Offset,
        val r: CornerRounding = CornerRounding.Unrounded
    )

    private fun customPolygon(
        pnr: List<PointNRound>,
        reps: Int,
        center: Offset = Offset(0.5f, 0.5f),
        mirroring: Boolean = false
    ): RoundedPolygon {
        val actualPoints = doRepeat(pnr, reps, center, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(actualPoints.size * 2) { ix ->
                val pt = actualPoints[ix / 2].o
                if (ix % 2 == 0) pt.x else pt.y
            },
            perVertexRounding = actualPoints.map { it.r },
            centerX = center.x,
            centerY = center.y
        )
    }

    private fun doRepeat(
        points: List<PointNRound>,
        reps: Int,
        center: Offset,
        mirroring: Boolean
    ): List<PointNRound> =
        if (mirroring) {
            buildList {
                val angles = points.map { (it.o - center).angleDegrees() }
                val distances = points.map { (it.o - center).getDistance() }
                val actualReps = reps * 2
                val sectionAngle = 360f / actualReps
                repeat(actualReps) { rep ->
                    points.indices.forEach { index ->
                        val i = if (rep % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || rep % 2 == 0) {
                            val a = (sectionAngle * rep +
                                    if (rep % 2 == 0) angles[i]
                                    else sectionAngle - angles[i] + 2 * angles[0]).toRadians()
                            val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                            add(PointNRound(finalPoint, points[i].r))
                        }
                    }
                }
            }
        } else {
            val np = points.size
            (0 until np * reps).map { idx ->
                val point = points[idx % np].o.rotateDegrees((idx / np) * 360f / reps, center)
                PointNRound(point, points[idx % np].r)
            }
        }

    private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero): Offset {
        val a = angle.toRadians()
        val off = this - center
        return Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
    }

    private fun Float.toRadians(): Float = this / 360f * (2 * PI.toFloat())
    private fun Offset.angleDegrees(): Float = atan2(y, x) * 180f / PI.toFloat()
}
