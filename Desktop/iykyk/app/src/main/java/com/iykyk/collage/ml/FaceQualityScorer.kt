package com.iykyk.collage.ml

import android.graphics.Bitmap
import android.graphics.RectF
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Evaluates whether a detected face is clearly visible and determines
 * how suitable a clearly visible face is as the representative shot
 * for a person.
 *
 * IMPORTANT:
 * This class does NOT identify people.
 * Identity is handled separately using face embeddings and clustering.
 *
 * Responsibilities:
 *
 * 1. Clear visibility
 *    - Face is sufficiently large.
 *    - Face is inside the frame.
 *    - Face is not severely blurred.
 *    - Head pose is not extreme.
 *
 * 2. Representative quality
 *    - Frontality.
 *    - Sharpness.
 *    - Eyes open.
 *    - Pleasant expression.
 *    - Face size.
 *    - No boundary clipping.
 *
 * A face that fails the clear-visibility gate should NOT be used
 * to start or continue a visible appearance and should NOT be
 * selected as a representative frame.
 */
object FaceQualityScorer {

    // -------------------------------------------------------------------------
    // CLEAR VISIBILITY
    // -------------------------------------------------------------------------

    /**
     * Determines whether a face is clearly visible enough to count as
     * a visible appearance.
     *
     * This is intentionally stricter than simply asking whether ML Kit
     * detected a face.
     *
     * A face can be detected by ML Kit but still be:
     * - too small
     * - partially outside the frame
     * - severely blurred
     * - looking too far away from the camera
     *
     * Such a face should not start a new appearance.
     */
    fun isClearlyVisible(
        obs: FaceObservation,
        config: FaceMatchingConfig = FaceMatchingConfig()
    ): Boolean {

        // Face must be sufficiently large in the source frame.
        if (obs.faceWidth < config.minFaceWidthPx) {
            return false
        }

        // Face must not touch the image boundary.
        if (obs.isClippedByBoundary) {
            return false
        }

        // Reject severely blurred faces.
        if (obs.sharpnessScore < config.minSharpness) {
            return false
        }

        // Reject extreme horizontal head rotation.
        if (abs(obs.headEulerAngleY) > config.maxYawDegrees) {
            return false
        }

        // Reject extreme vertical head rotation.
        if (abs(obs.headEulerAngleX) > config.maxPitchDegrees) {
            return false
        }

        return true
    }

    /**
     * Checks whether the detected bounding box is clipped by the frame.
     *
     * A small safety margin is used so faces that are almost touching
     * the frame boundary are also treated as clipped.
     */
    fun isClippedByBoundary(
        box: RectF,
        frameWidth: Int,
        frameHeight: Int,
        marginPx: Float = 4f
    ): Boolean {

        if (frameWidth <= 0 || frameHeight <= 0) {
            return true
        }

        return box.left <= marginPx ||
                box.top <= marginPx ||
                box.right >= frameWidth - marginPx ||
                box.bottom >= frameHeight - marginPx
    }

    // -------------------------------------------------------------------------
    // SHARPNESS
    // -------------------------------------------------------------------------

    /**
     * Estimates image sharpness using the variance of a Laplacian operator.
     *
     * Higher values generally indicate stronger edges and therefore
     * a sharper image.
     *
     * This is mainly useful for rejecting severely blurred frames and
     * ranking otherwise valid representative candidates.
     */
    fun calculateSharpness(bitmap: Bitmap): Float {

        if (bitmap.isRecycled) {
            return 0f
        }

        if (bitmap.width < 10 || bitmap.height < 10) {
            return 0f
        }

        val sampleWidth = 64
        val sampleHeight = 64

        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            sampleWidth,
            sampleHeight,
            true
        )

        val pixels = IntArray(sampleWidth * sampleHeight)

        scaled.getPixels(
            pixels,
            0,
            sampleWidth,
            0,
            0,
            sampleWidth,
            sampleHeight
        )

        if (scaled !== bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        // Convert RGB pixels to grayscale.
        val gray = FloatArray(sampleWidth * sampleHeight)

        for (i in pixels.indices) {
            val pixel = pixels[i]

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            gray[i] =
                0.299f * r +
                        0.587f * g +
                        0.114f * b
        }

        var sumLaplacian = 0.0
        var sumLaplacianSquared = 0.0
        var count = 0

        for (y in 1 until sampleHeight - 1) {
            for (x in 1 until sampleWidth - 1) {

                val center =
                    gray[y * sampleWidth + x]

                val top =
                    gray[(y - 1) * sampleWidth + x]

                val bottom =
                    gray[(y + 1) * sampleWidth + x]

                val left =
                    gray[y * sampleWidth + (x - 1)]

                val right =
                    gray[y * sampleWidth + (x + 1)]

                val laplacian =
                    top +
                            bottom +
                            left +
                            right -
                            4f * center

                sumLaplacian += laplacian
                sumLaplacianSquared +=
                    laplacian * laplacian

                count++
            }
        }

        if (count == 0) {
            return 0f
        }

        val mean =
            sumLaplacian / count

        val variance =
            (sumLaplacianSquared / count) -
                    (mean * mean)

        return max(
            0f,
            variance.toFloat()
        )
    }

    // -------------------------------------------------------------------------
    // HEAD POSE
    // -------------------------------------------------------------------------

    /**
     * Calculates how frontal the face is.
     *
     * Score:
     * 1.0 = very frontal
     * 0.0 = extreme pose
     *
     * Euler angles are supplied by ML Kit.
     */
    fun calculateFrontalityScore(
        eulerX: Float,
        eulerY: Float,
        eulerZ: Float
    ): Float {

        val pitchPenalty =
            (abs(eulerX) / 30f)
                .coerceIn(0f, 1f)

        val yawPenalty =
            (abs(eulerY) / 35f)
                .coerceIn(0f, 1f)

        val rollPenalty =
            (abs(eulerZ) / 25f)
                .coerceIn(0f, 1f)

        val totalPenalty =
            pitchPenalty * 0.4f +
                    yawPenalty * 0.4f +
                    rollPenalty * 0.2f

        return (
                1f - totalPenalty
                ).coerceIn(0f, 1f)
    }

    // -------------------------------------------------------------------------
    // REPRESENTATIVE FRAME SCORE
    // -------------------------------------------------------------------------

    /**
     * Calculates the quality of a face that has already passed
     * the clear-visibility check.
     *
     * IMPORTANT:
     * This method does not determine whether the face should count
     * as an appearance.
     *
     * Use isClearlyVisible() first.
     */
    fun calculateRepresentativeScore(
        obs: FaceObservation,
        config: FaceMatchingConfig = FaceMatchingConfig()
    ): Float {

        // Never select an unclear face as a representative.
        if (!isClearlyVisible(obs, config)) {
            return 0f
        }

        // -------------------------------------------------------------
        // 1. FRONTality
        // -------------------------------------------------------------

        val frontalityScore =
            calculateFrontalityScore(
                obs.headEulerAngleX,
                obs.headEulerAngleY,
                obs.headEulerAngleZ
            )

        // -------------------------------------------------------------
        // 2. EYES OPEN
        // -------------------------------------------------------------

        val eyeScore = (
                obs.leftEyeOpenProbability +
                        obs.rightEyeOpenProbability
                ) / 2f

        val normalizedEyeScore =
            eyeScore.coerceIn(0f, 1f)

        // -------------------------------------------------------------
        // 3. SHARPNESS
        // -------------------------------------------------------------

        /*
         * The absolute sharpness value depends on the source video.
         *
         * 80f is therefore treated only as a normalization reference,
         * while config.minSharpness is used as the hard visibility gate.
         */
        val normalizedSharpness =
            (obs.sharpnessScore / 80f)
                .coerceIn(0f, 1f)

        // -------------------------------------------------------------
        // 4. EXPRESSION
        // -------------------------------------------------------------

        /*
         * ML Kit may return -1 when smiling probability is unavailable.
         *
         * In that situation we do not penalize the person.
         */
        val expressionScore =
            if (obs.smilingProbability >= 0f) {
                obs.smilingProbability
                    .coerceIn(0f, 1f)
            } else {
                0.5f
            }

        // -------------------------------------------------------------
        // 5. FACE SIZE
        // -------------------------------------------------------------

        val frameWidth =
            max(
                1,
                obs.frameWidth
            )

        val faceWidthRatio =
            obs.faceWidth /
                    frameWidth.toFloat()

        /*
         * A face occupying roughly 35% of the frame width
         * receives the maximum size score.
         *
         * Smaller faces still receive a partial score.
         */
        val sizeScore =
            (
                    faceWidthRatio / 0.35f
                    ).coerceIn(
                    0.1f,
                    1f
                )

        // -------------------------------------------------------------
        // 6. CLIPPING
        // -------------------------------------------------------------

        val nonClippingScore =
            if (obs.isClippedByBoundary) {
                0f
            } else {
                1f
            }

        // -------------------------------------------------------------
        // FINAL SCORE
        // -------------------------------------------------------------

        val compositeScore =
            config.frontalityWeight *
                    frontalityScore +

                    config.sharpnessWeight *
                    normalizedSharpness +

                    config.eyesWeight *
                    normalizedEyeScore +

                    config.expressionWeight *
                    expressionScore +

                    config.visibilityWeight *
                    sizeScore +

                    config.nonClippingWeight *
                    nonClippingScore

        return compositeScore.coerceIn(
            0f,
            1f
        )
    }

    // -------------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------------

    /**
     * Convenience method that checks visibility and calculates the
     * representative score in one call.
     *
     * Returns:
     *
     * null -> face is not clearly visible
     *
     * score -> face is clearly visible and score is valid
     */
    fun getRepresentativeScoreIfClearlyVisible(
        obs: FaceObservation,
        config: FaceMatchingConfig = FaceMatchingConfig()
    ): Float? {

        if (!isClearlyVisible(obs, config)) {
            return null
        }

        return calculateRepresentativeScore(
            obs,
            config
        )
    }
}

