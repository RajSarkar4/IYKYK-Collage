package com.iykyk.collage

import android.graphics.RectF
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.ml.FaceQualityGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityGateTest {

    private val config = FaceMatchingConfig(
        minFaceSizePx = 38,
        minFaceWidthPx = 48,
        minFaceHeightPx = 48,
        minSharpness = 15f,
        whipPanBlurThreshold = 10f,
        minFaceVisibilityRatio = 0.90f,
        maxYawDegrees = 50f,
        maxPitchDegrees = 40f
    )

    private fun createDummyObservation(
        faceWidth: Float = 100f,
        faceHeight: Float = 100f,
        sharpnessScore: Float = 25f,
        leftEyeProb: Float = 1.0f,
        rightEyeProb: Float = 1.0f,
        eulerX: Float = 0f,
        eulerY: Float = 0f,
        isClipped: Boolean = false,
        frameWidth: Int = 1080,
        frameHeight: Int = 1920
    ): FaceObservation {
        val dummyEmbedding = FloatArray(192) { 0.1f }
        dummyEmbedding[0] = 1.0f

        val box = RectF(100f, 100f, 100f + faceWidth, 100f + faceHeight)

        return FaceObservation(
            timestampMs = 1000L,
            frameIndex = 5,
            boundingBox = box,
            embedding = dummyEmbedding,
            headEulerAngleX = eulerX,
            headEulerAngleY = eulerY,
            leftEyeOpenProbability = leftEyeProb,
            rightEyeOpenProbability = rightEyeProb,
            faceWidth = faceWidth,
            faceHeight = faceHeight,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            sharpnessScore = sharpnessScore,
            isClippedByBoundary = isClipped
        )
    }

    @Test
    fun testValidFacePassesRepresentativeGate() {
        val obs = createDummyObservation()
        assertTrue(FaceQualityGate.isValidForIdentity(obs, config))
        assertTrue(FaceQualityGate.isValidForRepresentative(obs, config))
    }

    @Test
    fun testBlurryFrameRejection() {
        val obs = createDummyObservation(sharpnessScore = 5f) // Below whipPanBlurThreshold
        assertTrue(FaceQualityGate.isValidForIdentity(obs, config)) // Usable for identity tracking
        assertFalse(FaceQualityGate.isValidForRepresentative(obs, config)) // Rejected for representative
    }

    @Test
    fun testTinyFaceRejection() {
        val obs = createDummyObservation(faceWidth = 20f, faceHeight = 20f)
        assertFalse(FaceQualityGate.isValidForIdentity(obs, config))
        assertFalse(FaceQualityGate.isValidForRepresentative(obs, config))
    }

    @Test
    fun testClippedFaceRejection() {
        val obs = createDummyObservation(isClipped = true)
        assertTrue(FaceQualityGate.isValidForIdentity(obs, config))
        assertFalse(FaceQualityGate.isValidForRepresentative(obs, config))
    }

    @Test
    fun testClosedEyeRejection() {
        val obs = createDummyObservation(leftEyeProb = 0.05f, rightEyeProb = 0.05f)
        assertTrue(FaceQualityGate.isValidForIdentity(obs, config))
        assertFalse(FaceQualityGate.isValidForRepresentative(obs, config))
    }

    @Test
    fun testExcessiveHeadRotationRejection() {
        val obs = createDummyObservation(eulerY = 60f) // Exceeds maxYawDegrees = 50f
        assertTrue(FaceQualityGate.isValidForIdentity(obs, config))
        assertFalse(FaceQualityGate.isValidForRepresentative(obs, config))
    }
}
