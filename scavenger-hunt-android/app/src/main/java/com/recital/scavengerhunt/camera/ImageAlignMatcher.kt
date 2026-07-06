package com.recital.scavengerhunt.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class MatchResult(
    val score: Float,
    /** Move phone right (+) or left (−) to line up the ghost. */
    val panX: Float,
    /** Move phone down (+) or up (−) to line up the ghost. */
    val panY: Float
)

object ImageAlignMatcher {
    /** Combined brightness + edge match — hold steady when bar fills. */
    const val MATCH_THRESHOLD = 0.26f
    const val HOLD_MS = 600L
    private const val SAMPLE = 64
    private const val MAX_SHIFT = 12
    private const val SHIFT_STEP = 2

    fun decodeReference(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    fun match(image: ImageProxy, reference: Bitmap): MatchResult {
        val rotation = image.imageInfo.rotationDegrees
        val effW = if (rotation == 90 || rotation == 270) image.height else image.width
        val effH = if (rotation == 90 || rotation == 270) image.width else image.height
        val upright = matchAtAspect(image, reference, effW, effH, rotation)
        val swapped = matchAtAspect(image, reference, effH, effW, rotation)
        return if (upright.score >= swapped.score * 0.95f) upright else swapped
    }

    fun score(image: ImageProxy, reference: Bitmap): Float = match(image, reference).score

    fun score(live: Bitmap, reference: Bitmap): Float {
        val w = live.width
        val h = live.height
        val refGray = toGrayGridCropped(reference, w, h, SAMPLE, SAMPLE)
        val liveGray = toGrayGridCropped(live, w, h, SAMPLE, SAMPLE)
        return matchGrids(refGray, liveGray).score
    }

    private fun matchAtAspect(
        image: ImageProxy,
        reference: Bitmap,
        aspectW: Int,
        aspectH: Int,
        rotation: Int
    ): MatchResult {
        val live = sampleLiveGray(image, aspectW, aspectH, rotation) ?: return MatchResult(0f, 0f, 0f)
        val refGray = toGrayGridCropped(reference, aspectW, aspectH, SAMPLE, SAMPLE)
        return matchGrids(refGray, live)
    }

    private fun matchGrids(refGray: FloatArray, liveGray: FloatArray): MatchResult {
        val refGrad = gradientGrid(refGray, SAMPLE, SAMPLE)
        val liveGrad = gradientGrid(liveGray, SAMPLE, SAMPLE)

        var bestScore = 0f
        var bestShiftX = 0
        var bestShiftY = 0

        for (sy in -MAX_SHIFT..MAX_SHIFT step SHIFT_STEP) {
            for (sx in -MAX_SHIFT..MAX_SHIFT step SHIFT_STEP) {
                val brightness = shiftedCorrelation(refGray, liveGray, SAMPLE, SAMPLE, sx, sy)
                val edges = shiftedCorrelation(refGrad, liveGrad, SAMPLE, SAMPLE, sx, sy)
                val score = (brightness * 0.35f + edges * 0.65f).coerceIn(0f, 1f)
                if (score > bestScore) {
                    bestScore = score
                    bestShiftX = sx
                    bestShiftY = sy
                }
            }
        }

        // Positive shift = live content sits left/up of ghost → move phone right/down.
        val panX = (bestShiftX.toFloat() / MAX_SHIFT).coerceIn(-1f, 1f)
        val panY = (bestShiftY.toFloat() / MAX_SHIFT).coerceIn(-1f, 1f)
        return MatchResult(bestScore, panX, panY)
    }

    private fun shiftedCorrelation(
        ref: FloatArray,
        live: FloatArray,
        w: Int,
        h: Int,
        shiftX: Int,
        shiftY: Int
    ): Float {
        var sumA = 0f
        var sumB = 0f
        var count = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val lx = x + shiftX
                val ly = y + shiftY
                if (lx !in 0 until w || ly !in 0 until h) continue
                val a = ref[y * w + x]
                val b = live[ly * w + lx]
                sumA += a
                sumB += b
                count++
            }
        }
        if (count < w * h / 4) return 0f
        val meanA = sumA / count
        val meanB = sumB / count
        var num = 0f
        var denA = 0f
        var denB = 0f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val lx = x + shiftX
                val ly = y + shiftY
                if (lx !in 0 until w || ly !in 0 until h) continue
                val da = ref[y * w + x] - meanA
                val db = live[ly * w + lx] - meanB
                num += da * db
                denA += da * da
                denB += db * db
            }
        }
        val den = sqrt(denA * denB)
        if (den <= 1e-6f) return 0f
        return (num / den).coerceIn(0f, 1f)
    }

    private fun sampleLiveGray(
        image: ImageProxy,
        aspectW: Int,
        aspectH: Int,
        rotation: Int
    ): FloatArray? {
        val w = image.width
        val h = image.height
        val effW = if (rotation == 90 || rotation == 270) h else w
        val effH = if (rotation == 90 || rotation == 270) w else h
        val crop = centerAspectCrop(effW, effH, aspectW, aspectH)
        val out = FloatArray(SAMPLE * SAMPLE)
        var idx = 0
        for (ty in 0 until SAMPLE) {
            val dy = crop.top + (ty + 0.5f) * crop.height() / SAMPLE
            for (tx in 0 until SAMPLE) {
                val dx = crop.left + (tx + 0.5f) * crop.width() / SAMPLE
                val (sx, sy) = uprightToSensor(dx, dy, w, h, rotation)
                out[idx++] = readGray(image, sx, sy)
            }
        }
        return out
    }

    private fun gradientGrid(gray: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(gray.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val l = gray[y * w + max(x - 1, 0)]
                val r = gray[y * w + min(x + 1, w - 1)]
                val u = gray[max(y - 1, 0) * w + x]
                val d = gray[min(y + 1, h - 1) * w + x]
                out[y * w + x] = sqrt((r - l) * (r - l) + (d - u) * (d - u))
            }
        }
        return out
    }

    private fun uprightToSensor(dx: Float, dy: Float, w: Int, h: Int, rotation: Int): Pair<Int, Int> =
        when (rotation) {
            90 -> Pair(dy.toInt().coerceIn(0, w - 1), (h - 1 - dx).toInt().coerceIn(0, h - 1))
            180 -> Pair((w - 1 - dx).toInt().coerceIn(0, w - 1), (h - 1 - dy).toInt().coerceIn(0, h - 1))
            270 -> Pair((w - 1 - dy).toInt().coerceIn(0, w - 1), dx.toInt().coerceIn(0, h - 1))
            else -> Pair(dx.toInt().coerceIn(0, w - 1), dy.toInt().coerceIn(0, h - 1))
        }

    private fun readGray(image: ImageProxy, col: Int, row: Int): Float {
        val yPlane = image.planes[0]
        val buf = yPlane.buffer.duplicate()
        val offset = row * yPlane.rowStride + col * yPlane.pixelStride
        if (offset !in 0 until buf.limit()) return 0f
        return (buf.get(offset).toInt() and 0xFF) / 255f
    }

    private fun centerAspectCrop(frameW: Int, frameH: Int, aspectW: Int, aspectH: Int): Rect {
        if (aspectW <= 0 || aspectH <= 0) {
            return Rect(0, 0, frameW, frameH)
        }
        val targetAspect = aspectW.toFloat() / aspectH
        val frameAspect = frameW.toFloat() / frameH
        val cw: Int
        val ch: Int
        if (frameAspect > targetAspect) {
            ch = frameH
            cw = (frameH * targetAspect).toInt().coerceAtLeast(1)
        } else {
            cw = frameW
            ch = (frameW / targetAspect).toInt().coerceAtLeast(1)
        }
        val left = (frameW - cw) / 2
        val top = (frameH - ch) / 2
        return Rect(left, top, left + cw, top + ch)
    }

    private fun toGrayGridCropped(source: Bitmap, aspectW: Int, aspectH: Int, w: Int, h: Int): FloatArray {
        val crop = centerAspectCrop(source.width, source.height, aspectW, aspectH)
        val cropped = Bitmap.createBitmap(
            source,
            crop.left.coerceIn(0, source.width - 1),
            crop.top.coerceIn(0, source.height - 1),
            crop.width().coerceIn(1, source.width - crop.left),
            crop.height().coerceIn(1, source.height - crop.top)
        )
        val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)
        if (cropped != source) cropped.recycle()
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != source) scaled.recycle()
        return FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
    }

    fun alignmentHintLabel(panX: Float, panY: Float, score: Float): String? {
        if (score >= MATCH_THRESHOLD - 0.02f) return null
        val parts = mutableListOf<String>()
        if (abs(panX) >= 0.22f) {
            parts += if (panX > 0) "Move right →" else "Move left ←"
        }
        if (abs(panY) >= 0.22f) {
            parts += if (panY > 0) "Move down ↓" else "Move up ↑"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
    }
}
