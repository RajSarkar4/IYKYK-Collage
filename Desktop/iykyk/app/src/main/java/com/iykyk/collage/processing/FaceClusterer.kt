package com.iykyk.collage.processing

import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.domain.model.PersonCluster

/**
 * Agglomerative face clusterer adapter.
 */
class FaceClusterer(
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) {

    fun clusterFaces(
        observations: List<FaceObservation>,
        embeddingDimension: Int,
        appearanceTracker: AppearanceTracker,
        representativeSelector: (List<FaceObservation>) -> FaceObservation
    ): List<PersonCluster> {
        if (observations.isEmpty()) return emptyList()

        val identityClusterer = PersonIdentityClusterer(config, embeddingDimension)
        val tracker = MultiPersonAppearanceTracker(config) { appearance ->
            identityClusterer.processAppearance(appearance)
        }

        // Group observations by timestamp frame
        val observationsByTimestamp = observations.groupBy { it.timestampMs }
        for ((timestamp, obsList) in observationsByTimestamp) {
            tracker.processFrame(
                timestampMs = timestamp,
                observations = obsList,
                scoreCalculator = { it.representativeScore }
            )
        }

        tracker.finishAll()
        return identityClusterer.getFinalPersonClusters()
    }
}
