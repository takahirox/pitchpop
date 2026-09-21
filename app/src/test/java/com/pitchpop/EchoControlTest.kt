package com.pitchpop

import org.junit.Assert.*
import org.junit.Test

class EchoControlTest {
    @Test fun briefPitchRemnantsAreRejectedButSustainedVoiceIsAccepted() {
        val gate = StableVoiceGate()
        val voice = PitchFrame(0, 220.0, 0.99, true)
        repeat(6) { assertFalse(gate.filter(voice).voiced) }
        assertTrue(gate.filter(voice).voiced)
        assertFalse(gate.filter(voice.copy(voiced = false)).voiced)
        assertFalse(gate.filter(voice).voiced)
        assertFalse(gate.filter(voice.copy(frequencyHz = 440.0)).voiced)
    }
    @Test fun referenceContainsAcceptedPcmIncludingPartialWritesAndWrap() {
        val reference = PlaybackReference(8)
        val source = ShortArray(12) { it.toShort() }
        reference.append(source, 0, 3)
        reference.append(source, 3, 4)
        val out = ShortArray(3)
        assertTrue(reference.read(2, out)); assertArrayEquals(shortArrayOf(2, 3, 4), out)
        assertFalse(reference.read(5, out)); assertArrayEquals(shortArrayOf(0, 0, 0), out)
        reference.append(source, 7, 5)
        assertFalse(reference.read(3, out))
        assertTrue(reference.read(8, out)); assertArrayEquals(shortArrayOf(8, 9, 10), out)
    }
    @Test fun completedPlaybackPadsTheLastCaptureFrameWithSilence() {
        val reference = PlaybackReference(8)
        reference.append(shortArrayOf(1, 2, 3), 0, 3)
        val out = ShortArray(3)
        assertFalse(reference.read(2, out))
        reference.finish()
        assertTrue(reference.read(2, out))
        assertArrayEquals(shortArrayOf(3, 0, 0), out)
        assertTrue(reference.read(3, out))
        assertArrayEquals(shortArrayOf(0, 0, 0), out)
    }
    @Test fun clockDoesNotChaseSchedulerJitterButResetsOnStalls() {
        val clock = EchoReferenceClock()
        assertEquals(0L, clock.nextFrame(1)); assertTrue(clock.discontinuity)
        assertEquals(960L, clock.nextFrame(1000)); assertFalse(clock.discontinuity)
        assertEquals(1920L, clock.nextFrame(1750)); assertFalse(clock.discontinuity)
        assertEquals(2880L, clock.nextFrame(12000)); assertFalse(clock.discontinuity)
        assertEquals(60000L, clock.nextFrame(60000)); assertTrue(clock.discontinuity)
        clock.invalidate()
        assertEquals(60000L, clock.nextFrame(60000)); assertTrue(clock.discontinuity)
    }
    @Test fun gateRejectsConvergenceAndEchoButAllowsIndependentVoice() {
        val gate = EchoGate()
        repeat(59) { assertFalse(gate.allow(0.1, 0.001, 0.1)); assertTrue(gate.calibrating) }
        assertFalse(gate.allow(0.1, 0.001, 0.1)); assertFalse(gate.calibrating)
        assertTrue(gate.allow(0.15, 0.08, 0.1))
        repeat(12) { assertFalse(gate.allow(0.1, 0.001, 0.0)) }
        assertTrue(gate.allow(0.03, 0.03, 0.0))
        assertTrue(EchoGate().allow(0.03, 0.03, 0.0))
    }
    @Test fun referenceAndMicrophoneDownsamplingUseTheSameScale() {
        val destination = ShortArray(2)
        downsampleForPitch(shortArrayOf(3000, 0, -3000, 6000, 6000, 6000), destination)
        assertArrayEquals(shortArrayOf(0, 6000), destination)
    }
}
