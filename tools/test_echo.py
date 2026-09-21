#!/usr/bin/env python3
"""Exercise the production native echo processor with deterministic synthetic acoustics.

Build: cmake -S app/src/main/cpp -B /tmp/pitchpop-echo -DCMAKE_BUILD_TYPE=Release
       cmake --build /tmp/pitchpop-echo
Run: python3 tools/test_echo.py /tmp/pitchpop-echo/libpitchpop_echo.dylib
No microphone recording or third-party Python packages are used.
"""
import array
import ctypes
import math
from pathlib import Path
import random
import sys
import unittest
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE, FRAME = 16000, 320
LIB = ctypes.CDLL(sys.argv.pop(1))
PCM = ctypes.c_int16 * FRAME
LIB.pp_echo_create.restype = ctypes.c_void_p
LIB.pp_echo_process.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_int16), ctypes.POINTER(ctypes.c_int16), ctypes.POINTER(ctypes.c_int16)]
LIB.pp_echo_destroy.argtypes = [ctypes.c_void_p]


def rms(samples):
    return math.sqrt(sum(float(x) ** 2 for x in samples) / len(samples)) / 32768


def clamp(value):
    return max(-32768, min(32767, round(value)))


def source(mode="on"):
    stems = []
    for stem in ("accompaniment", "guide"):
        with wave.open(str(ROOT / f"app/src/main/assets/audio/chocho_{stem}.wav")) as wav:
            data = array.array("h", wav.readframes(wav.getnframes()))
        if sys.byteorder != "little": data.byteswap()
        stems.append(data)
    result = []
    for index in range(0, 18 * 48000, 3):
        gain = 0 if mode == "off" else (0.1 if mode == "adaptive" and index > 8 * 48000 else 1)
        # Match the app: mix at 48 kHz before reducing to 16 kHz.
        result.append(int(sum(clamp(0.65 * (stems[0][index+j] + gain * stems[1][index+j])) for j in range(3)) / 3))
    return result


def process(reference, microphone):
    handle = LIB.pp_echo_create()
    assert handle
    result = []
    try:
        for index in range(0, len(reference), FRAME):
            out = PCM()
            LIB.pp_echo_process(handle, PCM(*microphone[index:index+FRAME]), PCM(*reference[index:index+FRAME]), out)
            result.extend(out)
    finally:
        LIB.pp_echo_destroy(handle)
    return result


class EchoTest(unittest.TestCase):
    def test_delayed_reflected_playback_is_suppressed(self):
        for mode, delay in (("on", 0), ("on", 960), ("on", 2400), ("off", 640), ("adaptive", 960)):
            with self.subTest(mode=mode, delay=delay):
                reference = source(mode)
                rng = random.Random(13)
                def at(i): return reference[i] if i >= 0 else 0
                microphone = [clamp(0.55 * at(i-delay) + 0.18 * at(i-delay-420) + rng.gauss(0, 8)) for i in range(len(reference))]
                cleaned = process(reference, microphone)
                # Allow the adaptive filter to learn before measuring across note changes.
                start = 4 * RATE
                reduction = 20 * math.log10(rms(microphone[start:]) / max(1e-9, rms(cleaned[start:])))
                audible_frames = sum(rms(cleaned[i:i+FRAME]) >= 0.008 for i in range(start, len(cleaned)-FRAME, FRAME))
                print(f"{mode}, delay={delay/RATE*1000:.0f}ms: reduction={reduction:.1f}dB, above pitch floor={audible_frames} frames", flush=True)
                # With no broadband probe, require at least 15 dB acoustic reduction.
                # The end-to-end tests independently require ZERO echo-only pitch credit.
                self.assertGreater(reduction, 15)
                # Residual transient frames are expected; EchoPipelineTest checks the actual
                # gate + detector + scorer and requires zero echo-only credit.

    def test_near_end_voice_survives_with_playback(self):
        reference = source()
        voice_start = 6 * RATE
        voice = [0 if i < voice_start else 32767 * (0.16 * math.sin(2*math.pi*220*i/RATE)
                 + 0.09 * math.sin(2*math.pi*440*i/RATE + 0.4)
                 + 0.04 * math.sin(2*math.pi*660*i/RATE + 0.9)) for i in range(len(reference))]
        microphone = [clamp((0.55 * reference[i-960] if i >= 960 else 0) + voice[i]) for i in range(len(reference))]
        cleaned = process(reference, microphone)
        segment = cleaned[8*RATE:]
        projection = 2 * abs(sum(x * complex(math.cos(2*math.pi*220*i/RATE), math.sin(2*math.pi*220*i/RATE)) for i,x in enumerate(segment))) / len(segment) / 32768
        print(f"double talk: residual RMS={rms(segment):.4f}, 220Hz amplitude={projection:.4f}", flush=True)
        self.assertGreater(rms(segment), 0.03)
        self.assertGreater(projection, 0.025)

    def test_voice_without_playback_survives_and_silence_stays_silent(self):
        reference = [0] * (RATE * 3)
        voice = [clamp(4000 * math.sin(2*math.pi*190*i/RATE)) for i in range(len(reference))]
        self.assertGreater(rms(process(reference, voice)[RATE:]), 0.03)
        self.assertEqual(rms(process(reference, reference)), 0)


if __name__ == "__main__":
    unittest.main()
