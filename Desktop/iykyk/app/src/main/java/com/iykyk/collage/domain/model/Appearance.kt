package com.iykyk.collage.domain.model

import android.graphics.Bitmap

/**
 * Represents a single continuous appearance window of a person in the video.
 */
data class Appearance(
    val personId: Int,
    val appearanceIndex: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val observations: List<FaceObservation> = emptyList(),
    val representativeObservation: FaceObservation,
    val bestEmbedding: FloatArray = representativeObservation.embedding,
    val bestFrameScore: Float = representativeObservation.representativeScore,
    val bestFrameBitmap: Bitmap? = null
) {
    val durationSeconds: Float
        get() = (endTimestampMs - startTimestampMs) / 1000.0f
}

