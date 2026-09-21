package com.pitchpop

import kotlin.math.*

data class IntroNote(val beat: Double, val beats: Double, val midi: Int, val gain: Double)
data class IntroScore(val totalBeats: Int, val notes: List<IntroNote>,
                      val decay: Double = 1.5, val harmonicRolloff: Double = 1.35)

/** Existing published piano introductions. Attribution and usage limits: licenses/Introductions.txt. */
object SongIntroductions {
    // Art Studio Mahoroba MIDI uses 480 ticks/quarter, including rolled-chord offsets.
    private fun midiNote(start: Int, end: Int, pitch: Int, velocity: Int) =
        IntroNote(start / 480.0, (end - start) / 480.0, pitch, .025 * velocity / 105.0)

    private val chocho = IntroScore(8, listOf(
        // moneko: bars 1–2, C major. Personal-use prototype only; see source terms.
        IntroNote(0.0, 1.0, 60, 0.045),
        IntroNote(1.0, 1.0, 64, 0.045),
        IntroNote(2.0, 1.0, 67, 0.045),
        IntroNote(3.0, 1.0, 67, 0.045),
        IntroNote(4.0, 1.0, 64, 0.045),
        IntroNote(5.0, 1.0, 64, 0.045),
        IntroNote(6.0, 2.0, 64, 0.045),
        IntroNote(0.0, 0.5, 48, 0.030),
        IntroNote(0.5, 0.5, 55, 0.030),
        IntroNote(1.0, 0.5, 52, 0.030),
        IntroNote(1.5, 0.5, 55, 0.030),
        IntroNote(2.0, 0.5, 48, 0.030),
        IntroNote(2.5, 0.5, 55, 0.030),
        IntroNote(3.0, 0.5, 52, 0.030),
        IntroNote(3.5, 0.5, 55, 0.030),
        IntroNote(4.0, 0.5, 48, 0.030),
        IntroNote(4.5, 0.5, 55, 0.030),
        IntroNote(5.0, 0.5, 52, 0.030),
        IntroNote(5.5, 0.5, 55, 0.030),
        IntroNote(6.0, 2.0, 48, 0.030)
    ))
    private val sakura = IntroScore(16, listOf(
        // Yamada arrangement, Mahoroba edition: piano bars 1–4, transposed down 5 semitones.
        midiNote(1, 480, 33, 105),
        midiNote(1, 480, 57, 105),
        midiNote(31, 480, 40, 105),
        midiNote(31, 480, 60, 105),
        midiNote(61, 480, 45, 105),
        midiNote(61, 480, 64, 105),
        midiNote(91, 480, 69, 105),
        midiNote(481, 960, 48, 80),
        midiNote(481, 960, 60, 80),
        midiNote(511, 960, 52, 80),
        midiNote(511, 960, 64, 80),
        midiNote(541, 960, 57, 80),
        midiNote(541, 960, 69, 80),
        midiNote(961, 1440, 53, 95),
        midiNote(961, 1920, 62, 95),
        midiNote(991, 1440, 57, 95),
        midiNote(991, 1920, 65, 95),
        midiNote(1021, 1440, 59, 95),
        midiNote(1021, 1920, 71, 95),
        midiNote(1441, 1920, 38, 80),
        midiNote(1921, 2400, 33, 105),
        midiNote(1921, 2400, 57, 105),
        midiNote(1951, 2400, 40, 105),
        midiNote(1951, 2400, 60, 105),
        midiNote(1981, 2400, 45, 105),
        midiNote(1981, 2400, 64, 105),
        midiNote(2011, 2400, 69, 105),
        midiNote(2401, 2880, 48, 80),
        midiNote(2401, 2880, 60, 80),
        midiNote(2431, 2880, 52, 80),
        midiNote(2431, 2880, 64, 80),
        midiNote(2461, 2880, 57, 80),
        midiNote(2461, 2880, 69, 80),
        midiNote(2881, 3360, 53, 95),
        midiNote(2881, 3840, 62, 95),
        midiNote(2911, 3360, 57, 95),
        midiNote(2911, 3840, 65, 95),
        midiNote(2941, 3360, 59, 95),
        midiNote(2941, 3840, 71, 95),
        midiNote(3361, 3840, 38, 80),
        midiNote(3841, 4080, 33, 105),
        midiNote(3841, 4320, 72, 105),
        midiNote(3851, 4320, 76, 105),
        midiNote(4081, 4320, 40, 80),
        midiNote(4321, 4560, 45, 80),
        midiNote(4321, 4800, 69, 80),
        midiNote(4331, 4800, 72, 80),
        midiNote(4561, 4800, 47, 80),
        midiNote(4801, 5040, 50, 95),
        midiNote(4801, 5040, 71, 95),
        midiNote(4801, 5280, 65, 95),
        midiNote(5041, 5280, 52, 80),
        midiNote(5041, 5280, 69, 80),
        midiNote(5281, 5520, 59, 80),
        midiNote(5281, 5760, 53, 80),
        midiNote(5281, 5760, 65, 80),
        midiNote(5521, 5760, 57, 80),
        midiNote(5761, 6240, 52, 105),
        midiNote(5761, 6240, 57, 105),
        midiNote(5771, 6240, 60, 105),
        midiNote(5781, 6240, 64, 105),
        midiNote(6241, 6480, 71, 80),
        midiNote(6241, 6720, 50, 80),
        midiNote(6241, 6720, 62, 80),
        midiNote(6251, 6720, 53, 80),
        midiNote(6251, 6720, 65, 80),
        midiNote(6481, 6720, 69, 80),
        midiNote(6721, 7200, 45, 95),
        midiNote(6721, 7680, 60, 95),
        midiNote(6721, 7680, 69, 95),
        midiNote(6731, 7200, 52, 95),
        midiNote(6731, 7680, 64, 95),
        midiNote(7201, 7440, 59, 80),
        midiNote(7441, 7680, 57, 80)
    ))
    // Kodomo MusiQ piano score p.2, bars 7–8, alternative left-hand staff.
    // Play the published closing two bars as the eight-beat singing introduction.
    private val kaeru = IntroScore(8, listOf(
        IntroNote(0.00, 0.50, 60, 0.045),
        IntroNote(0.50, 0.50, 60, 0.045),
        IntroNote(1.00, 0.50, 62, 0.045),
        IntroNote(1.50, 0.50, 62, 0.045),
        IntroNote(2.00, 0.50, 64, 0.045),
        IntroNote(2.50, 0.50, 64, 0.045),
        IntroNote(3.00, 0.50, 65, 0.045),
        IntroNote(3.50, 0.50, 65, 0.045),
        IntroNote(4.00, 1.00, 64, 0.045),
        IntroNote(5.00, 1.00, 62, 0.045),
        IntroNote(6.00, 1.00, 60, 0.045),
        IntroNote(0.00, 1.00, 48, 0.025),
        IntroNote(1.00, 1.00, 52, 0.025),
        IntroNote(1.00, 1.00, 55, 0.025),
        IntroNote(2.00, 1.00, 48, 0.025),
        IntroNote(3.00, 1.00, 52, 0.025),
        IntroNote(3.00, 1.00, 55, 0.025),
        IntroNote(4.00, 1.00, 48, 0.025),
        IntroNote(5.00, 1.00, 53, 0.025),
        IntroNote(5.00, 1.00, 55, 0.025),
        IntroNote(6.00, 1.00, 48, 0.025),
        IntroNote(7.00, 1.00, 52, 0.025),
        IntroNote(7.00, 1.00, 55, 0.025)
    ))
    private val tulip = IntroScore(16, listOf(
        IntroNote(0.00, 1.00, 60, 0.035),
        IntroNote(0.00, 1.00, 67, 0.035),
        IntroNote(1.00, 1.00, 60, 0.035),
        IntroNote(1.00, 1.00, 67, 0.035),
        IntroNote(2.00, 1.00, 60, 0.035),
        IntroNote(2.00, 1.00, 64, 0.035),
        IntroNote(3.00, 1.00, 60, 0.035),
        IntroNote(3.00, 1.00, 67, 0.035),
        IntroNote(4.00, 1.00, 65, 0.035),
        IntroNote(4.00, 1.00, 69, 0.035),
        IntroNote(5.00, 1.00, 65, 0.035),
        IntroNote(5.00, 1.00, 69, 0.035),
        IntroNote(6.00, 1.00, 64, 0.035),
        IntroNote(6.00, 1.00, 67, 0.035),
        IntroNote(8.00, 1.00, 60, 0.035),
        IntroNote(8.00, 1.00, 64, 0.035),
        IntroNote(9.00, 1.00, 64, 0.035),
        IntroNote(10.00, 1.00, 59, 0.035),
        IntroNote(10.00, 1.00, 62, 0.035),
        IntroNote(11.00, 1.00, 62, 0.035),
        IntroNote(12.00, 3.00, 60, 0.035),
        IntroNote(0.00, 1.00, 52, 0.025),
        IntroNote(0.00, 1.00, 55, 0.025),
        IntroNote(1.00, 1.00, 52, 0.025),
        IntroNote(1.00, 1.00, 55, 0.025),
        IntroNote(2.00, 1.00, 48, 0.025),
        IntroNote(2.00, 1.00, 55, 0.025),
        IntroNote(3.00, 1.00, 52, 0.025),
        IntroNote(3.00, 1.00, 55, 0.025),
        IntroNote(4.00, 0.50, 53, 0.025),
        IntroNote(4.50, 0.50, 55, 0.025),
        IntroNote(5.00, 0.50, 57, 0.025),
        IntroNote(5.50, 0.50, 59, 0.025),
        IntroNote(6.00, 1.00, 60, 0.025),
        IntroNote(7.00, 1.00, 48, 0.025),
        IntroNote(8.00, 1.00, 43, 0.025),
        IntroNote(9.00, 1.00, 55, 0.025),
        IntroNote(10.00, 1.00, 43, 0.025),
        IntroNote(11.00, 1.00, 53, 0.025),
        IntroNote(12.00, 1.00, 48, 0.025),
        IntroNote(12.00, 1.00, 52, 0.025),
        IntroNote(13.00, 1.00, 55, 0.025),
        IntroNote(14.00, 2.00, 48, 0.025),
        IntroNote(14.00, 2.00, 52, 0.025)
    ))
    fun score(id: String): IntroScore = when (id) {
        "chocho" -> chocho
        "sakura" -> sakura
        "kaeru" -> kaeru
        "tulip" -> tulip
        else -> error("No sourced introduction for $id")
    }

    fun durationMs(song: Song): Long = (score(song.id).totalBeats * 60000.0 / song.bpm).roundToLong()

    fun render(song: Song, frames: Int, sampleRate: Int): ShortArray {
        val score = score(song.id)
        val mix = DoubleArray(frames)
        for (note in score.notes) {
            val first = (note.beat * 60 / song.bpm * sampleRate).roundToInt()
            val last = min(frames, ((note.beat + note.beats) * 60 / song.bpm * sampleRate).roundToInt())
            val hz = 440 * 2.0.pow((note.midi - 69) / 12.0)
            val weights = (1..16).map { if (hz * it < sampleRate / 2) 1.0 / it.toDouble().pow(score.harmonicRolloff) else 0.0 }
            for (i in first until last) {
                val t = (i - first).toDouble() / sampleRate
                val remaining = (last - i).toDouble() / sampleRate
                val edge = minOf(1.0, t / .025, remaining / .06)
                val envelope = sin(edge * PI / 2).pow(2) * exp(-score.decay * t)
                val phase = 2 * PI * hz * t
                var tone = 0.0
                for (h in weights.indices) tone += weights[h] * sin((h + 1) * phase)
                mix[i] += tone * envelope * note.gain
            }
        }
        return ShortArray(frames) { (mix[it] * 32767).roundToInt().coerceIn(-32768, 32767).toShort() }
    }
}
