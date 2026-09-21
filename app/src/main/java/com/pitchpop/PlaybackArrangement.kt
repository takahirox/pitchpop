package com.pitchpop

import kotlin.math.*

/** A sourced piano introduction for each song, then the full song. */
class PlaybackArrangement(val song: Song, private val accompaniment: ShortArray,
                          private val melody: ShortArray, private val sampleRate: Int = 48000) {
    init { require(song.bpm in 40..160 && sampleRate > 0) }
    val singingStartMs = song.notes.first().startMs
    val introMs = SongIntroductions.durationMs(song)
    val startMs = singingStartMs - introMs
    val endMs = song.durationMs
    val firstSample = startMs * sampleRate / 1000
    val singingSample = singingStartMs * sampleRate / 1000
    val lastSample = endMs * sampleRate / 1000
    private val intro = SongIntroductions.render(song, (singingSample-firstSample).toInt(), sampleRate)
    init {
        require(accompaniment.size == melody.size && lastSample <= accompaniment.size)
    }
    fun sampleAt(songSample: Long, guideGain: Double): Short {
        require(songSample >= firstSample && songSample < lastSample)
        val value = if (songSample < singingSample) {
            val elapsed = songSample - firstSample
            // Use the song's tempo and key, with a gentle entrance and no guide melody.
            val fade = (elapsed.toDouble() / (sampleRate * 0.08)).coerceIn(0.0, 1.0)
            intro[elapsed.toInt()] * 0.65 * fade
        } else {
            accompaniment[songSample.toInt()] * 0.65 + melody[songSample.toInt()] * guideGain * 0.65
        }
        return value.roundToInt().coerceIn(-32768, 32767).toShort()
    }
}
