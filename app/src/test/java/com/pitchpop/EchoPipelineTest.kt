package com.pitchpop

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class EchoPipelineTest {
    @Test fun accompanimentIntroAllowsScoringTheFirstSungNote() {
        for ((id, bpm, midi) in listOf(Triple("chocho", 112, 67), Triple("sakura", 100, 69),
                Triple("kaeru", 96, 60), Triple("tulip", 90, 60))) {
            val accompaniment = stem("accompaniment", id); val melody = stem("guide", id)
            val song = Song(id, "", "", "", 18000, listOf(Note(1000, 1600, midi)), bpm = bpm)
            val voiceHz = 440 * 2.0.pow((midi - 69) / 12.0)
            val arrangement = PlaybackArrangement(song, accompaniment, melody, 16000)
            val size = (((arrangement.lastSample-arrangement.firstSample+319)/320)*320).toInt()
            val reference = ShortArray(size) { i ->
                val sample = arrangement.firstSample+i
                if (sample < arrangement.lastSample) arrangement.sampleAt(sample, 1.0) else 0
            }
            val onset = (arrangement.singingSample-arrangement.firstSample).toInt()
            assertEquals(0, reference.first().toInt())
            for (i in 0 until onset) assertEquals(arrangement.sampleAt(arrangement.firstSample+i, 0.0), reference[i])
            for (delay in listOf(0, 960, 2401)) {
                var phase = 0.0
                val mic = ShortArray(size) { i ->
                    phase += 2*PI*voiceHz*2.0.pow(0.15*sin(2*PI*5*i/16000)/12)/16000
                    val voice = if (i >= onset) 32767*(0.16*sin(phase)+0.09*sin(2*phase+0.4)) else 0.0
                    (voice + (if (i >= delay) 0.55*reference[i-delay] else 0.0)).roundToInt().coerceIn(-32768,32767).toShort()
                }
                val early = runPipeline(reference, mic, onset, midi, onset+4000)
                assertTrue("First note must score within its first quarter-second (song=$id delay=$delay): $early", early >= 1)
                val echoOnly = ShortArray(size) { i -> if (i >= delay) (0.55*reference[i-delay]).toInt().toShort() else 0 }
                println("intro echo song=$id delay=$delay")
                assertEquals(0, runPipeline(reference, echoOnly, onset))
            }
        }
    }
    @Test fun voiceMatchingTheGuidesPitchIsNotBlanketRejected() {
        val reference = reference()
        for (i in 16000 until reference.size) {
            val phase = 2*PI*220*i/16000
            reference[i] = (32767*(0.12*sin(phase)+0.025*sin(2*phase))).roundToInt().toShort()
        }
        var phase = 0.0
        val mic = ShortArray(reference.size) { i ->
            phase += 2*PI*220*2.0.pow(0.15*sin(2*PI*5*i/16000)/12)/16000
            val voice = if (i >= 1*16000) 32767*(0.16*sin(phase)+0.09*sin(2*phase+0.4)+0.04*sin(3*phase+0.9)) else 0.0
            (voice + if (i >= 960) 0.55*reference[i-960] else 0.0).roundToInt().coerceIn(-32768,32767).toShort()
        }
        val accepted = runPipeline(reference, mic, 8*16000, 57)
        println("same-note accepted=$accepted")
        assertTrue("Same-note singing with natural vibrato must survive: $accepted", accepted > 200)
    }
    private fun stem(name: String, id: String = "chocho"): ShortArray {
        val data = File(System.getProperty("pitchpop.assets"), "audio/${id}_$name.wav").readBytes()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(String(data, 36, 4) == "data") // Generator emits canonical PCM WAV files.
        val pcm = ShortArray((data.size - 44) / 2) { buffer.getShort(44 + 2*it) }
        return ShortArray(pcm.size / 3).also { downsampleForPitch(pcm, it) }
    }
    private fun reference(guide: Boolean = true, adaptive: Boolean = false): ShortArray {
        val accompaniment = stem("accompaniment"); val melody = stem("guide")
        val reference = ShortArray(16000 * 18) { (0.65 * (accompaniment[it] + if (guide) melody[it] * (if (adaptive && it > 8*16000) 0.1 else 1.0) else 0.0)).toInt().toShort() }
        return reference
    }
    @Test fun echoOnlyWithGuideOffOrFadingDoesNotEarnPoints() {
        for (reference in listOf(reference(guide = false), reference(adaptive = true))) {
            val mic = ShortArray(reference.size) { i ->
                ((if (i >= 960) 0.55*reference[i-960] else 0.0) +
                    (if (i >= 1380) 0.18*reference[i-1380] else 0.0)).roundToInt().toShort()
            }
            assertEquals(0, runPipeline(reference, mic))
        }
    }
    @Test fun echoOnlyDoesNotEarnPointsAndDoubleTalkStillHasPitch() {
        for (delay in listOf(0, 960, 2401)) {
            val reference = reference()
            val mic = ShortArray(reference.size) { i ->
                ((if (i >= delay) 0.55 * reference[i-delay] else 0.0) +
                    (if (i >= delay+420) 0.18 * reference[i-delay-420] else 0.0)).roundToInt().toShort()
            }
            val accepted = runPipeline(reference, mic)
            println("echo delay=$delay: accepted pitch frames=$accepted")
            assertEquals("Echo-only pitch must not earn credit (delay=$delay)", 0, accepted)
        }
        val reference = reference()
        val mic = ShortArray(reference.size) { i ->
            val phase = 2*PI*220*i/16000
            val voice = if (i >= 1*16000) 32767*(0.16*sin(phase)+0.09*sin(2*phase+0.4)+0.04*sin(3*phase+0.9)) else 0.0
            (voice + if (i >= 960) 0.55*reference[i-960] else 0.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        assertTrue("Singer must remain detectable over playback", runPipeline(reference, mic, 8*16000, 57) > 200)
    }
    private fun runPipeline(reference: ShortArray, microphone: ShortArray, from: Int = 16000, targetMidi: Int? = null, until: Int = reference.size): Int {
        val detector = YinDetector(); val gate = EchoGate(); val score = ScoringEngine(); val voiceGate = StableVoiceGate()
        val window = ShortArray(768); val cleaned = ShortArray(320)
        var previousMic = 0.0; var previousReference = 0.0; var accepted = 0
        SoftwareEchoCanceller().use { echo ->
            for (start in reference.indices step 320) {
                val mic = microphone.copyOfRange(start, start+320)
                val render = reference.copyOfRange(start, start+320)
                echo.process(mic, render, cleaned)
                val allow = gate.allow(previousMic, pcmRms(cleaned), previousReference)
                previousMic = pcmRms(mic); previousReference = pcmRms(render)
                window.copyInto(window, 0, 320)
                for (i in cleaned.indices) window[448+i] = if (allow) cleaned[i] else 0
                val candidate = if (allow) detector.detect(window, 16000, start.toLong()/16-44)
                    else PitchFrame(start.toLong()/16-44, 0.0, 0.0, false)
                val pitch = voiceGate.filter(candidate)
                if (start >= from && start < until) {
                    if (pitch.voiced && pitch.confidence >= 0.8) {
                        if (targetMidi == null || abs(pitch.midiPitch-targetMidi) < 0.5) accepted++
                        score.add(pitch, Note(0, Long.MAX_VALUE, targetMidi ?: pitch.midiPitch.roundToInt()), 0.02)
                    }
                }
            }
        }
        if (targetMidi == null) assertEquals("Echo must not increase score", 0.0, score.score, 0.0)
        return accepted
    }
}
