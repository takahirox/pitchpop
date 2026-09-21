#!/usr/bin/env python3
"""Exercise the installed debug app on a disposable emulator using platform UIAutomator.

Usage: python3 tools/smoke_test.py emulator-5554
This stops/relaunches PitchPop and changes only its microphone permission/settings.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
assert re.fullmatch(r"emulator-\d+", SERIAL), "Use a disposable emulator, not a physical device"


def adb(*args):
    return subprocess.check_output(["adb", "-s", SERIAL, *args], text=True, timeout=20)


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/pitchpop-window.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/pitchpop-window.xml")).iter("node"))


def tap(text, prefix=False):
    nodes = screen()
    for node in nodes:
        value = node.get("text", "")
        if value == text or (prefix and value.startswith(text)):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
    raise AssertionError(f"Missing {text!r}: {[n.get('text') for n in nodes if n.get('text')]}")


def expect(text, seconds=12):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        texts = [n.get("text") for n in screen()]
        if text in texts:
            return
        if "音声を確認してね" in texts:
            raise AssertionError(f"Audio failed: {texts}")
        time.sleep(0.2)
    raise AssertionError(f"Did not see {text!r}: {texts}")


def main():
    adb("shell", "am", "force-stop", "com.pitchpop")
    adb("shell", "pm", "revoke", "com.pitchpop", "android.permission.RECORD_AUDIO")
    adb("shell", "am", "start", "-n", "com.pitchpop/.MainActivity")
    tap("♪  ちょうちょう")
    expect("歌声を見えるようにしよう")
    tap("もどる")
    print("PASS: microphone explanation and cancel", flush=True)
    # Grant only this app's permission, keeping the automated test independent of OS dialog locale.
    adb("shell", "pm", "grant", "com.pitchpop", "android.permission.RECORD_AUDIO")
    tap("設定")
    tap("音名を表示する")
    tap("上手に歌えたら小さく")
    tap("もどる")
    for name in ("ちょうちょう", "さくらさくら", "かえるの歌", "チューリップ"):
        tap("♪  " + name)
        expect("歌ってくれてありがとう！", 55)
        expect("0 点")
        tap("曲をえらぶ")
        print(f"PASS: {name} song completed with silent-input score 0", flush=True)
    remaining_checks()


def remaining_checks():
    tap("設定"); tap("鳴らさない"); tap("もどる")
    tap("♪  ちょうちょう")
    expect("歌ってくれてありがとう！", 48)
    expect("0 点")
    tap("曲をえらぶ")
    print("PASS: full song with guide off", flush=True)
    tap("設定"); tap("いつも鳴らす"); tap("もどる")
    tap("♪  さくらさくら")
    tap("やめる")
    expect("好きなうたをえらんでね")
    tap("♪  さくらさくら")
    expect("歌ってくれてありがとう！", 55)
    print("PASS: stop/restart with guide on", flush=True)
    background_check()


def background_check(from_result=True):
    if from_result:
        tap("もう一度歌う")
    else:
        adb("shell", "am", "force-stop", "com.pitchpop")
        adb("shell", "pm", "grant", "com.pitchpop", "android.permission.RECORD_AUDIO")
        adb("shell", "am", "start", "-n", "com.pitchpop/.MainActivity")
        tap("♪  さくらさくら")
    time.sleep(2)  # Let capture/playback start before testing lifecycle cleanup.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.pitchpop/.MainActivity")
    expect("好きなうたをえらんでね")
    print("PASS: background stops playback", flush=True)


if __name__ == "__main__":
    if "--background" in sys.argv:
        background_check(from_result=False)
    elif "--remaining" in sys.argv:
        adb("shell", "am", "force-stop", "com.pitchpop")
        adb("shell", "pm", "grant", "com.pitchpop", "android.permission.RECORD_AUDIO")
        adb("shell", "am", "start", "-n", "com.pitchpop/.MainActivity")
        remaining_checks()
    else:
        main()
