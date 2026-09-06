package com.iykyk.collage.processing

import android.util.Log
import com.iykyk.collage.domain.model.Appearance
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation

/**
 * Implements temporal appearance tracking to count continuous visible segments of each person.
 */
class AppearanceTracker(
    private val config: FaceMatchingConfig = FaceMatchingConfig()
) {

    companion object {
        private const val TAG = "AppearanceTracker"
    }

    /**
     * Groups face observations into continuous temporal appearance segments.
     */
    fun trackAppearances(
        personId: Int,
        sortedObservations: List<FaceObservation>,
        representativeSelector: (List<FaceObservation>) -> FaceObservation
    ): List<Appearance> {
        if (sortedObservations.isEmpty()) return emptyList()

        val appearances = mutableListOf<Appearance>()
        var currentSegment = mutableListOf<FaceObservation>()
        var appearanceIndex = 1

        for (i in sortedObservations.indices) {
            val obs = sortedObservations[i]

            if (currentSegment.isEmpty()) {
                currentSegment.add(obs)
            } else {
                val prevObs = currentSegment.last()
                val gapMs = obs.timestampMs - prevObs.timestampMs

                if (gapMs <= config.appearanceGapThresholdMs) {
                    currentSegment.add(obs)
                } else {
                    val representative = representativeSelector(currentSegment)
                    appearances.add(
                        Appearance(
                            personId = personId,
                            appearanceIndex = appearanceIndex++,
                            startTimestampMs = currentSegment.first().timestampMs,
                            endTimestampMs = currentSegment.last().timestampMs,
                            observations = currentSegment.toList(),
                            representativeObservation = representative,
                            bestEmbedding = representative.embedding,
                            bestFrameScore = representative.representativeScore
                        )
                    )
                    currentSegment = mutableListOf(obs)
                }
            }
        }

        if (currentSegment.isNotEmpty()) {
            val representative = representativeSelector(currentSegment)
            appearances.add(
                Appearance(
                    personId = personId,
                    appearanceIndex = appearanceIndex,
                    startTimestampMs = currentSegment.first().timestampMs,
                    endTimestampMs = currentSegment.last().timestampMs,
                    observations = currentSegment.toList(),
                    representativeObservation = representative,
                    bestEmbedding = representative.embedding,
                    bestFrameScore = representative.representativeScore
                )
            )
        }

        Log.d(TAG, "Person $personId: Total observations=${sortedObservations.size}, Continuous appearances=${appearances.size}")

        return appearances
    }
}
