#!/usr/bin/env python3
"""Produce original aligned PCM stems and timelines with Python's standard library."""
import array
import json
import math
from pathlib import Path
import sys
import wave

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
RATE = 48000


def tone(pcm, start_ms, end_ms, midi, gain, guide=False):
    start = round(start_ms * RATE / 1000)
    end = min(len(pcm), round(end_ms * RATE / 1000))
    hz = 440 * 2 ** ((midi - 69) / 12)
    for i in range(start, end):
        t = (i - start) / RATE
        remaining = (end - i) / RATE
        envelope = min(1, t / 0.012, remaining / 0.035)
        phase = 2 * math.pi * hz * t
        value = math.sin(phase)
        if guide:
            value = (value + 0.2 * math.sin(2 * phase)) / 1.2
        else:
            envelope *= math.exp(-3 * t)
        pcm[i] = max(-32768, min(32767, pcm[i] + round(value * envelope * gain * 32767)))


def write_wav(path, pcm):
    if sys.byteorder != "little":
        pcm.byteswap()
    with wave.open(str(path), "wb") as out:
        out.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        out.writeframes(pcm.tobytes())


def main():
    (ASSETS / "audio").mkdir(parents=True, exist_ok=True)
    sources = json.loads((ROOT / "tools/song_sources.json").read_text())
    songs = []
    for source in sources["songs"]:
        beat_ms = 60000 / source["bpm"]
        beat = 0
        notes = []
        for line in source["melody"]:
            for token in line.split():
                pitch, duration = token.split(":")
                start = round(1000 + beat * beat_ms)
                beat += float(duration)
                end = round(1000 + beat * beat_ms)
                if int(pitch):
                    notes.append({"startMs": start, "endMs": end, "midiNote": int(pitch)})
        music_end_ms = round(1000 + beat * beat_ms)
        duration_ms = music_end_ms + 500
        frames = duration_ms * RATE // 1000
        guide = array.array("h", [0]) * frames
        accompaniment = array.array("h", [0]) * frames
        for note in notes:
            tone(guide, note["startMs"], note["endMs"], note["midiNote"], 0.24, True)
        if "accompanimentNotes" in source:
            for b, length, pitch in source["accompanimentNotes"]:
                tone(accompaniment, round(1000 + b * beat_ms),
                     round(1000 + (b + length) * beat_ms), pitch, 0.09)
        else:
            for b in range(math.ceil(beat)):
                start = round(1000 + b * beat_ms)
                end = min(music_end_ms, round(start + beat_ms * 0.8))
                tone(accompaniment, start, end, source["bass"] + (7 if b % 2 else 0), 0.16)
        identifier = source["id"]
        guide_path, backing_path = f"audio/{identifier}_guide.wav", f"audio/{identifier}_accompaniment.wav"
        write_wav(ASSETS / guide_path, guide)
        write_wav(ASSETS / backing_path, accompaniment)
        songs.append({"id": identifier, "title": source["title"], "bpm": source["bpm"], "bassMidi": source["bass"], "durationMs": duration_ms,
                      "accompaniment": backing_path, "guideMelody": guide_path, "notes": notes})
        print(f"{identifier}: {len(notes)} notes, {duration_ms / 1000:.1f}s")
    (ASSETS / "songs.json").write_text(json.dumps({"songs": songs}, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
