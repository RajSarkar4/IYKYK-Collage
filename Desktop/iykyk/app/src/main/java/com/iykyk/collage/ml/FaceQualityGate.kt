package com.iykyk.collage.ml

import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import kotlin.math.abs

/**
 * Evaluates face observation validity across two strict tiers:
 * 1. Identity Usability: Basic integrity to maintain tracking & identity clustering without corrupting embeddings.
 * 2. Representative Quality: Strict quality gate to prevent blurry, tiny, clipped, rotated, or closed-eye frames from becoming the final person image.
 */
object FaceQualityGate {

    /**
     * Basic usability check for identity processing and temporal tracking.
     */
    fun isValidForIdentity(
        obs: FaceObservation,
        config: FaceMatchingConfig = FaceMatchingConfig(),
        expectedEmbeddingDimension: Int = 192
    ): Boolean {
        // Face size minimum check
        if (obs.faceWidth < config.minFaceSizePx || obs.faceHeight < config.minFaceSizePx) {
            return false
        }

        // Sharpness / Blur check - ignore completely smeared/corrupt frames
        if (obs.sharpnessScore < 1.5f) {
            return false
        }

        // Embedding validity check
        if (!EmbeddingSimilarity.isValidEmbedding(obs.embedding, expectedEmbeddingDimension)) {
            return false
        }

        return true
    }

    /**
     * Strict quality gate for representative frame selection.
     */
    fun isValidForRepresentative(
        obs: FaceObservation,
        config: FaceMatchingConfig = FaceMatchingConfig(),
        expectedEmbeddingDimension: Int = 192
    ): Boolean {
        if (!isValidForIdentity(obs, config, expectedEmbeddingDimension)) {
            return false
        }

        // 1. Sharpness / Motion Blur Gate
        if (obs.sharpnessScore < config.minSharpness || obs.sharpnessScore < config.whipPanBlurThreshold) {
            return false
        }

        // 2. Clear Face Dimensions Gate
        if (obs.faceWidth < config.minFaceWidthPx || obs.faceHeight < config.minFaceHeightPx) {
            return false
        }

        // 3. Boundary Non-Clipping Gate
        if (obs.isClippedByBoundary) {
            return false
        }

        // 4. Visibility Ratio Gate
        if (obs.visibilityRatio < config.minFaceVisibilityRatio) {
            return false
        }

        // 5. Head Pose Angle Gate
        if (abs(obs.headEulerAngleY) > config.maxYawDegrees || abs(obs.headEulerAngleX) > config.maxPitchDegrees) {
            return false
        }

        // 6. Eye Openness Gate (Reject only if both eyes are confidently closed)
        val avgEyeOpenness = (obs.leftEyeOpenProbability + obs.rightEyeOpenProbability) / 2.0f
        if (avgEyeOpenness < 0.20f) {
            return false
        }

        return true
    }
}
