package com.pitchpop

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SongIntroductionsTest {
    @Test fun publishedIntroductionsKeepTheirBarCountsAndSoftEdges() {
        val c = SongIntroductions.score("chocho")
        val s = SongIntroductions.score("sakura")
        assertNotEquals(c.notes, s.notes)
        assertTrue(c.notes.any { it.beat == .5 })
        assertEquals(8, c.totalBeats)
        assertEquals(16, s.totalBeats)
        // Chocho PDF, bars 1–2: right-hand C E G G | E E E(half).
        assertEquals(listOf(60, 64, 67, 67, 64, 64, 64), c.notes.filter { it.midi >= 60 }.map { it.midi })
        assertEquals(20, c.notes.size)
        // Mahoroba MIDI, bars 1–4: preserve all piano voices and rolled-chord timing.
        assertEquals(74, s.notes.size)
        assertTrue(s.notes.any { it.midi == 69 && abs(it.beat - 91.0 / 480) < 1e-9 })
        // Frog: published closing bars, with the final quarter rest before singing.
        val frog = SongIntroductions.score("kaeru")
        val frogMelody = frog.notes.filter { it.midi >= 60 }
        assertEquals(listOf(60, 60, 62, 62, 64, 64, 65, 65, 64, 62, 60), frogMelody.map { it.midi })
        assertEquals(8, frog.totalBeats)
        assertEquals(7.0, frogMelody.last().beat + frogMelody.last().beats, 0.0)
        assertTrue(frog.notes.all { it.midi in 48..65 })
        for ((id, bpm) in listOf("chocho" to 112, "sakura" to 100, "kaeru" to 96, "tulip" to 90)) {
            val song = Song(id, "", "", "", 18000, listOf(Note(1000, 1600, 67)), bpm)
            val frames = (SongIntroductions.durationMs(song)*48).toInt()
            val pcm = SongIntroductions.render(song, frames, 48000)
            val score = SongIntroductions.score(id)
            assertTrue(score.notes.all { it.beat >= 0 && it.beats > 0 && it.beat+it.beats <= score.totalBeats })
            assertEquals(0, pcm.first().toInt())
            assertTrue(abs(pcm.last().toInt()) < 10)
            assertTrue(pcm.maxOf { abs(it.toInt()) } < 10000)
            // The intro must actually supply sound while the echo filter learns.
            val active = pcm.toList().chunked(960).count { pcmRms(it.toShortArray()) >= 0.002 }
            assertTrue("Not enough audible intro for calibration: $id", active >= 65)
        }
    }
}
