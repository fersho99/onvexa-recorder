package com.onvexa.recorder.app.transcription

import kotlin.math.sqrt

/** Agrupacion de embeddings de Vosk por similitud. No identifica personas ni separa voces superpuestas. */
class SpeakerClusters(private val maxDistance: Double = 0.35, private val maxSpeakers: Int = 16) {
    private val centers = mutableListOf<DoubleArray>()
    private val counts = mutableListOf<Int>()
    fun assign(vector: DoubleArray, frames: Int): Int? {
        if (frames < 60 || vector.isEmpty() || vector.any { !it.isFinite() }) return null
        val norm = sqrt(vector.sumOf { it * it })
        if (norm < 1e-8) return null
        val normalized = DoubleArray(vector.size) { vector[it] / norm }
        if (centers.any { it.size != normalized.size }) return null
        val distances = centers.map { center ->
            val cn = sqrt(center.sumOf { it * it })
            1.0 - normalized.indices.sumOf { normalized[it] * center[it] } / cn.coerceAtLeast(1e-8)
        }
        val nearest = distances.indices.minByOrNull { distances[it] }
        if (nearest == null || (distances[nearest] > maxDistance && centers.size < maxSpeakers)) {
            centers.add(normalized); counts.add(1); return centers.lastIndex
        }
        val count = counts[nearest] + 1
        centers[nearest].indices.forEach { i -> centers[nearest][i] += (normalized[i] - centers[nearest][i]) / count }
        counts[nearest] = count
        return nearest
    }
}
