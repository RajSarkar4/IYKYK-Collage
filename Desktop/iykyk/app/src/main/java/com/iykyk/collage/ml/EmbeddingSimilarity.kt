package com.iykyk.collage.ml

import kotlin.math.sqrt

/**
 * Utility functions for comparing and averaging face embeddings.
 *
 * Embeddings are expected to be FloatArray vectors produced by FaceEmbedder.
 * They should normally be L2-normalized before identity comparison.
 */
object EmbeddingSimilarity {

    /**
     * Calculates cosine similarity between two embedding vectors.
     *
     * Cosine similarity:
     *   1.0  -> same direction / highly similar
     *   0.0  -> unrelated directions
     *  -1.0  -> opposite directions
     *
     * The vectors do not have to be pre-normalized because this method
     * calculates their norms internally.
     *
     * @return similarity in the range [-1.0, 1.0].
     */
    fun cosineSimilarity(
        vecA: FloatArray,
        vecB: FloatArray
    ): Float {
        require(vecA.isNotEmpty()) {
            "Embedding A must not be empty"
        }

        require(vecA.size == vecB.size) {
            "Embedding dimensions must match: ${vecA.size} != ${vecB.size}"
        }

        var dotProduct = 0.0
        var normASquared = 0.0
        var normBSquared = 0.0

        for (i in vecA.indices) {
            val a = vecA[i].toDouble()
            val b = vecB[i].toDouble()

            dotProduct += a * b
            normASquared += a * a
            normBSquared += b * b
        }

        if (normASquared <= 0.0 || normBSquared <= 0.0) {
            return 0f
        }

        val similarity =
            dotProduct / (sqrt(normASquared) * sqrt(normBSquared))

        // Protect against tiny floating-point errors.
        return similarity
            .toFloat()
            .coerceIn(-1.0f, 1.0f)
    }

    /**
     * L2-normalizes an embedding in-place.
     *
     * After normalization:
     *     sqrt(sum(x[i]^2)) ~= 1
     *
     * Returns the same FloatArray instance.
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        if (vector.isEmpty()) {
            return vector
        }

        var sumSquares = 0.0

        for (value in vector) {
            val v = value.toDouble()
            sumSquares += v * v
        }

        val norm = sqrt(sumSquares)

        if (norm <= 1e-12) {
            return vector
        }

        val inverseNorm = 1.0 / norm

        for (i in vector.indices) {
            vector[i] = (vector[i].toDouble() * inverseNorm).toFloat()
        }

        return vector
    }

    /**
     * Computes the centroid of a collection of embeddings.
     *
     * The embeddings are averaged component-by-component and the resulting
     * centroid is L2-normalized.
     *
     * All embeddings must have the same dimension.
     */
    fun computeCentroid(
        embeddings: List<FloatArray>,
        dimension: Int
    ): FloatArray {
        require(dimension > 0) {
            "Embedding dimension must be greater than zero"
        }

        if (embeddings.isEmpty()) {
            return FloatArray(dimension)
        }

        require(
            embeddings.all { it.size == dimension }
        ) {
            "All embeddings must have dimension $dimension"
        }

        val centroid = FloatArray(dimension)

        for (embedding in embeddings) {
            for (i in 0 until dimension) {
                centroid[i] += embedding[i]
            }
        }

        val count = embeddings.size.toFloat()

        for (i in centroid.indices) {
            centroid[i] /= count
        }

        return l2Normalize(centroid)
    }

    /**
     * Computes a weighted centroid of embeddings where higher quality observations contribute more.
     */
    fun computeWeightedCentroid(
        embeddings: List<FloatArray>,
        weights: List<Float>,
        dimension: Int
    ): FloatArray {
        if (embeddings.isEmpty() || weights.isEmpty()) return FloatArray(dimension)
        val centroid = FloatArray(dimension)
        var totalWeight = 0f

        for (idx in embeddings.indices) {
            val weight = maxOf(0.01f, weights.getOrElse(idx) { 1.0f })
            val emb = embeddings[idx]
            for (i in 0 until dimension) {
                centroid[i] += emb[i] * weight
            }
            totalWeight += weight
        }

        if (totalWeight > 0f) {
            for (i in 0 until dimension) {
                centroid[i] /= totalWeight
            }
        }

        return l2Normalize(centroid)
    }

    /**
     * Computes cosine similarity between two already L2-normalized vectors.
     *
     * This is faster than cosineSimilarity() because it does not calculate
     * vector norms again.
     *
     * Use this only when both vectors are guaranteed to be normalized.
     */
    fun normalizedCosineSimilarity(
        vecA: FloatArray,
        vecB: FloatArray
    ): Float {
        require(vecA.isNotEmpty()) {
            "Embedding A must not be empty"
        }

        require(vecA.size == vecB.size) {
            "Embedding dimensions must match: ${vecA.size} != ${vecB.size}"
        }

        var dotProduct = 0.0

        for (i in vecA.indices) {
            dotProduct += vecA[i].toDouble() * vecB[i].toDouble()
        }

        return dotProduct
            .toFloat()
            .coerceIn(-1.0f, 1.0f)
    }

    /**
     * Checks whether an embedding is valid for identity matching.
     */
    fun isValidEmbedding(
        embedding: FloatArray,
        expectedDimension: Int
    ): Boolean {
        if (embedding.size != expectedDimension) return false
        if (embedding.isEmpty()) return false

        var sumSquares = 0.0

        for (value in embedding) {
            if (!value.isFinite()) return false

            val v = value.toDouble()
            sumSquares += v * v
        }

        return sumSquares > 1e-12
    }
}
