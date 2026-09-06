package com.iykyk.collage

import com.iykyk.collage.ml.EmbeddingSimilarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class EmbeddingSimilarityTest {

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val vecA = floatArrayOf(0.6f, 0.8f, 0.0f)
        val vecB = floatArrayOf(0.6f, 0.8f, 0.0f)
        val similarity = EmbeddingSimilarity.cosineSimilarity(vecA, vecB)
        assertEquals(1.0f, similarity, 1e-4f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val vecA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecB = floatArrayOf(0.0f, 1.0f, 0.0f)
        val similarity = EmbeddingSimilarity.cosineSimilarity(vecA, vecB)
        assertEquals(0.0f, similarity, 1e-4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCosineSimilarityDimensionMismatch() {
        val vecA = floatArrayOf(1.0f, 0.0f)
        val vecB = floatArrayOf(1.0f, 0.0f, 0.0f)
        EmbeddingSimilarity.cosineSimilarity(vecA, vecB)
    }

    @Test
    fun testL2Normalization() {
        val vec = floatArrayOf(3.0f, 4.0f)
        val normalized = EmbeddingSimilarity.l2Normalize(vec)
        val norm = sqrt((normalized[0] * normalized[0] + normalized[1] * normalized[1]).toDouble()).toFloat()
        assertEquals(1.0f, norm, 1e-4f)
        assertEquals(0.6f, normalized[0], 1e-4f)
        assertEquals(0.8f, normalized[1], 1e-4f)
    }

    @Test
    fun testComputeCentroidNormalized() {
        val vecA = EmbeddingSimilarity.l2Normalize(floatArrayOf(1.0f, 0.0f))
        val vecB = EmbeddingSimilarity.l2Normalize(floatArrayOf(0.0f, 1.0f))

        val centroid = EmbeddingSimilarity.computeCentroid(listOf(vecA, vecB), 2)

        val norm = sqrt((centroid[0] * centroid[0] + centroid[1] * centroid[1]).toDouble()).toFloat()
        assertEquals(1.0f, norm, 1e-4f)
        assertEquals(1.0f / sqrt(2.0f), centroid[0], 1e-4f)
        assertEquals(1.0f / sqrt(2.0f), centroid[1], 1e-4f)
    }

    @Test
    fun testIsValidEmbedding() {
        val validVec = FloatArray(192) { 0.1f }
        assertTrue(EmbeddingSimilarity.isValidEmbedding(validVec, 192))

        val wrongDimVec = FloatArray(128) { 0.1f }
        assertFalse(EmbeddingSimilarity.isValidEmbedding(wrongDimVec, 192))

        val nanVec = FloatArray(192) { 0.1f }.apply { this[0] = Float.NaN }
        assertFalse(EmbeddingSimilarity.isValidEmbedding(nanVec, 192))

        val zeroVec = FloatArray(192) { 0.0f }
        assertFalse(EmbeddingSimilarity.isValidEmbedding(zeroVec, 192))
    }
}
