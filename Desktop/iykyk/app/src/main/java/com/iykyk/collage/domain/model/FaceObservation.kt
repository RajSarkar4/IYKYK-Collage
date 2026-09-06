package com.iykyk.collage.domain.model

import android.graphics.RectF

/**
 * Lightweight, memory-efficient immutable domain model representing a detected face.
 * Does NOT hold large Bitmap instances to prevent Out-Of-Memory issues during long video processing.
 */
data class FaceObservation(
    val timestampMs: Long,
    val frameIndex: Int,
    val boundingBox: RectF,
    val embedding: FloatArray,
    val headEulerAngleX: Float = 0f,
    val headEulerAngleY: Float = 0f,
    val headEulerAngleZ: Float = 0f,
    val leftEyeOpenProbability: Float = 1.0f,
    val rightEyeOpenProbability: Float = 1.0f,
    val smilingProbability: Float = 0.5f,
    val faceWidth: Float,
    val faceHeight: Float,
    val frameWidth: Int,
    val frameHeight: Int,
    val sharpnessScore: Float = 0f,
    val faceVisibilityScore: Float = 1.0f,
    val isClippedByBoundary: Boolean = false,
    val representativeScore: Float = 0f
) {
    val timestampSeconds: Float
        get() = timestampMs / 1000.0f

    val visibilityRatio: Float
        get() {
            if (frameWidth <= 0 || frameHeight <= 0) return 1.0f
            val visibleLeft = boundingBox.left.coerceIn(0f, frameWidth.toFloat())
            val visibleTop = boundingBox.top.coerceIn(0f, frameHeight.toFloat())
            val visibleRight = boundingBox.right.coerceIn(0f, frameWidth.toFloat())
            val visibleBottom = boundingBox.bottom.coerceIn(0f, frameHeight.toFloat())
            val visibleArea = (visibleRight - visibleLeft).coerceAtLeast(0f) * (visibleBottom - visibleTop).coerceAtLeast(0f)
            val totalArea = (boundingBox.width() * boundingBox.height()).coerceAtLeast(1f)
            return (visibleArea / totalArea).coerceIn(0f, 1f)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceObservation

        if (timestampMs != other.timestampMs) return false
        if (frameIndex != other.frameIndex) return false
        if (boundingBox != other.boundingBox) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + frameIndex
        result = 31 * result + boundingBox.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
