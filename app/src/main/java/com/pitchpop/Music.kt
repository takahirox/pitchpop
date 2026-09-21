package com.pitchpop

import kotlin.math.*

data class Note(val startMs: Long, val endMs: Long, val midiNote: Int)
data class Song(val id: String, val title: String, val accompaniment: String,
                val guideMelody: String, val durationMs: Long, val notes: List<Note>, val bpm: Int = 120, val bassMidi: Int = 48) {
    fun targetAt(timeMs: Long): Note? = notes.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
}
data class PitchFrame(val timestampMs: Long, val frequencyHz: Double, val confidence: Double, val voiced: Boolean) {
    val midiPitch: Double get() = if (frequencyHz > 0) 69 + 12 * log2(frequencyHz / 440) else Double.NaN
}

/** Fold only whole octaves; retain the singer's signed semitone/cents error. */
fun octaveAlignedPitch(midiPitch: Double, targetMidi: Int, allowOctaveDifferences: Boolean = true): Double {
    if (!allowOctaveDifferences || !midiPitch.isFinite()) return midiPitch
    return midiPitch + 12 * round((targetMidi - midiPitch) / 12)
}
data class ScoringConfig(val maxErrorCents: Double = 200.0, val pointsPerSecond: Double = 100.0,
                         val minConfidence: Double = 0.8, val allowOctaveDifferences: Boolean = true)
class ScoringEngine(val config: ScoringConfig = ScoringConfig()) {
    var score = 0.0; private set
    fun accuracy(frame: PitchFrame, target: Note?): Double {
        if (!frame.voiced || frame.confidence < config.minConfidence || !frame.midiPitch.isFinite() || target == null) return 0.0
        val error = abs(octaveAlignedPitch(frame.midiPitch, target.midiNote, config.allowOctaveDifferences) - target.midiNote) * 100
        return (1 - error / config.maxErrorCents).coerceIn(0.0, 1.0).pow(2)
    }
    fun add(frame: PitchFrame, target: Note?, elapsedSeconds: Double): Double {
        val accuracy = accuracy(frame, target)
        score += accuracy * config.pointsPerSecond * elapsedSeconds.coerceIn(0.0, 0.1)
        return accuracy
    }
}
enum class GuideMode(val label: String) { ON("いつも鳴らす"), OFF("鳴らさない"), ADAPTIVE("上手に歌えたら小さく") }
data class GuideConfig(val windowSeconds: Double = 1.5, val fadeThreshold: Double = 0.7,
                       val recoverThreshold: Double = 0.4, val minVolume: Double = 0.08,
                       val fadePerSecond: Double = 0.35, val recoveryPerSecond: Double = 0.6)
class AdaptiveGuide(private val config: GuideConfig = GuideConfig()) {
    var volume = 1.0; private set
    private var recentAccuracy = 0.0
    fun update(mode: GuideMode, accuracy: Double, dt: Double, hasTarget: Boolean): Double {
        val seconds = dt.coerceIn(0.0, 0.1)
        when (mode) {
            GuideMode.ON -> volume = 1.0
            GuideMode.OFF -> volume = 0.0
            GuideMode.ADAPTIVE -> if (hasTarget) {
                recentAccuracy += (accuracy - recentAccuracy) * (1 - exp(-seconds / config.windowSeconds))
                if (recentAccuracy >= config.fadeThreshold) volume -= config.fadePerSecond * seconds
                if (recentAccuracy <= config.recoverThreshold) volume += config.recoveryPerSecond * seconds
                volume = volume.coerceIn(config.minVolume, 1.0)
            }
        }
        return volume
    }
}
interface PitchDetector { fun detect(samples: ShortArray, sampleRate: Int, timestampMs: Long): PitchFrame }
data class PitchConfig(val minHz: Double = 80.0, val maxHz: Double = 1000.0,
                       val threshold: Double = 0.15, val minRms: Double = 0.008)
class YinDetector(private val config: PitchConfig = PitchConfig()) : PitchDetector {
    override fun detect(samples: ShortArray, sampleRate: Int, timestampMs: Long): PitchFrame {
        fun silence() = PitchFrame(timestampMs, 0.0, 0.0, false)
        val rms = sqrt(samples.sumOf { (it / 32768.0).pow(2) } / samples.size)
        if (rms < config.minRms) return silence()
        val maxTau = min(samples.size / 2, ceil(sampleRate / config.minHz).toInt())
        val minTau = max(2, (sampleRate / config.maxHz).toInt())
        if (maxTau <= minTau) return silence()
        val difference = DoubleArray(maxTau + 1)
        val window = samples.size - maxTau
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (i in 0 until window) { val d = samples[i].toDouble() - samples[i + tau]; sum += d * d }
            difference[tau] = sum
        }
        var running = 0.0
        difference[0] = 1.0
        for (tau in 1..maxTau) {
            running += difference[tau]
            difference[tau] = if (running == 0.0) 1.0 else difference[tau] * tau / running
        }
        var tau = minTau
        while (tau < maxTau) {
            if (difference[tau] < config.threshold) {
                while (tau + 1 < maxTau && difference[tau + 1] < difference[tau]) tau++
                val a = difference[tau - 1]; val b = difference[tau]; val c = difference[tau + 1]
                val denominator = a - 2 * b + c
                val offset = if (abs(denominator) > 1e-12) (0.5 * (a - c) / denominator).coerceIn(-1.0, 1.0) else 0.0
                return PitchFrame(timestampMs, sampleRate / (tau + offset), (1 - b).coerceIn(0.0, 1.0), true)
            }
            tau++
        }
        return silence()
    }
}
