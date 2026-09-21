#!/usr/bin/env python3
"""Validate the shipped catalog, and aligned audio stems."""
import array
import json
from pathlib import Path
import unittest
import wave

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"


class AssetTest(unittest.TestCase):
    def test_catalog_and_stems(self):
        songs = json.loads((ASSETS / "songs.json").read_text())["songs"]
        self.assertEqual({s["id"] for s in songs}, {"chocho", "sakura", "kaeru", "tulip"})
        expected_audio = {s[key] for s in songs for key in ("guideMelody", "accompaniment")}
        self.assertEqual({str(p.relative_to(ASSETS)) for p in (ASSETS / "audio").glob("*.wav")}, expected_audio)
        sources = json.loads((ASSETS.parents[3] / "tools/song_sources.json").read_text())["songs"]
        self.assertEqual({s["id"] for s in sources}, {s["id"] for s in songs})
        for song in songs:
            with self.subTest(song=song["id"]):
                self.assertTrue(40 <= song["bpm"] <= 160)
                self.assertTrue(0 < song["bassMidi"] < 128)
                previous_end = 0
                for note in song["notes"]:
                    self.assertGreaterEqual(note["startMs"], previous_end)
                    self.assertGreater(note["endMs"], note["startMs"])
                    self.assertLessEqual(note["endMs"], song["durationMs"])
                    self.assertTrue(0 < note["midiNote"] < 128)
                    previous_end = note["endMs"]
                self.assertNotIn("phrases", song)
                lengths = []
                for stem in ("guideMelody", "accompaniment"):
                    with wave.open(str(ASSETS / song[stem]), "rb") as audio:
                        self.assertEqual((audio.getnchannels(), audio.getsampwidth(), audio.getframerate()), (1, 2, 48000))
                        lengths.append(audio.getnframes())
                        pcm = array.array("h", audio.readframes(audio.getnframes()))
                        self.assertGreater(max(pcm), 1000)
                        self.assertLess(max(abs(v) for v in pcm), 32767)
                        self.assertTrue(all(v == 0 for v in pcm[:48000]))
                self.assertEqual(lengths[0], lengths[1])
                self.assertEqual(lengths[0], song["durationMs"] * 48)


if __name__ == "__main__":
    unittest.main()
