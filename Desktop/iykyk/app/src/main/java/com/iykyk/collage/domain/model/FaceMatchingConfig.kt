package com.iykyk.collage.domain.model

/**
 * Central configuration for the complete face-processing pipeline.
 *
 * Contains thresholds for:
 * - Face detection
 * - Face embedding matching
 * - Identity clustering
 * - Clear-face visibility
 * - Appearance tracking
 * - Representative frame selection
 */
data class FaceMatchingConfig(

    // ========================================================================
    // IDENTITY MATCHING
    // ========================================================================

    /**
     * Cosine similarity threshold used when comparing normalized
     * face embeddings.
     *
     * This is a tunable starting value, not a guaranteed value for
     * every embedding model.
     */
    val similarityThreshold: Float = 0.55f,


    // ========================================================================
    // VIDEO PROCESSING
    // ========================================================================

    /**
     * Number of frames sampled from the video per second.
     *
     * 5 FPS is generally sufficient for appearance tracking while
     * avoiding unnecessary processing.
     */
    val frameSamplingRate: Float = 5f,
    val targetFps: Int = 5,
    val minFaceSizePx: Int = 38,


    // ========================================================================
    // APPEARANCE TRACKING
    // ========================================================================

    /**
     * Maximum amount of time, in milliseconds, that a person's face
     * can temporarily disappear before the current appearance ends.
     *
     * A short detection gap can occur because of:
     * - temporary detection failure
     * - motion
     * - partial occlusion
     *
     * Longer gaps indicate a new appearance.
     */
    val appearanceGapThresholdMs: Long = 1200L,


    // ========================================================================
    // CLEAR FACE VISIBILITY
    // ========================================================================

    /**
     * Minimum face width in pixels required for a face to be considered
     * clearly visible.
     *
     * This prevents tiny background faces from being counted as
     * appearances.
     */
    val minFaceWidthPx: Int = 48,

    /**
     * Minimum face height in pixels required for a face to be considered
     * clearly visible.
     */
    val minFaceHeightPx: Int = 48,

    /**
     * Minimum ML Kit relative face size.
     *
     * ML Kit interprets this as the approximate minimum face size
     * relative to the image dimensions.
     */
    val minMlKitFaceSizeRatio: Float = 0.08f,

    /**
     * Minimum amount of the face that must remain inside the frame.
     *
     * 1.0 = completely inside.
     *
     * This is useful if partial clipping checks are added later.
     */
    val minFaceVisibilityRatio: Float = 0.90f,

    /**
     * Maximum allowed horizontal head rotation for a face to be
     * considered clearly visible.
     */
    val maxYawDegrees: Float = 50f,

    /**
     * Maximum allowed vertical head rotation for a face to be
     * considered clearly visible.
     */
    val maxPitchDegrees: Float = 40f,


    // ========================================================================
    // BLUR / SHARPNESS
    // ========================================================================

    /**
     * Minimum Laplacian-variance sharpness score required for a face
     * to be considered clearly visible.
     *
     * This is a starting value and should be tuned using the supplied
     * sample videos.
     */
    val minSharpness: Float = 5f,

    /**
     * Sharpness threshold below which a frame is considered strongly
     * blurred / likely to be part of a whip-pan transition.
     *
     * This can be lower than minSharpness because it serves a separate
     * purpose from representative-frame selection.
     */
    val whipPanBlurThreshold: Float = 2f,


    // ========================================================================
    // REPRESENTATIVE FRAME WEIGHTS
    // ========================================================================

    /**
     * Weight given to frontal head pose.
     */
    val frontalityWeight: Float = 0.25f,

    /**
     * Weight given to image sharpness.
     */
    val sharpnessWeight: Float = 0.25f,

    /**
     * Weight given to eyes being open.
     */
    val eyesWeight: Float = 0.20f,

    /**
     * Weight given to pleasant expression / smiling probability.
     */
    val expressionWeight: Float = 0.15f,

    /**
     * Weight given to face size / visibility.
     */
    val visibilityWeight: Float = 0.10f,

    /**
     * Weight given to the face not being clipped by the frame boundary.
     */
    val nonClippingWeight: Float = 0.05f
)
