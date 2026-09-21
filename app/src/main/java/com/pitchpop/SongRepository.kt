package com.pitchpop

import android.content.res.AssetManager
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SongRepository(private val assets: AssetManager) {
    fun load(): List<Song> {
        val array = JSONObject(assets.open("songs.json").bufferedReader().use { it.readText() }).getJSONArray("songs")
        return (0 until array.length()).map { i ->
            val song = array.getJSONObject(i)
            val notes = song.getJSONArray("notes")
            Song(song.getString("id"), song.getString("title"), song.getString("accompaniment"),
                song.getString("guideMelody"), song.getLong("durationMs"),
                (0 until notes.length()).map { n -> notes.getJSONObject(n).let { Note(it.getLong("startMs"), it.getLong("endMs"), it.getInt("midiNote")) } },
                song.getInt("bpm"), song.getInt("bassMidi"))
        }
    }
    fun pcm(path: String): ShortArray {
        val bytes = assets.open(path).use { it.readBytes() }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Invalid WAV: $path" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var validFormat = false
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            require(size >= 0 && offset.toLong() + 8 + size <= bytes.size)
            if (id == "fmt ") {
                require(size >= 16)
                validFormat = buffer.getShort(offset + 8).toInt() == 1 && buffer.getShort(offset + 10).toInt() == 1 &&
                    buffer.getInt(offset + 12) == AudioEngine.SAMPLE_RATE && buffer.getShort(offset + 22).toInt() == 16
            }
            if (id == "data") {
                require(validFormat && size % 2 == 0) { "Expected mono 48 kHz PCM16: $path" }
                return ShortArray(size / 2) { buffer.getShort(offset + 8 + it * 2) }
            }
            offset += 8 + size + size % 2
        }
        error("Missing WAV data: $path")
    }
}
