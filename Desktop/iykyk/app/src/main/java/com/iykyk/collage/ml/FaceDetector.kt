package com.iykyk.collage.ml

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.collage.domain.model.FaceMatchingConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit Face Detection API for accurate multi-face detection, bounding boxes,
 * head Euler rotation angles, eye open probabilities, and smiling probability.
 */
class FaceDetector(
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) : AutoCloseable {

    private val detectorOptions =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_ALL
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_ALL
            )
            .setMinFaceSize(
                config.minMlKitFaceSizeRatio
            )
            .enableTracking()
            .build()

    private val mlKitDetector =
        FaceDetection.getClient(detectorOptions)

    suspend fun detectFaces(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): List<Face> {

        require(!bitmap.isRecycled) {
            "Cannot process a recycled bitmap"
        }

        val inputImage = InputImage.fromBitmap(
            bitmap,
            rotationDegrees
        )

        return mlKitDetector
            .process(inputImage)
            .awaitTask()
    }

    override fun close() {
        mlKitDetector.close()
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->

            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }

            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel()
                }
            }
        }
}