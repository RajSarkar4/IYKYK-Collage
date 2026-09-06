package com.iykyk.collage.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.iykyk.collage.collage.CollageGenerator
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.domain.model.PersonCluster
import com.iykyk.collage.domain.model.ProcessingResult
import com.iykyk.collage.domain.model.ProcessingStage
import com.iykyk.collage.domain.model.ProcessingState
import com.iykyk.collage.ml.FaceDetector
import com.iykyk.collage.ml.FaceEmbedder
import com.iykyk.collage.ml.FaceQualityGate
import com.iykyk.collage.ml.FaceQualityScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/**
 * High-performance orchestrator for video analysis, multi-person face tracking,
 * identity clustering, representative shot selection, and collage generation.
 *
 * Memory Safety:
 * Processes video frames on-the-fly and recycles Bitmaps immediately off the main thread.
 */
class VideoProcessor(
    private val context: Context,
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) {

    companion object {
        private const val TAG = "VideoProcessor"
    }

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val frameExtractor = VideoFrameExtractor(context, config)
    private val faceDetector = FaceDetector(config)
    private val faceEmbedder = FaceEmbedder(context, config)
    private val collageGenerator = CollageGenerator()

    /**
     * Executes end-to-end processing of selected portrait video off the main thread with cancellation support.
     */
    suspend fun processVideo(videoUri: Uri): ProcessingResult = withContext(Dispatchers.Default) {
        try {
            _processingState.value = ProcessingState.Processing(
                progress = 0.05f,
                stage = ProcessingStage.INITIALIZING,
                stageMessage = "Initializing processing pipeline..."
            )

            val durationMs = frameExtractor.getVideoDurationMs(videoUri)
            if (durationMs <= 0) {
                val errorMsg = "Unable to read video metadata or invalid duration."
                _processingState.value = ProcessingState.Error(errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            Log.d(TAG, "Processing video URI: $videoUri (Duration: $durationMs ms, Embedding Dimension: ${faceEmbedder.embeddingDimension})")

            val identityClusterer = PersonIdentityClusterer(config, faceEmbedder.embeddingDimension)
            val multiPersonTracker = MultiPersonAppearanceTracker(config) { completedAppearance ->
                identityClusterer.processAppearance(completedAppearance)
            }

            var processedFramesCount = 0
            var detectedFacesCount = 0

            // 1. Frame Extraction, Multi-Person Tracking & Appearance Building Stage
            frameExtractor.extractFrames(
                videoUri = videoUri,
                onProgress = { currentIdx, totalEst ->
                    val frameRatio = currentIdx.toFloat() / max(1, totalEst)
                    val progressVal = (0.05f + frameRatio * 0.80f).coerceIn(0.05f, 0.85f)
                    _processingState.value = ProcessingState.Processing(
                        progress = progressVal,
                        stage = ProcessingStage.DETECTING_FACES,
                        stageMessage = "Processing frame $currentIdx / $totalEst...",
                        sampledFramesCount = currentIdx,
                        detectedFacesCount = detectedFacesCount
                    )
                },
                onFrameExtracted = { extractedFrame ->
                    coroutineContext.ensureActive()

                    processedFramesCount++
                    val frameBitmap = extractedFrame.bitmap
                    val faces: List<Face> = faceDetector.detectFaces(frameBitmap)

                    val frameObservations = mutableListOf<FaceObservation>()

                    for (face in faces) {
                        val boundingBox = RectF(
                            face.boundingBox.left.toFloat(),
                            face.boundingBox.top.toFloat(),
                            face.boundingBox.right.toFloat(),
                            face.boundingBox.bottom.toFloat()
                        )

                        if (boundingBox.width() < config.minFaceSizePx || boundingBox.height() < config.minFaceSizePx) {
                            continue
                        }

                        val isClipped = FaceQualityScorer.isClippedByBoundary(boundingBox, frameBitmap.width, frameBitmap.height)
                        val faceCrop = faceEmbedder.cropFaceWithMargin(frameBitmap, boundingBox, 0.20f)
                        val sharpness = FaceQualityScorer.calculateSharpness(faceCrop)
                        val embedding = faceEmbedder.extractEmbedding(frameBitmap, boundingBox)

                        if (!faceCrop.isRecycled) faceCrop.recycle()

                        val eulerX = face.headEulerAngleX
                        val eulerY = face.headEulerAngleY
                        val eulerZ = face.headEulerAngleZ
                        val leftEyeProb = face.leftEyeOpenProbability ?: 1.0f
                        val rightEyeProb = face.rightEyeOpenProbability ?: 1.0f
                        val smileProb = face.smilingProbability ?: 0.5f

                        val tempObs = FaceObservation(
                            timestampMs = extractedFrame.timestampMs,
                            frameIndex = extractedFrame.frameIndex,
                            boundingBox = boundingBox,
                            embedding = embedding,
                            headEulerAngleX = eulerX,
                            headEulerAngleY = eulerY,
                            headEulerAngleZ = eulerZ,
                            leftEyeOpenProbability = leftEyeProb,
                            rightEyeOpenProbability = rightEyeProb,
                            smilingProbability = smileProb,
                            faceWidth = boundingBox.width(),
                            faceHeight = boundingBox.height(),
                            frameWidth = frameBitmap.width,
                            frameHeight = frameBitmap.height,
                            sharpnessScore = sharpness,
                            faceVisibilityScore = 1.0f,
                            isClippedByBoundary = isClipped
                        )

                        val repScore = FaceQualityScorer.calculateRepresentativeScore(tempObs, config)
                        val obs = tempObs.copy(representativeScore = repScore)

                        // Basic Identity Usability Check
                        if (FaceQualityGate.isValidForIdentity(obs, config, faceEmbedder.embeddingDimension)) {
                            frameObservations.add(obs)
                            detectedFacesCount++
                        }
                    }

                    // Process incoming frame observations in MultiPersonAppearanceTracker
                    multiPersonTracker.processFrame(
                        timestampMs = extractedFrame.timestampMs,
                        observations = frameObservations,
                        frameBitmap = frameBitmap,
                        scoreCalculator = { it.representativeScore }
                    )

                    // Promptly recycle frame bitmap to avoid memory leaks
                    if (!frameBitmap.isRecycled) frameBitmap.recycle()
                }
            )

            coroutineContext.ensureActive()

            // 2. Finalize all active tracks at end of video stream
            _processingState.value = ProcessingState.Processing(
                progress = 0.88f,
                stage = ProcessingStage.CLUSTERING_IDENTITIES,
                stageMessage = "Clustering identities & selecting best shots...",
                sampledFramesCount = processedFramesCount,
                detectedFacesCount = detectedFacesCount
            )

            multiPersonTracker.finishAll()

            val rawClusters = identityClusterer.getFinalPersonClusters()
            val validClusters = rawClusters.map { cluster ->
                val peakObs = selectPeakRepresentativeObservation(cluster)
                cluster.copy(representativeObservation = peakObs)
            }.filter { cluster ->
                cluster.observations.isNotEmpty()
            }

            val finalClusters = extractRepresentativeBitmaps(videoUri, validClusters.ifEmpty { rawClusters })

            Log.d(TAG, "Completed Processing. Total Frames: $processedFramesCount, Detected Faces: $detectedFacesCount, Unique People Identified: ${finalClusters.size}")

            if (finalClusters.isEmpty()) {
                val errorMsg = "No faces detected in the selected video."
                _processingState.value = ProcessingState.Error(errorMsg)
                throw IllegalStateException(errorMsg)
            }

            // 3. Final Collage Generation Stage
            _processingState.value = ProcessingState.Processing(
                progress = 0.95f,
                stage = ProcessingStage.GENERATING_COLLAGE,
                stageMessage = "Rendering portrait grid collage...",
                sampledFramesCount = processedFramesCount,
                detectedFacesCount = detectedFacesCount
            )

            val collageBitmap = collageGenerator.generateCollage(finalClusters)

            val result = ProcessingResult(
                videoUri = videoUri,
                videoDurationMs = durationMs,
                totalSampledFrames = processedFramesCount,
                totalDetectedFaces = detectedFacesCount,
                clusters = finalClusters,
                collageBitmap = collageBitmap
            )

            _processingState.value = ProcessingState.Success(result)
            return@withContext result
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "Video processing cancelled by user.")
                _processingState.value = ProcessingState.Idle
                throw e
            }
            Log.e(TAG, "Video processing failed: ${e.message}", e)
            if (_processingState.value !is ProcessingState.Error) {
                _processingState.value = ProcessingState.Error(e.localizedMessage ?: "Video processing failed.")
            }
            throw e
        }
    }

    private fun selectPeakRepresentativeObservation(cluster: PersonCluster): FaceObservation {
        if (cluster.observations.isEmpty()) return cluster.representativeObservation

        // 1. Prefer observations passing full representative quality gate
        val passingObs = cluster.observations.filter { obs ->
            FaceQualityGate.isValidForRepresentative(obs, config, faceEmbedder.embeddingDimension)
        }

        if (passingObs.isNotEmpty()) {
            return passingObs.maxByOrNull { it.representativeScore }!!
        }

        // 2. Otherwise pick the sharpest observation available for this person
        return cluster.observations.maxByOrNull { it.sharpnessScore }
            ?: cluster.observations.maxByOrNull { it.representativeScore }
            ?: cluster.representativeObservation
    }

    /**
     * Extracts full-resolution representative frame Bitmaps on-demand for each person cluster.
     */
    private fun extractRepresentativeBitmaps(
        videoUri: Uri,
        clusters: List<PersonCluster>
    ): List<PersonCluster> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            clusters.map { cluster ->
                val repObs = cluster.representativeObservation
                val timeUs = repObs.timestampMs * 1000L
                val frameBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (frameBitmap != null) {
                    val oldBitmap = cluster.representativeBitmap
                    if (oldBitmap != null && oldBitmap != frameBitmap && !oldBitmap.isRecycled) {
                        oldBitmap.recycle()
                    }
                    cluster.copy(representativeBitmap = frameBitmap)
                } else {
                    cluster
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting representative frame bitmaps: ${e.message}", e)
            clusters
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun close() {
        try {
            faceDetector.close()
            faceEmbedder.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VideoProcessor resources: ${e.message}", e)
        }
    }
}
