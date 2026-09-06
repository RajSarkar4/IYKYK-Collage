package com.iykyk.collage.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.iykyk.collage.domain.model.PersonCluster
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Android Canvas-based high-quality collage generator.
 * Produces an Instagram Story-inspired 1080x1920 portrait collage displaying every detected
 * person exactly once with generous context-preserving crops and appearance count badges.
 */
class CollageGenerator {

    companion object {
        private const val OUTPUT_WIDTH = 1080
        private const val OUTPUT_HEIGHT = 1920
        private const val CORNER_RADIUS = 32f
        private const val PADDING = 40f
        private const val HEADER_HEIGHT = 200f
    }

    /**
     * Renders a portrait collage Bitmap representing each unique person cluster exactly once.
     */
    fun generateCollage(clusters: List<PersonCluster>): Bitmap {
        val bitmap = Bitmap.createBitmap(OUTPUT_WIDTH, OUTPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawHeader(canvas, clusters.size)

        if (clusters.isEmpty()) {
            drawEmptyState(canvas)
            return bitmap
        }

        drawPersonGrid(canvas, clusters)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, OUTPUT_HEIGHT.toFloat(),
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E293B"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat(), bgPaint)

        // Accent top glow line
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, OUTPUT_WIDTH.toFloat(), 0f,
                Color.parseColor("#6366F1"),
                Color.parseColor("#EC4899"),
                Shader.TileMode.CLAMP
            )
            strokeWidth = 10f
        }
        canvas.drawLine(0f, 0f, OUTPUT_WIDTH.toFloat(), 0f, accentPaint)
    }

    private fun drawHeader(canvas: Canvas, personCount: Int) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("IYKYK COLLAGE", PADDING, 100f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val subtitleText = "$personCount Unique ${if (personCount == 1) "Person" else "People"} Identified"
        canvas.drawText(subtitleText, PADDING, 150f, subtitlePaint)
    }

    private fun drawPersonGrid(canvas: Canvas, clusters: List<PersonCluster>) {
        val count = clusters.size
        val columns = when {
            count == 1 -> 1
            count <= 4 -> 2
            else -> 2
        }

        val rows = ceil(count.toDouble() / columns).toInt()

        val availableWidth = OUTPUT_WIDTH - (PADDING * 2) - ((columns - 1) * PADDING)
        val availableHeight = OUTPUT_HEIGHT - HEADER_HEIGHT - PADDING - ((rows - 1) * PADDING)

        val cellWidth = availableWidth / columns
        val cellHeight = availableHeight / rows

        for (i in clusters.indices) {
            val cluster = clusters[i]
            val col = i % columns
            val row = i / columns

            val left = PADDING + col * (cellWidth + PADDING)
            val top = HEADER_HEIGHT + row * (cellHeight + PADDING)
            val right = left + cellWidth
            val bottom = top + cellHeight

            val rect = RectF(left, top, right, bottom)
            drawPersonCard(canvas, rect, cluster)
        }
    }

    private fun drawPersonCard(canvas: Canvas, rect: RectF, cluster: PersonCluster) {
        // Draw card background / shadow border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, borderPaint)

        val innerRect = RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f)

        val repObs = cluster.representativeObservation
        val imageBitmap = cluster.representativeBitmap

        if (imageBitmap != null && !imageBitmap.isRecycled) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(innerRect, CORNER_RADIUS - 3f, CORNER_RADIUS - 3f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            // Contextual cropping (Correction #11): Preserve environment & body context
            val sourceCropRect = calculateContextualCropRect(
                bitmapWidth = imageBitmap.width,
                bitmapHeight = imageBitmap.height,
                faceBox = repObs.boundingBox,
                obsFrameWidth = repObs.frameWidth,
                obsFrameHeight = repObs.frameHeight
            )

            val scale = max(innerRect.width() / sourceCropRect.width(), innerRect.height() / sourceCropRect.height())
            val dx = innerRect.left + (innerRect.width() - sourceCropRect.width() * scale) / 2f - sourceCropRect.left * scale
            // Top-weighted alignment (0.10f): keeps top of head & eyes safely inside upper portion of card
            val dy = innerRect.top + (innerRect.height() - sourceCropRect.height() * scale) * 0.10f - sourceCropRect.top * scale
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(imageBitmap, matrix, paint)

            canvas.restore()
        }

        // Draw badge overlay at bottom of card
        drawBadgeOverlay(canvas, innerRect, cluster)
    }

    /**
     * Calculates a generous contextual crop around the person preserving background environment,
     * avoiding extremely tight or low-res face bounding box crops.
     */
    private fun calculateContextualCropRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        faceBox: RectF,
        obsFrameWidth: Int = 1080,
        obsFrameHeight: Int = 1920
    ): RectF {
        val scaleX = if (obsFrameWidth > 0) bitmapWidth.toFloat() / obsFrameWidth else 1.0f
        val scaleY = if (obsFrameHeight > 0) bitmapHeight.toFloat() / obsFrameHeight else 1.0f

        val scaledFaceBox = RectF(
            faceBox.left * scaleX,
            faceBox.top * scaleY,
            faceBox.right * scaleX,
            faceBox.bottom * scaleY
        )

        val faceW = scaledFaceBox.width()
        val faceH = scaledFaceBox.height()

        // Expand face box generously (2.2x width, 2.8x height upward for head/hair, 3.5x height downward for torso)
        val expandedLeft = max(0f, scaledFaceBox.centerX() - (faceW * 2.2f))
        val expandedTop = max(0f, scaledFaceBox.centerY() - (faceH * 2.8f))
        val expandedRight = min(bitmapWidth.toFloat(), scaledFaceBox.centerX() + (faceW * 2.2f))
        val expandedBottom = min(bitmapHeight.toFloat(), scaledFaceBox.centerY() + (faceH * 3.5f))

        return RectF(expandedLeft, expandedTop, expandedRight, expandedBottom)
    }

    private fun drawBadgeOverlay(canvas: Canvas, cardRect: RectF, cluster: PersonCluster) {
        val pillHeight = 70f
        val pillMargin = 16f
        val pillRect = RectF(
            cardRect.left + pillMargin,
            cardRect.bottom - pillHeight - pillMargin,
            cardRect.right - pillMargin,
            cardRect.bottom - pillMargin
        )

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 15, 23, 42) // Semi-transparent dark Slate
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillBgPaint)

        // Draw label text
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(cluster.label, pillRect.left + 24f, pillRect.centerY() + 9f, labelPaint)

        // Draw appearance count badge text
        val countText = "${cluster.appearanceCount} ${if (cluster.appearanceCount == 1) "Appearance" else "Appearances"}"
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8") // Vibrant Cyan
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val countTextWidth = countPaint.measureText(countText)
        canvas.drawText(countText, pillRect.right - countTextWidth - 24f, pillRect.centerY() + 9f, countPaint)
    }

    private fun drawEmptyState(canvas: Canvas) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("No faces detected in video.", PADDING, OUTPUT_HEIGHT / 2f, textPaint)
    }
}
