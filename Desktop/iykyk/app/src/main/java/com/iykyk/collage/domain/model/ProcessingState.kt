package com.iykyk.collage.domain.model

import android.graphics.Bitmap
import android.net.Uri

enum class ProcessingStage(val displayName: String) {
    INITIALIZING("Initializing engine..."),
    EXTRACTING_FRAMES("Extracting video frames..."),
    DETECTING_FACES("Detecting faces..."),
    COMPUTING_EMBEDDINGS("Generating face embeddings..."),
    CLUSTERING_IDENTITIES("Clustering identities..."),
    TRACKING_APPEARANCES("Tracking continuous appearances..."),
    SELECTING_REPRESENTATIVE_SHOTS("Selecting best representative shots..."),
    GENERATING_COLLAGE("Generating final collage..."),
    COMPLETED("Processing complete!")
}

data class ProcessingResult(
    val videoUri: Uri,
    val videoDurationMs: Long,
    val totalSampledFrames: Int,
    val totalDetectedFaces: Int,
    val clusters: List<PersonCluster>,
    val collageBitmap: Bitmap? = null,
    val collageUri: Uri? = null
)

sealed interface ProcessingState {
    data object Idle : ProcessingState

    data class Processing(
        val progress: Float, // 0.0f to 1.0f
        val stage: ProcessingStage,
        val stageMessage: String,
        val sampledFramesCount: Int = 0,
        val detectedFacesCount: Int = 0
    ) : ProcessingState

    data class Success(
        val result: ProcessingResult
    ) : ProcessingState

    data class Error(
        val message: String
    ) : ProcessingState
}
