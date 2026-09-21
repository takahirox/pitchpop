package com.pitchpop

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.util.Random

class MusicTest {
    private val target = Note(1000, 2000, 69)
    private fun frame(cents: Double = 0.0, voiced: Boolean = true, confidence: Double = 1.0) =
        PitchFrame(1500, 440 * 2.0.pow(cents / 1200), confidence, voiced)

    @Test fun scoringRewardsAccuracyAndNeverSubtracts() {
        val perfect = ScoringEngine(); val close = ScoringEngine(); val wrong = ScoringEngine()
        repeat(50) { perfect.add(frame(), target, 0.02); close.add(frame(50.0), target, 0.02); wrong.add(frame(250.0), target, 0.02) }
        assertEquals(100.0, perfect.score, 0.001)
        assertTrue(perfect.score > close.score && close.score > wrong.score)
        val before = perfect.score
        perfect.add(frame(), null, 0.02)
        perfect.add(frame(voiced = false), target, 0.02)
        perfect.add(frame(confidence = 0.1), target, 0.02)
        perfect.add(frame(), target, -1.0)
        assertEquals(before, perfect.score, 0.0)
    }
    @Test fun customScoringToleranceChangesTheCurve() {
        val narrow = ScoringEngine(ScoringConfig(maxErrorCents = 100.0))
        val wide = ScoringEngine(ScoringConfig(maxErrorCents = 300.0))
        assertEquals(0.0, narrow.accuracy(frame(150.0), target), 0.0)
        assertTrue(wide.accuracy(frame(150.0), target) > 0.0)
    }
    @Test fun octaveDifferencesShareScoringAndDisplayButKeepPitchErrors() {
        val scoring = ScoringEngine()
        for (octaves in -2..2) {
            for (cents in listOf(-250.0, -50.0, 0.0, 50.0, 250.0)) {
                val sung = frame(octaves * 1200.0 + cents)
                assertEquals("octaves=$octaves cents=$cents", 69 + cents / 100,
                    octaveAlignedPitch(sung.midiPitch, target.midiNote), 1e-9)
                assertEquals(scoring.accuracy(frame(cents), target), scoring.accuracy(sung, target), 1e-9)
            }
        }
        assertEquals(1.0, scoring.accuracy(frame(-1200.0), target), 1e-9)
        assertEquals(0.0, scoring.accuracy(frame(-950.0), target), 1e-9)
        assertEquals(0.0, scoring.accuracy(frame(-1200.0, voiced = false), target), 0.0)
        assertEquals(0.0, scoring.accuracy(frame(-1200.0, confidence = 0.1), target), 0.0)
        assertTrue(octaveAlignedPitch(Double.NaN, 69).isNaN())
    }
    @Test fun disablingOctaveToleranceKeepsAbsolutePitchAndRejectsOctaveMatches() {
        val strict = ScoringEngine(ScoringConfig(allowOctaveDifferences = false))
        val tolerant = ScoringEngine()
        assertEquals(1.0, tolerant.accuracy(frame(-1200.0), target), 1e-9)
        for (octaves in listOf(-2, -1, 1, 2)) {
            val sung = frame(octaves * 1200.0)
            assertEquals(0.0, strict.accuracy(sung, target), 0.0)
            assertEquals(sung.midiPitch, octaveAlignedPitch(sung.midiPitch, target.midiNote, false), 0.0)
        }
        for (cents in listOf(-50.0, 0.0, 50.0, 250.0)) {
            assertEquals(tolerant.accuracy(frame(cents), target), strict.accuracy(frame(cents), target), 1e-9)
        }
    }
    @Test fun timelineUsesExclusiveNoteEndsAndAllowsRests() {
        val song = Song("test", "Test", "", "", 3000, listOf(target, Note(2200, 2500, 72)))
        assertNull(song.targetAt(999)); assertEquals(target, song.targetAt(1000))
        assertEquals(target, song.targetAt(1999)); assertNull(song.targetAt(2000)); assertNull(song.targetAt(2100))
        assertEquals(72, song.targetAt(2200)!!.midiNote); assertNull(song.targetAt(2500))
    }
    @Test fun guideFadesRecoversAndIgnoresRests() {
        val guide = AdaptiveGuide()
        repeat(500) { guide.update(GuideMode.ADAPTIVE, 1.0, 0.02, true) }
        assertEquals(0.08, guide.volume, 0.001)
        repeat(100) { guide.update(GuideMode.ADAPTIVE, 0.0, 0.02, false) }
        assertEquals(0.08, guide.volume, 0.001)
        repeat(500) { guide.update(GuideMode.ADAPTIVE, 0.0, 0.02, true) }
        assertEquals(1.0, guide.volume, 0.001)
        assertEquals(0.0, guide.update(GuideMode.OFF, 1.0, 0.02, true), 0.0)
        assertEquals(1.0, guide.update(GuideMode.ON, 0.0, 0.02, true), 0.0)
    }
    @Test fun yinDetectsVocalRangeIncludingHarmonics() {
        val detector = YinDetector()
        for (hz in listOf(82.41, 110.0, 220.0, 261.63, 440.0, 880.0)) {
            val samples = ShortArray(768) { i ->
                val phase = 2 * PI * hz * i / 16000
                ((sin(phase) * 0.4 + sin(2 * phase) * 0.2 + sin(3 * phase) * 0.1) * 32767).toInt().toShort()
            }
            val result = detector.detect(samples, 16000, 1234)
            assertTrue("$hz Hz should be voiced", result.voiced)
            assertTrue("$hz Hz detected as ${result.frequencyHz}", abs(1200 * log2(result.frequencyHz / hz)) < 15)
            assertEquals(1234, result.timestampMs)
        }
    }
    @Test fun yinRejectsSilenceNoiseAndDc() {
        val detector = YinDetector()
        assertFalse(detector.detect(ShortArray(768), 16000, 0).voiced)
        assertFalse(detector.detect(ShortArray(768) { 2000 }, 16000, 0).voiced)
        val random = Random(42)
        assertFalse(detector.detect(ShortArray(768) { (random.nextInt(16000) - 8000).toShort() }, 16000, 0).voiced)
    }
}
