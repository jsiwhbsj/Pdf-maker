package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Normalized 0f..1f corner coordinates of a document quadrilateral.
 */
data class DocumentQuad(
    val topLeftX: Float = 0.04f,
    val topLeftY: Float = 0.04f,
    val topRightX: Float = 0.96f,
    val topRightY: Float = 0.04f,
    val bottomRightX: Float = 0.96f,
    val bottomRightY: Float = 0.96f,
    val bottomLeftX: Float = 0.04f,
    val bottomLeftY: Float = 0.96f
) {
    fun areaFraction(): Float {
        // Shoelace formula on normalized coordinates
        val s1 = topLeftX * topRightY + topRightX * bottomRightY + bottomRightX * bottomLeftY + bottomLeftX * topLeftY
        val s2 = topLeftY * topRightX + topRightY * bottomRightX + bottomRightY * bottomLeftX + bottomLeftY * topLeftX
        return abs(s1 - s2) * 0.5f
    }

    fun isConvexAndValid(): Boolean {
        val pts = listOf(
            topLeftX to topLeftY,
            topRightX to topRightY,
            bottomRightX to bottomRightY,
            bottomLeftX to bottomLeftY
        )
        var sign = 0f
        for (i in 0 until 4) {
            val (x1, y1) = pts[i]
            val (x2, y2) = pts[(i + 1) % 4]
            val (x3, y3) = pts[(i + 2) % 4]
            val cross = (x2 - x1) * (y3 - y2) - (y2 - y1) * (x3 - x2)
            if (abs(cross) < 1e-4f) return false
            if (sign == 0f) sign = cross else if (cross * sign < 0f) return false
        }
        val area = areaFraction()
        return area in 0.18f..0.98f
    }
}

enum class ScanEnhanceMode(val label: String) {
    COLOR("Color"),
    GRAYSCALE("Grayscale"),
    CRISP_BW("B&W"),
    ORIGINAL("Original")
}

object DocumentScannerProcessor {

    /**
     * Automatically detects the 4 corners of a document page in the photo
     * using luminance & Sobel gradient transition profiling across 4 quadrants.
     */
    fun detectDocumentQuad(source: Bitmap): DocumentQuad {
        val maxSide = 240
        val scale = maxSide.toFloat() / max(source.width, source.height)
        val w = max(32, (source.width * scale).roundToInt())
        val h = max(32, (source.height * scale).roundToInt())
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(
            source,
            null,
            android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        val luma = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            luma[i] = ((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
        }

        // Compute Sobel gradient magnitude
        val grad = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -luma[(y - 1) * w + (x - 1)] + luma[(y - 1) * w + (x + 1)] -
                    2 * luma[y * w + (x - 1)] + 2 * luma[y * w + (x + 1)] -
                    luma[(y + 1) * w + (x - 1)] + luma[(y + 1) * w + (x + 1)]
                val gy = -luma[(y - 1) * w + (x - 1)] - 2 * luma[(y - 1) * w + x] - luma[(y - 1) * w + (x + 1)] +
                    luma[(y + 1) * w + (x - 1)] + 2 * luma[(y + 1) * w + x] + luma[(y + 1) * w + (x + 1)]
                grad[y * w + x] = min(255, (abs(gx) + abs(gy)) shr 2)
            }
        }

        // Estimate center document brightness
        var centerSum = 0L
        var centerCount = 0
        for (y in (h / 3)..(2 * h / 3)) {
            for (x in (w / 3)..(2 * w / 3)) {
                centerSum += luma[y * w + x]
                centerCount++
            }
        }
        val centerBrightness = if (centerCount > 0) (centerSum / centerCount).toInt() else 180

        // Sample boundary points along top, bottom, left, right strips to fit 4 document edges
        val topPts = mutableListOf<Pair<Float, Float>>()
        val bottomPts = mutableListOf<Pair<Float, Float>>()
        val leftPts = mutableListOf<Pair<Float, Float>>()
        val rightPts = mutableListOf<Pair<Float, Float>>()

        val thresholdLuma = (centerBrightness * 0.68f).toInt().coerceIn(55, 175)

        // Vertical scanlines for top & bottom edges
        for (x in (w * 0.15f).toInt()..(w * 0.85f).toInt() step max(1, w / 28)) {
            var foundTop = -1
            for (y in 2 until h / 2) {
                if (grad[y * w + x] > 28 && luma[min(h - 1, y + 3) * w + x] >= thresholdLuma) {
                    foundTop = y
                    break
                }
            }
            if (foundTop > 0) topPts.add(x.toFloat() to foundTop.toFloat())

            var foundBottom = -1
            for (y in h - 3 downTo h / 2) {
                if (grad[y * w + x] > 28 && luma[max(0, y - 3) * w + x] >= thresholdLuma) {
                    foundBottom = y
                    break
                }
            }
            if (foundBottom > 0) bottomPts.add(x.toFloat() to foundBottom.toFloat())
        }

        // Horizontal scanlines for left & right edges
        for (y in (h * 0.15f).toInt()..(h * 0.85f).toInt() step max(1, h / 28)) {
            var foundLeft = -1
            for (x in 2 until w / 2) {
                if (grad[y * w + x] > 28 && luma[y * w + min(w - 1, x + 3)] >= thresholdLuma) {
                    foundLeft = x
                    break
                }
            }
            if (foundLeft > 0) leftPts.add(foundLeft.toFloat() to y.toFloat())

            var foundRight = -1
            for (x in w - 3 downTo w / 2) {
                if (grad[y * w + x] > 28 && luma[y * w + max(0, x - 3)] >= thresholdLuma) {
                    foundRight = x
                    break
                }
            }
            if (foundRight > 0) rightPts.add(foundRight.toFloat() to y.toFloat())
        }

        if (topPts.size >= 4 && bottomPts.size >= 4 && leftPts.size >= 4 && rightPts.size >= 4) {
            val topLine = fitLineYofX(topPts)
            val bottomLine = fitLineYofX(bottomPts)
            val leftLine = fitLineXofY(leftPts)
            val rightLine = fitLineXofY(rightPts)

            val tl = intersectYX(topLine, leftLine)
            val tr = intersectYX(topLine, rightLine)
            val br = intersectYX(bottomLine, rightLine)
            val bl = intersectYX(bottomLine, leftLine)

            if (tl != null && tr != null && br != null && bl != null) {
                val candidate = DocumentQuad(
                    topLeftX = (tl.first / w).coerceIn(0.01f, 0.45f),
                    topLeftY = (tl.second / h).coerceIn(0.01f, 0.45f),
                    topRightX = (tr.first / w).coerceIn(0.55f, 0.99f),
                    topRightY = (tr.second / h).coerceIn(0.01f, 0.45f),
                    bottomRightX = (br.first / w).coerceIn(0.55f, 0.99f),
                    bottomRightY = (br.second / h).coerceIn(0.55f, 0.99f),
                    bottomLeftX = (bl.first / w).coerceIn(0.01f, 0.45f),
                    bottomLeftY = (bl.second / h).coerceIn(0.55f, 0.99f)
                )
                if (candidate.isConvexAndValid()) {
                    return candidate
                }
            }
        }

        // Graceful fallback trim so edges stay clean when document fills the frame
        return DocumentQuad(
            topLeftX = 0.04f, topLeftY = 0.04f,
            topRightX = 0.96f, topRightY = 0.04f,
            bottomRightX = 0.96f, bottomRightY = 0.96f,
            bottomLeftX = 0.04f, bottomLeftY = 0.96f
        )
    }

    /**
     * Crops and corrects perspective using a 4-point projective homography, then applies optional rotation.
     */
    fun warpPerspectiveAndCrop(
        source: Bitmap,
        quad: DocumentQuad,
        rotationDegrees: Int = 0
    ): Bitmap {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()

        val srcPts = floatArrayOf(
            quad.topLeftX * sw, quad.topLeftY * sh,
            quad.topRightX * sw, quad.topRightY * sh,
            quad.bottomRightX * sw, quad.bottomRightY * sh,
            quad.bottomLeftX * sw, quad.bottomLeftY * sh
        )

        val topWidth = hypot(srcPts[2] - srcPts[0], srcPts[3] - srcPts[1])
        val bottomWidth = hypot(srcPts[4] - srcPts[6], srcPts[5] - srcPts[7])
        val leftHeight = hypot(srcPts[6] - srcPts[0], srcPts[7] - srcPts[1])
        val rightHeight = hypot(srcPts[4] - srcPts[2], srcPts[5] - srcPts[3])

        val outW = max(200, max(topWidth, bottomWidth).roundToInt()).coerceAtMost(2000)
        val outH = max(200, max(leftHeight, rightHeight).roundToInt()).coerceAtMost(2800)

        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val homography = Matrix()
        val mapped = homography.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val cropped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mapped) {
            canvas.drawBitmap(source, homography, paint)
        } else {
            canvas.drawBitmap(source, null, android.graphics.Rect(0, 0, outW, outH), paint)
        }

        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        if (normalizedRot == 0) return cropped

        val swapDims = normalizedRot == 90 || normalizedRot == 270
        val rotW = if (swapDims) cropped.height else cropped.width
        val rotH = if (swapDims) cropped.width else cropped.height
        val rotated = Bitmap.createBitmap(rotW, rotH, Bitmap.Config.ARGB_8888)
        val rotCanvas = Canvas(rotated)
        rotCanvas.translate(rotW / 2f, rotH / 2f)
        rotCanvas.rotate(normalizedRot.toFloat())
        rotCanvas.translate(-cropped.width / 2f, -cropped.height / 2f)
        rotCanvas.drawBitmap(cropped, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        cropped.recycle()
        return rotated
    }

    /**
     * Enhances document readability:
     * - Removes uneven lighting & shadows via local illumination normalization
     * - Boosts contrast & brightness so paper is bright and ink is crisp
     * - Applies fast unsharp luminance sharpening for crisp text
     */
    fun enhanceScannedDocument(
        source: Bitmap,
        mode: ScanEnhanceMode
    ): Bitmap {
        if (mode == ScanEnhanceMode.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute smooth 24x24 background illumination grid in pure Kotlin (no extra Bitmap allocations)
        val gridW = 24
        val gridH = 24
        val bgGrid = IntArray(gridW * gridH)
        val cellW = max(1, w / gridW)
        val cellH = max(1, h / gridH)
        for (gy in 0 until gridH) {
            val yStart = (gy * h) / gridH
            val yEnd = min(h, yStart + cellH)
            for (gx in 0 until gridW) {
                val xStart = (gx * w) / gridW
                val xEnd = min(w, xStart + cellW)
                var maxLuma = 75
                // Use 85th-percentile / local bright paper estimate in block
                var sumLuma = 0
                var count = 0
                for (y in yStart until yEnd step max(1, cellH / 4)) {
                    for (x in xStart until xEnd step max(1, cellW / 4)) {
                        val c = pixels[y * w + x]
                        val luma = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                        sumLuma += luma
                        if (luma > maxLuma) maxLuma = luma
                        count++
                    }
                }
                val avgLuma = if (count > 0) sumLuma / count else 180
                bgGrid[gy * gridW + gx] = ((maxLuma * 0.6f) + (avgLuma * 0.4f)).toInt().coerceIn(75, 255)
            }
        }

        val normPixels = IntArray(w * h)

        for (y in 0 until h) {
            val gyF = (y.toFloat() / max(1, h - 1)) * (gridH - 1)
            val gy0 = gyF.toInt().coerceIn(0, gridH - 2)
            val fy = gyF - gy0
            val rowOffset = y * w
            for (x in 0 until w) {
                val gxF = (x.toFloat() / max(1, w - 1)) * (gridW - 1)
                val gx0 = gxF.toInt().coerceIn(0, gridW - 2)
                val fx = gxF - gx0

                val c00 = bgGrid[gy0 * gridW + gx0]
                val c10 = bgGrid[gy0 * gridW + (gx0 + 1)]
                val c01 = bgGrid[(gy0 + 1) * gridW + gx0]
                val c11 = bgGrid[(gy0 + 1) * gridW + (gx0 + 1)]
                val top = c00 + (c10 - c00) * fx
                val bot = c01 + (c11 - c01) * fx
                val safeBg = max(75, (top + (bot - top) * fy).toInt())

                val i = rowOffset + x
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                when (mode) {
                    ScanEnhanceMode.CRISP_BW -> {
                        val pxLuma = (r * 77 + g * 150 + b * 29) shr 8
                        val ratio = (pxLuma * 255) / safeBg
                        val stretched = when {
                            ratio > 205 -> 255
                            ratio < 115 -> (ratio * 0.35f).toInt().coerceIn(0, 60)
                            else -> (((ratio - 115) * 255f) / 90f).toInt().coerceIn(0, 255)
                        }
                        normPixels[i] = Color.rgb(stretched, stretched, stretched)
                    }
                    ScanEnhanceMode.GRAYSCALE -> {
                        val pxLuma = (r * 77 + g * 150 + b * 29) shr 8
                        val scale = 244f / safeBg
                        val gVal = ((((pxLuma * scale).toInt() - 128) * 1.28f) + 136).toInt().coerceIn(0, 255)
                        normPixels[i] = Color.rgb(gVal, gVal, gVal)
                    }
                    ScanEnhanceMode.COLOR, ScanEnhanceMode.ORIGINAL -> {
                        val scale = 242f / safeBg
                        var nr = (r * scale).toInt()
                        var ng = (g * scale).toInt()
                        var nb = (b * scale).toInt()

                        nr = (((nr - 128) * 1.24f) + 134).toInt().coerceIn(0, 255)
                        ng = (((ng - 128) * 1.24f) + 134).toInt().coerceIn(0, 255)
                        nb = (((nb - 128) * 1.24f) + 134).toInt().coerceIn(0, 255)

                        normPixels[i] = Color.rgb(nr, ng, nb)
                    }
                }
            }
        }

        // Fast 3x3 unsharp text sharpening pass
        val sharpened = IntArray(w * h)
        normPixels.copyInto(sharpened)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = normPixels[idx]
                val up = normPixels[idx - w]
                val dn = normPixels[idx + w]
                val lf = normPixels[idx - 1]
                val rt = normPixels[idx + 1]

                val r = sharpenChannel(c shr 16 and 0xFF, up shr 16 and 0xFF, dn shr 16 and 0xFF, lf shr 16 and 0xFF, rt shr 16 and 0xFF)
                val g = sharpenChannel(c shr 8 and 0xFF, up shr 8 and 0xFF, dn shr 8 and 0xFF, lf shr 8 and 0xFF, rt shr 8 and 0xFF)
                val b = sharpenChannel(c and 0xFF, up and 0xFF, dn and 0xFF, lf and 0xFF, rt and 0xFF)
                sharpened[idx] = Color.rgb(r, g, b)
            }
        }

        return Bitmap.createBitmap(sharpened, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sharpenChannel(center: Int, up: Int, down: Int, left: Int, right: Int): Int {
        val avg = (up + down + left + right) shr 2
        val detail = center - avg
        return (center + (detail * 0.55f).toInt()).coerceIn(0, 255)
    }

    // Least squares y = m*x + b
    private fun fitLineYofX(pts: List<Pair<Float, Float>>): Pair<Float, Float> {
        val n = pts.size.toFloat()
        val sx = pts.sumOf { it.first.toDouble() }.toFloat()
        val sy = pts.sumOf { it.second.toDouble() }.toFloat()
        val sxx = pts.sumOf { (it.first * it.first).toDouble() }.toFloat()
        val sxy = pts.sumOf { (it.first * it.second).toDouble() }.toFloat()
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-4f) return 0f to (sy / n)
        val m = (n * sxy - sx * sy) / denom
        val b = (sy - m * sx) / n
        return m to b
    }

    // Least squares x = m*y + b
    private fun fitLineXofY(pts: List<Pair<Float, Float>>): Pair<Float, Float> {
        val n = pts.size.toFloat()
        val sy = pts.sumOf { it.second.toDouble() }.toFloat()
        val sx = pts.sumOf { it.first.toDouble() }.toFloat()
        val syy = pts.sumOf { (it.second * it.second).toDouble() }.toFloat()
        val syx = pts.sumOf { (it.second * it.first).toDouble() }.toFloat()
        val denom = n * syy - sy * sy
        if (abs(denom) < 1e-4f) return 0f to (sx / n)
        val m = (n * syx - sy * sx) / denom
        val b = (sx - m * sy) / n
        return m to b
    }

    // Intersect y = m1*x + b1 with x = m2*y + b2
    private fun intersectYX(yOfX: Pair<Float, Float>, xOfY: Pair<Float, Float>): Pair<Float, Float>? {
        val (m1, b1) = yOfX
        val (m2, b2) = xOfY
        val denom = 1f - m1 * m2
        if (abs(denom) < 1e-4f) return null
        val x = (m2 * b1 + b2) / denom
        val y = m1 * x + b1
        return x to y
    }
}
