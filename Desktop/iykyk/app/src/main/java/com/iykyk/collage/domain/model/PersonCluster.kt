package com.iykyk.collage.domain.model

import android.graphics.Bitmap

/**
 * Represents a unique person cluster identified across the video.
 * Holds only ONE representative frame Bitmap for rendering to avoid high memory usage.
 */
data class PersonCluster(
    val id: Int,
    val label: String,
    val observations: List<FaceObservation>,
    val centroidEmbedding: FloatArray,
    val appearances: List<Appearance>,
    val representativeObservation: FaceObservation,
    val representativeBitmap: Bitmap? = null
) {
    val appearanceCount: Int
        get() = appearances.size

    val totalObservationCount: Int
        get() = observations.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonCluster

        if (id != other.id) return false
        if (label != other.label) return false
        if (observations != other.observations) return false
        if (!centroidEmbedding.contentEquals(other.centroidEmbedding)) return false
        if (appearances != other.appearances) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + label.hashCode()
        result = 31 * result + observations.hashCode()
        result = 31 * result + centroidEmbedding.contentHashCode()
        result = 31 * result + appearances.hashCode()
        return result
    }
}
