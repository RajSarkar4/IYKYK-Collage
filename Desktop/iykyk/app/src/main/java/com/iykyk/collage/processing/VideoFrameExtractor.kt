package com.iykyk.collage.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.iykyk.collage.domain.model.FaceMatchingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val bitmap: Bitmap
)

/**
 * Incremental video frame extractor utilizing [MediaMetadataRetriever].
 * Samples video frames at configurable target frame rate (e.g. 5 FPS) without loading entire video into memory.
 */
class VideoFrameExtractor(
    private val context: Context,
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) {

    companion object {
        private const val TAG = "VideoFrameExtractor"
    }

    /**
     * Obtains total video duration in milliseconds.
     */
    suspend fun getVideoDurationMs(videoUri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration: ${e.message}", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts frame Bitmaps incrementally at target sampling interval.
     * Yields extracted frames via callback lambda to ensure memory is released promptly.
     */
    suspend fun extractFrames(
        videoUri: Uri,
        onProgress: (currentFrameIndex: Int, totalEstimatedFrames: Int) -> Unit,
        onFrameExtracted: suspend (ExtractedFrame) -> Unit
    ) = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                Log.w(TAG, "Invalid video duration ($durationMs ms)")
                return@withContext
            }

            val frameStepMs = 1000L / config.targetFps
            val totalEstimatedFrames = maxOf(1, ((durationMs / frameStepMs) + 1).toInt())
            var currentTimestampMs = 0L
            var frameIndex = 0

            Log.d(TAG, "Starting frame extraction. Duration: $durationMs ms, Target FPS: ${config.targetFps}, Est Frames: $totalEstimatedFrames")

            while (currentTimestampMs <= durationMs) {
                val timeUs = currentTimestampMs * 1000L
                val frameBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (frameBitmap != null) {
                    val currentFrameNum = frameIndex + 1
                    val frame = ExtractedFrame(currentFrameNum, currentTimestampMs, frameBitmap)
                    onProgress(currentFrameNum, totalEstimatedFrames)
                    onFrameExtracted(frame)
                }

                frameIndex++
                currentTimestampMs += frameStepMs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting frames from video: ${e.message}", e)
            throw e
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
