package com.iykyk.collage.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.iykyk.collage.domain.model.FaceMatchingConfig
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device face embedding extractor using a TensorFlow Lite model.
 *
 * IMPORTANT:
 * This class does not assume the embedding dimension.
 * The input/output tensor specifications are inspected directly
 * from the packaged TFLite model.
 *
 * Expected model characteristics:
 * - Single image input
 * - NHWC image layout: [1, height, width, 3]
 * - Float32 input
 * - Float32 embedding output
 *
 * The model's actual input dimensions and embedding dimension are
 * obtained dynamically from the TFLite interpreter.
 *
 * Pipeline:
 *
 * Face bounding box
 *        ↓
 * Generous contextual crop
 *        ↓
 * Resize to model input dimensions
 *        ↓
 * Normalize pixels according to model input type
 *        ↓
 * TFLite inference
 *        ↓
 * L2-normalize embedding
 *        ↓
 * Return embedding
 *
 * Identity matching is NOT performed here.
 * Embeddings are later compared and clustered by the identity pipeline.
 */
class FaceEmbedder(
    private val context: Context,
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) : AutoCloseable {

    companion object {
        private const val TAG = "FaceEmbedder"

        /**
         * Keep the model name in one place.
         *
         * Make sure this exact file exists under:
         *
         * app/src/main/assets/mobilefacenet.tflite
         */
        private const val MODEL_FILE_NAME = "mobilefacenet.tflite"

        /**
         * Default contextual margin around the detected face.
         *
         * The margin prevents the embedding from being based on an
         * excessively tight crop.
         */
        private const val DEFAULT_MARGIN_PERCENT = 0.20f
    }

    private var interpreter: Interpreter? = null

    val inputWidth: Int
    val inputHeight: Int
    val embeddingDimension: Int
    val inputDataType: DataType
    val outputDataType: DataType

    init {

        val modelBuffer =
            loadModelFile(
                context,
                MODEL_FILE_NAME
            )

        val options =
            Interpreter.Options().apply {
                setNumThreads(4)
            }

        try {

            val tfliteInterpreter =
                Interpreter(
                    modelBuffer,
                    options
                )

            interpreter = tfliteInterpreter

            // -------------------------------------------------------------
            // Inspect input tensor
            // -------------------------------------------------------------

            val inputTensor =
                tfliteInterpreter.getInputTensor(0)

            val inputShape =
                inputTensor.shape()

            val detectedInputType =
                inputTensor.dataType()

            require(inputShape.size == 4) {
                "Unsupported TFLite input shape: " +
                        inputShape.contentToString() +
                        ". Expected [1,H,W,3]."
            }

            require(inputShape[0] == 1) {
                "Unsupported batch size: ${inputShape[0]}. " +
                        "Expected batch size 1."
            }

            require(inputShape[3] == 3) {
                "Unsupported channel count: ${inputShape[3]}. " +
                        "Expected RGB input with 3 channels."
            }

            require(
                detectedInputType == DataType.FLOAT32
            ) {
                "Unsupported input data type: " +
                        "$detectedInputType. " +
                        "This implementation currently supports FLOAT32 input."
            }

            inputHeight =
                inputShape[1]

            inputWidth =
                inputShape[2]

            inputDataType =
                detectedInputType

            // -------------------------------------------------------------
            // Inspect output tensor
            // -------------------------------------------------------------

            val outputTensor =
                tfliteInterpreter.getOutputTensor(0)

            val outputShape =
                outputTensor.shape()

            val detectedOutputType =
                outputTensor.dataType()

            require(outputShape.size >= 2) {
                "Unsupported TFLite output shape: " +
                        outputShape.contentToString()
            }

            require(
                detectedOutputType == DataType.FLOAT32
            ) {
                "Unsupported output data type: " +
                        "$detectedOutputType. " +
                        "This implementation currently supports FLOAT32 output."
            }

            embeddingDimension =
                outputShape.last()

            require(embeddingDimension > 0) {
                "Invalid embedding dimension: " +
                        embeddingDimension
            }

            outputDataType =
                detectedOutputType

            Log.i(
                TAG,
                """
                Face embedding model initialized.

                Model: $MODEL_FILE_NAME
                Input shape: ${inputShape.contentToString()}
                Input type: $inputDataType
                Output shape: ${outputShape.contentToString()}
                Output type: $outputDataType
                Embedding dimension: $embeddingDimension
                """.trimIndent()
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to initialize face embedding model",
                e
            )

            interpreter?.close()
            interpreter = null

            throw IllegalStateException(
                "Unable to initialize TFLite face embedding model: " +
                        e.message,
                e
            )
        }
    }

    // ========================================================================
    // MODEL LOADING
    // ========================================================================

    /**
     * Loads the TFLite model directly from the application's assets.
     */
    private fun loadModelFile(
        context: Context,
        fileName: String
    ): ByteBuffer {

        try {

            val assetFileDescriptor =
                context.assets.openFd(fileName)

            val inputStream =
                FileInputStream(
                    assetFileDescriptor.fileDescriptor
                )

            val fileChannel =
                inputStream.channel

            val startOffset =
                assetFileDescriptor.startOffset

            val declaredLength =
                assetFileDescriptor.declaredLength

            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )

        } catch (e: Exception) {

            throw IllegalStateException(
                "Unable to load TFLite model '$fileName' " +
                        "from assets. Make sure the model exists in " +
                        "app/src/main/assets/.",
                e
            )
        }
    }

    // ========================================================================
    // EMBEDDING EXTRACTION
    // ========================================================================

    /**
     * Extracts a normalized face embedding from a detected face.
     *
     * The face is cropped with contextual margin before being resized
     * to the model's actual input dimensions.
     */
    fun extractEmbedding(
        sourceBitmap: Bitmap,
        boundingBox: RectF,
        marginPercent: Float = DEFAULT_MARGIN_PERCENT
    ): FloatArray {

        require(!sourceBitmap.isRecycled) {
            "Source bitmap has already been recycled."
        }

        require(
            sourceBitmap.width > 0 &&
                    sourceBitmap.height > 0
        ) {
            "Source bitmap has invalid dimensions."
        }

        require(
            boundingBox.width() > 0f &&
                    boundingBox.height() > 0f
        ) {
            "Face bounding box has invalid dimensions: $boundingBox"
        }

        require(
            marginPercent >= 0f
        ) {
            "marginPercent must be >= 0."
        }

        val croppedFace =
            cropFaceWithMargin(
                sourceBitmap,
                boundingBox,
                marginPercent
            )

        val resizedFace =
            try {

                Bitmap.createScaledBitmap(
                    croppedFace,
                    inputWidth,
                    inputHeight,
                    true
                )

            } catch (e: Exception) {

                if (
                    croppedFace !== sourceBitmap &&
                    !croppedFace.isRecycled
                ) {
                    croppedFace.recycle()
                }

                throw IllegalStateException(
                    "Failed to resize face crop to " +
                            "${inputWidth}x${inputHeight}.",
                    e
                )
            }

        try {

            val embedding =
                runTfliteInference(
                    resizedFace
                )

            return l2Normalize(
                embedding
            )

        } finally {

            if (
                croppedFace !== sourceBitmap &&
                !croppedFace.isRecycled
            ) {
                croppedFace.recycle()
            }

            if (
                resizedFace !== croppedFace &&
                !resizedFace.isRecycled
            ) {
                resizedFace.recycle()
            }
        }
    }

    // ========================================================================
    // TFLITE INFERENCE
    // ========================================================================

    /**
     * Runs the TFLite model on a resized RGB bitmap.
     *
     * Current implementation supports FLOAT32 model input.
     *
     * Pixel normalization:
     *
     * [0,255] -> [-1,1]
     *
     * This normalization MUST match the preprocessing expected by
     * the actual embedding model.
     */
    private fun runTfliteInference(
        bitmap: Bitmap
    ): FloatArray {

        val activeInterpreter =
            interpreter
                ?: throw IllegalStateException(
                    "TFLite interpreter is not initialized."
                )

        require(
            inputDataType == DataType.FLOAT32
        ) {
            "Unsupported input type: $inputDataType"
        }

        require(
            outputDataType == DataType.FLOAT32
        ) {
            "Unsupported output type: $outputDataType"
        }

        val inputBufferSize =
            inputWidth *
                    inputHeight *
                    3 *
                    Float.SIZE_BYTES

        val inputBuffer =
            ByteBuffer.allocateDirect(
                inputBufferSize
            ).apply {
                order(
                    ByteOrder.nativeOrder()
                )
            }

        val pixels =
            IntArray(
                inputWidth * inputHeight
            )

        bitmap.getPixels(
            pixels,
            0,
            inputWidth,
            0,
            0,
            inputWidth,
            inputHeight
        )

        for (pixel in pixels) {

            val red =
                (pixel shr 16) and 0xFF

            val green =
                (pixel shr 8) and 0xFF

            val blue =
                pixel and 0xFF

            /*
             * IMPORTANT:
             *
             * This assumes the packaged model expects
             * [-1,1] normalized RGB values.
             *
             * Verify this against the actual model's
             * training/preprocessing documentation.
             */
            inputBuffer.putFloat(
                (red - 127.5f) / 127.5f
            )

            inputBuffer.putFloat(
                (green - 127.5f) / 127.5f
            )

            inputBuffer.putFloat(
                (blue - 127.5f) / 127.5f
            )
        }

        inputBuffer.rewind()

        val output =
            Array(1) {
                FloatArray(
                    embeddingDimension
                )
            }

        activeInterpreter.run(
            inputBuffer,
            output
        )

        return output[0]
    }

    // ========================================================================
    // FACE CROPPING
    // ========================================================================

    /**
     * Creates a generous crop around the detected face.
     *
     * The crop is intentionally larger than the ML Kit bounding box
     * so that the embedding receives useful contextual facial regions
     * such as the forehead, chin and sides of the head.
     */
    fun cropFaceWithMargin(
        source: Bitmap,
        box: RectF,
        marginPercent: Float = DEFAULT_MARGIN_PERCENT
    ): Bitmap {

        require(!source.isRecycled) {
            "Source bitmap has already been recycled."
        }

        require(
            box.width() > 0f &&
                    box.height() > 0f
        ) {
            "Invalid face bounding box: $box"
        }

        val marginWidth =
            box.width() * marginPercent

        val marginHeight =
            box.height() * marginPercent

        val left =
            max(
                0,
                (box.left - marginWidth).toInt()
            )

        val top =
            max(
                0,
                (box.top - marginHeight).toInt()
            )

        val right =
            min(
                source.width,
                (box.right + marginWidth).toInt()
            )

        val bottom =
            min(
                source.height,
                (box.bottom + marginHeight).toInt()
            )

        val cropWidth =
            max(
                1,
                right - left
            )

        val cropHeight =
            max(
                1,
                bottom - top
            )

        return Bitmap.createBitmap(
            source,
            left,
            top,
            cropWidth,
            cropHeight
        )
    }

    // ========================================================================
    // NORMALIZATION
    // ========================================================================

    /**
     * L2-normalizes an embedding.
     *
     * Normalized embeddings allow cosine similarity to be calculated
     * efficiently using the dot product.
     */
    private fun l2Normalize(
        embedding: FloatArray
    ): FloatArray {

        var sumSquares = 0.0

        for (value in embedding) {
            sumSquares +=
                value.toDouble() *
                        value.toDouble()
        }

        val magnitude =
            sqrt(sumSquares)

        if (
            magnitude <=
            Float.MIN_VALUE.toDouble()
        ) {
            Log.w(
                TAG,
                "Model returned a zero-magnitude embedding."
            )

            return FloatArray(
                embedding.size
            )
        }

        return FloatArray(
            embedding.size
        ) { index ->

            (
                    embedding[index] /
                            magnitude.toFloat()
                    )
        }
    }

    // ========================================================================
    // RESOURCE MANAGEMENT
    // ========================================================================

    override fun close() {

        try {

            interpreter?.close()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error closing TFLite interpreter.",
                e
            )

        } finally {

            interpreter = null
        }
    }
}
