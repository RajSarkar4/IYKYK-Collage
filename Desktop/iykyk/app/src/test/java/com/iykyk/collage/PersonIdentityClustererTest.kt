package com.iykyk.collage

import android.graphics.RectF
import com.iykyk.collage.domain.model.Appearance
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.processing.PersonIdentityClusterer
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonIdentityClustererTest {

    private val config = FaceMatchingConfig(
        similarityThreshold = 0.65f
    )

    private fun createDummyAppearance(
        personId: Int,
        appearanceIndex: Int,
        startMs: Long,
        endMs: Long,
        embeddingValues: FloatArray = floatArrayOf(1.0f, 0.0f),
        frameScore: Float = 0.7f
    ): Appearance {
        val fullEmbedding = FloatArray(192) { 0f }
        for (i in embeddingValues.indices) {
            fullEmbedding[i] = embeddingValues[i]
        }

        val obs = FaceObservation(
            timestampMs = startMs,
            frameIndex = 1,
            boundingBox = RectF(100f, 100f, 200f, 200f),
            embedding = fullEmbedding,
            faceWidth = 100f,
            faceHeight = 100f,
            frameWidth = 1080,
            frameHeight = 1920,
            sharpnessScore = 30f,
            representativeScore = frameScore
        )

        return Appearance(
            personId = personId,
            appearanceIndex = appearanceIndex,
            startTimestampMs = startMs,
            endTimestampMs = endMs,
            observations = listOf(obs),
            representativeObservation = obs,
            bestEmbedding = fullEmbedding,
            bestFrameScore = frameScore
        )
    }

    @Test
    fun testReappearingPersonMergedIntoSinglePersonCluster() {
        val clusterer = PersonIdentityClusterer(config, 192)

        // Person A appearance 1 (0s-5s)
        val appA1 = createDummyAppearance(1, 1, 0L, 5000L, floatArrayOf(1.0f, 0.0f), frameScore = 0.71f)

        // Person A appearance 2 (12s-17s)
        val appA2 = createDummyAppearance(1, 2, 12000L, 17000L, floatArrayOf(0.99f, 0.01f), frameScore = 0.89f)

        // Person A appearance 3 (25s-29s)
        val appA3 = createDummyAppearance(1, 3, 25000L, 29000L, floatArrayOf(0.98f, 0.02f), frameScore = 0.77f)

        clusterer.processAppearance(appA1)
        clusterer.processAppearance(appA2)
        clusterer.processAppearance(appA3)

        val finalClusters = clusterer.getFinalPersonClusters()

        // MUST produce EXACTLY 1 PersonCluster for Person A
        assertEquals(1, finalClusters.size)

        val personA = finalClusters[0]
        assertEquals(3, personA.appearanceCount)

        // Final representative frame MUST be from appearance 2 (highest score 0.89f)
        assertEquals(0.89f, personA.representativeObservation.representativeScore, 1e-4f)
        assertEquals(12000L, personA.representativeObservation.timestampMs)
    }

    @Test
    fun testDifferentPeopleFormSeparatePersonClusters() {
        val clusterer = PersonIdentityClusterer(config, 192)

        // Person A (embedding [1, 0])
        val appA = createDummyAppearance(1, 1, 0L, 5000L, floatArrayOf(1.0f, 0.0f))

        // Person B (embedding [0, 1] - orthogonal vector)
        val appB = createDummyAppearance(2, 1, 10000L, 15000L, floatArrayOf(0.0f, 1.0f))

        clusterer.processAppearance(appA)
        clusterer.processAppearance(appB)

        val finalClusters = clusterer.getFinalPersonClusters()

        // MUST produce EXACTLY 2 distinct PersonClusters
        assertEquals(2, finalClusters.size)
        assertEquals(1, finalClusters[0].appearanceCount)
        assertEquals(1, finalClusters[1].appearanceCount)
    }
}
