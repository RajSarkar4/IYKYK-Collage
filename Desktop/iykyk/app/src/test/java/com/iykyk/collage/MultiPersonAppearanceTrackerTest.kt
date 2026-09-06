package com.iykyk.collage

import android.graphics.RectF
import com.iykyk.collage.domain.model.Appearance
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.processing.MultiPersonAppearanceTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiPersonAppearanceTrackerTest {

    private val config = FaceMatchingConfig(
        appearanceGapThresholdMs = 1200L,
        similarityThreshold = 0.65f
    )

    private fun createDummyObservation(
        timestampMs: Long,
        embeddingValues: FloatArray = floatArrayOf(1.0f, 0.0f),
        xOffset: Float = 0f
    ): FaceObservation {
        val fullEmbedding = FloatArray(192) { 0f }
        for (i in embeddingValues.indices) {
            fullEmbedding[i] = embeddingValues[i]
        }

        return FaceObservation(
            timestampMs = timestampMs,
            frameIndex = (timestampMs / 200).toInt(),
            boundingBox = RectF(xOffset, 100f, xOffset + 100f, 200f),
            embedding = fullEmbedding,
            faceWidth = 100f,
            faceHeight = 100f,
            frameWidth = 1080,
            frameHeight = 1920,
            sharpnessScore = 30f,
            representativeScore = 0.8f
        )
    }

    @Test
    fun testContinuousAppearanceSingleTrack() {
        val completedAppearances = mutableListOf<Appearance>()
        val tracker = MultiPersonAppearanceTracker(config) { appearance ->
            completedAppearances.add(appearance)
        }

        // Frame at 0s, 0.2s, 0.4s, 0.6s, 0.8s, 1.0s (all within 1200ms of previous frame)
        for (ts in listOf(0L, 200L, 400L, 600L, 800L, 1000L)) {
            val obs = createDummyObservation(ts)
            tracker.processFrame(ts, listOf(obs), scoreCalculator = { 0.8f })
        }

        // Finish stream
        tracker.finishAll()

        // Should produce EXACTLY 1 appearance segment
        assertEquals(1, completedAppearances.size)
        assertEquals(0L, completedAppearances[0].startTimestampMs)
        assertEquals(1000L, completedAppearances[0].endTimestampMs)
        assertEquals(6, completedAppearances[0].observations.size)
    }

    @Test
    fun testAppearanceTimeoutCreatesNewAppearance() {
        val completedAppearances = mutableListOf<Appearance>()
        val tracker = MultiPersonAppearanceTracker(config) { appearance ->
            completedAppearances.add(appearance)
        }

        // Person visible 0s..1s
        for (ts in listOf(0L, 200L, 400L, 600L, 800L, 1000L)) {
            val obs = createDummyObservation(ts)
            tracker.processFrame(ts, listOf(obs), scoreCalculator = { 0.8f })
        }

        // Person disappears, gap of 3000ms (> 1200ms gap threshold)
        // Person returns at 4000ms..4400ms
        for (ts in listOf(4000L, 4200L, 4400L)) {
            val obs = createDummyObservation(ts)
            tracker.processFrame(ts, listOf(obs), scoreCalculator = { 0.8f })
        }

        tracker.finishAll()

        // Should produce EXACTLY 2 distinct appearances for the track
        assertEquals(2, completedAppearances.size)
        assertEquals(0L, completedAppearances[0].startTimestampMs)
        assertEquals(1000L, completedAppearances[0].endTimestampMs)

        assertEquals(4000L, completedAppearances[1].startTimestampMs)
        assertEquals(4400L, completedAppearances[1].endTimestampMs)
    }

    @Test
    fun testSimultaneousMultiPersonTracking() {
        val completedAppearances = mutableListOf<Appearance>()
        val tracker = MultiPersonAppearanceTracker(config) { appearance ->
            completedAppearances.add(appearance)
        }

        // Vector A: [1, 0], Vector B: [0, 1]
        val vecPersonA = floatArrayOf(1.0f, 0.0f)
        val vecPersonB = floatArrayOf(0.0f, 1.0f)

        for (ts in listOf(0L, 200L, 400L)) {
            val obsA = createDummyObservation(ts, vecPersonA, xOffset = 100f)
            val obsB = createDummyObservation(ts, vecPersonB, xOffset = 500f)

            // Both Person A and Person B present in the same frame
            tracker.processFrame(ts, listOf(obsA, obsB), scoreCalculator = { 0.8f })
        }

        tracker.finishAll()

        // Should produce 2 active tracks / appearances (one for Person A, one for Person B)
        assertEquals(2, completedAppearances.size)
    }
}
