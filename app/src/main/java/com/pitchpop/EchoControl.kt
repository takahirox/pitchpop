package com.pitchpop

import kotlin.math.*

/** A bounded, sample-indexed copy of exactly the PCM accepted by AudioTrack. */
class PlaybackReference(private val capacity: Int = 48000 * 2) {
    private val samples = ShortArray(capacity)
    private var written = 0L
    private var finished = false
    @Synchronized fun finish() { finished = true }
    init { require(capacity > 0) }
    @Synchronized fun append(source: ShortArray, offset: Int, count: Int) {
        check(!finished)
        require(offset >= 0 && count >= 0 && offset <= source.size - count)
        for (i in offset until offset + count) samples[(written++ % capacity).toInt()] = source[i]
    }
    @Synchronized fun read(start: Long, destination: ShortArray): Boolean {
        if (start < max(0L, written - capacity) || (!finished && start > written - destination.size)) {
            destination.fill(0)
            return false
        }
        for (i in destination.indices) destination[i] = if (start + i < written) samples[((start + i) % capacity).toInt()] else 0
        return true
    }
}

/** Buffered capture can lag the playback head during computation; preserve its sample clock.
 * Allow up to one second of backlog inside the two-second reference history. */
class EchoReferenceClock(private val hop: Int = 960, private val maxDriftFrames: Int = 48000) {
    private var next = -1L
    var discontinuity = false; private set
    fun nextFrame(playbackHead: Long): Long {
        discontinuity = next < 0 || abs(playbackHead - next) > maxDriftFrames
        if (discontinuity) next = playbackHead / 3 * 3
        return next.also { next += hop }
    }
    fun invalidate() { next = -1 }
}

data class EchoGateConfig(val settleFrames: Int = 60, val referenceFloor: Double = 0.001,
                          val minResidualRatio: Double = 0.22, val echoTailFrames: Int = 13)
/** Avoid credit during filter convergence and when most input was identified as echo. */
class EchoGate(private val config: EchoGateConfig = EchoGateConfig()) {
    private var learnedFrames = 0
    private var tail = 0
    var calibrating = false; private set
    fun allow(micRms: Double, residualRms: Double, referenceRms: Double): Boolean {
        val hasReference = referenceRms >= config.referenceFloor
        if (hasReference) { learnedFrames++; tail = config.echoTailFrames } else tail = max(0, tail - 1)
        calibrating = tail > 0 && learnedFrames < config.settleFrames
        if (calibrating) return false
        return tail == 0 || residualRms >= micRms * config.minResidualRatio
    }
}

fun pcmRms(samples: ShortArray): Double = sqrt(samples.sumOf { (it / 32768.0).pow(2) } / samples.size)

/** Reject brief pitched remnants at note boundaries before display or scoring. */
class StableVoiceGate(private val requiredFrames: Int = 7, private val maxStepSemitones: Double = 2.0) {
    private var consecutive = 0
    private var previous = Double.NaN
    fun filter(frame: PitchFrame): PitchFrame {
        if (!frame.voiced || frame.confidence < 0.8 || !frame.midiPitch.isFinite()) {
            consecutive = 0; previous = Double.NaN
            return frame.copy(voiced = false)
        }
        consecutive = if (previous.isFinite() && abs(frame.midiPitch - previous) <= maxStepSemitones) consecutive + 1 else 1
        previous = frame.midiPitch
        return if (consecutive >= requiredFrames) frame else frame.copy(voiced = false)
    }
}

/** Both the reference and microphone use exactly the same 48 -> 16 kHz reduction. */
fun downsampleForPitch(source: ShortArray, destination: ShortArray) {
    require(source.size == destination.size * 3)
    for (i in destination.indices) destination[i] =
        ((source[i * 3].toInt() + source[i * 3 + 1] + source[i * 3 + 2]) / 3).toShort()
}
