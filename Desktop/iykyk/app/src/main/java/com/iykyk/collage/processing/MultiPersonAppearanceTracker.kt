package com.iykyk.collage.processing

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.collage.domain.model.Appearance
import com.iykyk.collage.domain.model.FaceMatchingConfig
import com.iykyk.collage.domain.model.FaceObservation
import com.iykyk.collage.ml.EmbeddingSimilarity
import com.iykyk.collage.ml.FaceQualityGate
import kotlin.math.sqrt

/**
 * High-performance real-time online tracker for multi-person visibility across sampled video frames.
 *
 * Enforces:
 * 1. Simultaneous multi-person active tracking.
 * 2. 1-to-1 frame observation assignment (no observation assigned to multiple tracks).
 * 3. Appearance gap thresholding (1200ms): continuous visibility is ONE appearance.
 * 4. Memory Safety: Retains ONLY the single active best frame Bitmap per track, promptly recycling replaced Bitmaps.
 */
class MultiPersonAppearanceTracker(
    private val config: FaceMatchingConfig = FaceMatchingConfig(),
    private val onAppearanceCompleted: (Appearance) -> Unit
) {

    companion object {
        private const val TAG = "MultiPersonTracker"
    }

    private class ActiveTrack(
        val trackId: Int,
        val firstSeenTimestampMs: Long,
        var lastSeenTimestampMs: Long,
        var observations: MutableList<FaceObservation> = mutableListOf(),
        var bestObservation: FaceObservation,
        var bestScore: Float,
        var bestFrameBitmap: Bitmap? = null,
        var appearanceIndex: Int = 1
    )

    private val activeTracks = mutableListOf<ActiveTrack>()
    private var nextTrackId = 1

    /**
     * Processes face observations detected in a single video frame.
     *
     * @param timestampMs Frame timestamp in milliseconds.
     * @param observations Valid face observations detected in this frame.
     * @param frameBitmap Optional current frame Bitmap (used to extract best representative shot if score improves).
     * @param scoreCalculator Lambda to calculate representative score for an observation.
     */
    fun processFrame(
        timestampMs: Long,
        observations: List<FaceObservation>,
        frameBitmap: Bitmap? = null,
        scoreCalculator: (FaceObservation) -> Float
    ) {
        // 1. First, check for inactive tracks whose gap exceeds appearanceGapThresholdMs
        checkAndCloseInactiveTracks(timestampMs)

        if (observations.isEmpty()) return

        // 2. Perform 1-to-1 matching between incoming observations and active tracks
        val assignments = matchObservationsToTracks(observations)

        val assignedObservationIndices = mutableSetOf<Int>()

        for ((obsIndex, track) in assignments) {
            assignedObservationIndices.add(obsIndex)
            val obs = observations[obsIndex]
            val score = scoreCalculator(obs)

            track.lastSeenTimestampMs = timestampMs
            track.observations.add(obs)

            // Evaluate if this observation is a better representative frame
            val passesRepresentativeGate = FaceQualityGate.isValidForRepresentative(obs, config)
            val currentBestPasses = FaceQualityGate.isValidForRepresentative(track.bestObservation, config)

            val isBetterShot = if (passesRepresentativeGate && !currentBestPasses) {
                true
            } else if (passesRepresentativeGate == currentBestPasses) {
                score > track.bestScore
            } else {
                false
            }

            if (isBetterShot) {
                track.bestObservation = obs
                track.bestScore = score

                // Retain updated best frame Bitmap & recycle old bitmap safely
                if (frameBitmap != null && !frameBitmap.isRecycled) {
                    val newBestCrop = cropRepresentativeBitmap(frameBitmap, obs)
                    if (newBestCrop != null) {
                        if (track.bestFrameBitmap != null && !track.bestFrameBitmap!!.isRecycled) {
                            track.bestFrameBitmap!!.recycle()
                        }
                        track.bestFrameBitmap = newBestCrop
                    }
                }
            }
        }

        // 3. Unassigned observations create new active tracks
        for (i in observations.indices) {
            if (i !in assignedObservationIndices) {
                val obs = observations[i]
                val score = scoreCalculator(obs)
                val newTrackId = nextTrackId++

                val bestCrop = if (frameBitmap != null && !frameBitmap.isRecycled) {
                    cropRepresentativeBitmap(frameBitmap, obs)
                } else null

                val newTrack = ActiveTrack(
                    trackId = newTrackId,
                    firstSeenTimestampMs = timestampMs,
                    lastSeenTimestampMs = timestampMs,
                    observations = mutableListOf(obs),
                    bestObservation = obs,
                    bestScore = score,
                    bestFrameBitmap = bestCrop
                )

                activeTracks.add(newTrack)
                Log.d(TAG, "Started active track #$newTrackId at ${timestampMs}ms")
            }
        }
    }

    /**
     * Performs 1-to-1 matching using embedding similarity and spatial bounding box proximity.
     */
    private fun matchObservationsToTracks(
        observations: List<FaceObservation>
    ): Map<Int, ActiveTrack> {
        if (activeTracks.isEmpty()) return emptyMap()

        data class CandidateMatch(
            val observationIndex: Int,
            val track: ActiveTrack,
            val similarity: Float
        )

        val candidateMatches = mutableListOf<CandidateMatch>()

        for (i in observations.indices) {
            val obs = observations[i]
            for (track in activeTracks) {
                val similarity = EmbeddingSimilarity.cosineSimilarity(obs.embedding, track.bestObservation.embedding)
                
                // Allow match if cosine similarity meets threshold
                if (similarity >= config.similarityThreshold) {
                    candidateMatches.add(CandidateMatch(i, track, similarity))
                }
            }
        }

        // Greedy 1-to-1 selection sorted by highest similarity
        candidateMatches.sortByDescending { it.similarity }

        val assignedObservations = mutableSetOf<Int>()
        val assignedTracks = mutableSetOf<Int>()
        val resultAssignments = mutableMapOf<Int, ActiveTrack>()

        for (match in candidateMatches) {
            if (match.observationIndex !in assignedObservations && match.track.trackId !in assignedTracks) {
                assignedObservations.add(match.observationIndex)
                assignedTracks.add(match.track.trackId)
                resultAssignments[match.observationIndex] = match.track
            }
        }

        return resultAssignments
    }

    /**
     * Closes active tracks that have been inactive longer than appearanceGapThresholdMs.
     */
    private fun checkAndCloseInactiveTracks(currentTimestampMs: Long) {
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            val gapMs = currentTimestampMs - track.lastSeenTimestampMs

            if (gapMs > config.appearanceGapThresholdMs) {
                finalizeAndEmitAppearance(track)
                iterator.remove()
            }
        }
    }

    /**
     * Flushes and closes all remaining active tracks at video completion.
     */
    fun finishAll() {
        for (track in activeTracks) {
            finalizeAndEmitAppearance(track)
        }
        activeTracks.clear()
    }

    private fun finalizeAndEmitAppearance(track: ActiveTrack) {
        val appearance = Appearance(
            personId = track.trackId,
            appearanceIndex = track.appearanceIndex,
            startTimestampMs = track.firstSeenTimestampMs,
            endTimestampMs = track.lastSeenTimestampMs,
            observations = track.observations.toList(),
            representativeObservation = track.bestObservation,
            bestEmbedding = track.bestObservation.embedding,
            bestFrameScore = track.bestScore,
            bestFrameBitmap = track.bestFrameBitmap
        )

        Log.d(TAG, "Finalized Appearance for track #${track.trackId}: ${track.firstSeenTimestampMs}ms–${track.lastSeenTimestampMs}ms (Observations: ${track.observations.size}, Score: ${track.bestScore})")
        onAppearanceCompleted(appearance)
    }

    private fun cropRepresentativeBitmap(source: Bitmap, obs: FaceObservation): Bitmap? {
        return try {
            val box = obs.boundingBox
            val marginW = box.width() * 0.40f
            val marginH = box.height() * 0.40f

            val left = (box.left - marginW).toInt().coerceIn(0, source.width - 1)
            val top = (box.top - marginH).toInt().coerceIn(0, source.height - 1)
            val right = (box.right + marginW).toInt().coerceIn(left + 1, source.width)
            val bottom = (box.bottom + marginH).toInt().coerceIn(top + 1, source.height)

            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop representative bitmap: ${e.message}")
            null
        }
    }
}
