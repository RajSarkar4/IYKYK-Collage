package com.iykyk.collage.processing

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.collage.domain.model.Appearance
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.domain.model.PersonCluster
import com.iykyk.collage.ml.EmbeddingSimilarity
import com.iykyk.collage.ml.FaceQualityGate

/**
 * Agglomerative identity clusterer that merges completed appearances into unique person identities.
 *
 * Strictly prevents duplicate people by comparing completed appearance embeddings against existing
 * person centroids. Updates L2-normalized mean centroids upon merging to prevent centroid drift.
 * Ensures EXACTLY ONE overall best representative frame is selected per unique person.
 */
class PersonIdentityClusterer(
    private val config: FaceMatchingConfig = FaceMatchingConfig(),
    private val expectedEmbeddingDimension: Int = 192
) {

    companion object {
        private const val TAG = "PersonIdentityClusterer"
    }

    private class IdentityCluster(
        val personId: Int,
        var label: String,
        val appearances: MutableList<Appearance> = mutableListOf(),
        var centroidEmbedding: FloatArray,
        var overallBestObservation: FaceObservation,
        var overallBestScore: Float,
        var overallBestBitmap: Bitmap? = null
    )

    private val clusters = mutableListOf<IdentityCluster>()
    private var nextPersonId = 1

    /**
     * Integrates a completed appearance into the identity clusters.
     */
    fun processAppearance(appearance: Appearance) {
        val embedding = appearance.bestEmbedding
        if (!EmbeddingSimilarity.isValidEmbedding(embedding, expectedEmbeddingDimension)) {
            Log.w(TAG, "Ignoring completed appearance with invalid embedding.")
            return
        }

        var bestCluster: IdentityCluster? = null
        var maxSimilarity = -1.0f

        // 1. Compare embedding against existing person cluster centroids
        for (cluster in clusters) {
            val similarity = EmbeddingSimilarity.cosineSimilarity(embedding, cluster.centroidEmbedding)
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity
                bestCluster = cluster
            }
        }

        // 2. Merge into existing cluster if similarity meets threshold
        if (bestCluster != null && maxSimilarity >= config.similarityThreshold) {
            Log.i(TAG, "Merging Appearance (duration ${appearance.durationSeconds}s) into Person #${bestCluster.personId} (Similarity: $maxSimilarity >= ${config.similarityThreshold})")

            bestCluster.appearances.add(appearance)

            // Prevent Centroid Drift: Re-compute weighted centroid across appearance embeddings
            val appearanceEmbeddings = bestCluster.appearances.map { it.bestEmbedding }
            val appearanceWeights = bestCluster.appearances.map { maxOf(0.1f, it.bestFrameScore) }
            bestCluster.centroidEmbedding = EmbeddingSimilarity.computeWeightedCentroid(appearanceEmbeddings, appearanceWeights, expectedEmbeddingDimension)

            // Update overall best representative frame if this appearance has a higher quality score
            val currentScore = appearance.bestFrameScore
            val currentPassesGate = FaceQualityGate.isValidForRepresentative(appearance.representativeObservation, config, expectedEmbeddingDimension)
            val overallPassesGate = FaceQualityGate.isValidForRepresentative(bestCluster.overallBestObservation, config, expectedEmbeddingDimension)

            val isBetterShot = if (currentPassesGate && !overallPassesGate) {
                true
            } else if (currentPassesGate == overallPassesGate) {
                currentScore > bestCluster.overallBestScore
            } else {
                false
            }

            if (isBetterShot) {
                bestCluster.overallBestObservation = appearance.representativeObservation
                bestCluster.overallBestScore = currentScore

                // Update representative Bitmap & recycle replaced bitmap safely
                if (appearance.bestFrameBitmap != null && !appearance.bestFrameBitmap.isRecycled) {
                    if (bestCluster.overallBestBitmap != null && bestCluster.overallBestBitmap != appearance.bestFrameBitmap && !bestCluster.overallBestBitmap!!.isRecycled) {
                        bestCluster.overallBestBitmap!!.recycle()
                    }
                    bestCluster.overallBestBitmap = appearance.bestFrameBitmap
                }
            }
        } else {
            // 3. Create a new PersonCluster identity
            val personId = nextPersonId++
            Log.i(TAG, "Created NEW PersonCluster #$personId for Appearance (Max similarity: $maxSimilarity < ${config.similarityThreshold})")

            val newCluster = IdentityCluster(
                personId = personId,
                label = "Person $personId",
                appearances = mutableListOf(appearance),
                centroidEmbedding = embedding.copyOf(),
                overallBestObservation = appearance.representativeObservation,
                overallBestScore = appearance.bestFrameScore,
                overallBestBitmap = appearance.bestFrameBitmap
            )

            clusters.add(newCluster)
        }
    }

    /**
     * Processes a bulk list of completed appearances.
     */
    fun processAppearances(appearances: List<Appearance>) {
        for (app in appearances) {
            processAppearance(app)
        }
    }

    private fun mergeDuplicateClusters() {
        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val clusterA = clusters[i]
                    val clusterB = clusters[j]

                    val similarity = EmbeddingSimilarity.cosineSimilarity(
                        clusterA.centroidEmbedding,
                        clusterB.centroidEmbedding
                    )

                    // If centroid similarity indicates same identity, merge clusterB into clusterA
                    if (similarity >= config.similarityThreshold) {
                        Log.i(TAG, "Post-merge: Merging duplicate cluster Person #${clusterB.personId} into Person #${clusterA.personId} (Similarity: $similarity)")

                        clusterA.appearances.addAll(clusterB.appearances)
                        val allEmbeddings = clusterA.appearances.map { it.bestEmbedding }
                        clusterA.centroidEmbedding = EmbeddingSimilarity.computeCentroid(allEmbeddings, expectedEmbeddingDimension)

                        val scoreA = clusterA.overallBestScore
                        val scoreB = clusterB.overallBestScore
                        val passA = FaceQualityGate.isValidForRepresentative(clusterA.overallBestObservation, config, expectedEmbeddingDimension)
                        val passB = FaceQualityGate.isValidForRepresentative(clusterB.overallBestObservation, config, expectedEmbeddingDimension)

                        if ((passB && !passA) || (passB == passA && scoreB > scoreA)) {
                            clusterA.overallBestObservation = clusterB.overallBestObservation
                            clusterA.overallBestScore = scoreB
                            if (clusterB.overallBestBitmap != null && !clusterB.overallBestBitmap!!.isRecycled) {
                                if (clusterA.overallBestBitmap != null && clusterA.overallBestBitmap != clusterB.overallBestBitmap && !clusterA.overallBestBitmap!!.isRecycled) {
                                    clusterA.overallBestBitmap!!.recycle()
                                }
                                clusterA.overallBestBitmap = clusterB.overallBestBitmap
                            }
                        }

                        clusters.removeAt(j)
                        mergedAny = true
                        break
                    }
                }
                if (mergedAny) break
            }
        }
    }

    /**
     * Returns final list of unique PersonCluster domain models.
     * Guaranteed: exact 1 PersonCluster per unique identity, exact 1 final representative image per person.
     */
    fun getFinalPersonClusters(): List<PersonCluster> {
        mergeDuplicateClusters()
        return clusters.mapIndexed { index, cluster ->
            val displayId = index + 1
            val allObservations = cluster.appearances.flatMap { it.observations }

            PersonCluster(
                id = displayId,
                label = "Person $displayId",
                observations = allObservations,
                centroidEmbedding = cluster.centroidEmbedding,
                appearances = cluster.appearances.toList(),
                representativeObservation = cluster.overallBestObservation,
                representativeBitmap = cluster.overallBestBitmap
            )
        }
    }
}
