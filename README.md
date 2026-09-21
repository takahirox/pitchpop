# PitchPop

An offline Android singing-game prototype for [Issue #2](https://github.com/takahirox/pitchpop/issues/2).
Choose ちょうちょう, さくらさくら, かえるの歌 or チューリップ, tap a song to play it in full, and follow the green target bars with the orange trace of your voice. Scores only increase. The Japanese interface offers optional note names, three guide-melody modes, and an **オクターブ違いを許容する** switch (on by default). The switch is saved across app restarts and controls both scoring/adaptive-guide accuracy and the voice trace. Turn it off to compare absolute pitch without octave adjustment.

## Build and run

Requirements: JDK 17, Android SDK platform 35, NDK 26.1.10909125, SDK CMake 3.22.1, a host C compiler (Xcode Command Line Tools on macOS) for native/JVM tests, and an Android device/emulator running Android 8.0 (API 26) or newer with a microphone.

```sh
export ANDROID_HOME=/path/to/Android/sdk
./gradlew assembleDebug testDebugUnitTest lintDebug
python3 tools/test_assets.py
# macOS; use libpitchpop_echo.so on Linux
python3 tools/test_echo.py app/build/echo-host/libpitchpop_echo.dylib
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pitchpop/.MainActivity
```

Alternatively open this directory in Android Studio and run `app`. The first build downloads Gradle and build dependencies. The installed app requires no network connection and declares no Internet permission. Microphone permission is requested at the first play attempt. Audio is processed in memory and never recorded to a file or transmitted.

### Speaker echo suppression

Playback is copied into an in-memory reference buffer and removed from microphone input using bundled SpeexDSP 1.2.1 before pitch detection. Playback starts with **the introduction from an existing published piano score**: two bars for ちょうちょう and かえるの歌 (8 beats), four bars for さくらさくら and チューリップ (16 beats), played at the catalog tempo. The entrance fades in gently; the guide melody stays silent until singing starts. The screen counts down the beats. The filter learns during this intro, so the previous 1.2-second scoring wait is spent before the first sung note. No separate noise/chime probe is played and no microphone recording is saved.

Delay is estimated from the intro. A fixed direct-path gain is selected only for an almost pure delayed-gain match (correlation above .995); otherwise SpeexDSP handles the acoustic path. The adaptive filter is trained on the original microphone/reference pair, so a long intro does not train it on an already-cancelled zero path and then suppress the first sung note. The direct-path case assumes the calibrated path stays stable; restart after moving the device or changing volume. Brief residual pitches must remain stable for seven analysis frames (120 ms onset confirmation, plus 20 ms processing delay). Synthetic tests verify the first sung pitch is accepted within 250 ms of onset in full-song playback; actual device latency still varies.

Music/game output routing is retained. Platform AEC is additionally requested where available. Real-device performance still needs listening checks: clipping, movement or volume changes can alter the acoustic path. Restart the song to calibrate again after changing the setup. The settings screen includes the SpeexDSP license.

### Emulator microphone

Enable host microphone input in the emulator's Extended Controls → Microphone → **Virtual microphone uses host audio input**, or run `adb -s emulator-5554 emu avd hostmicon`. Allow microphone access in both macOS and Android; a one-time Android grant can expire after the app stops or is updated. Check the Mac's selected input device as well. See the [official emulator microphone controls](https://developer.android.com/studio/run/emulator-extended-controls).

The play screen distinguishes near-silent input (`マイクに音が届いていません`), audio without a stable pitch (`マイク入力あり`), and detected pitch (`歌声が聞こえているよ`). A detected pitch can also come from speaker leakage; change your sung pitch and verify that the orange trace follows. Debug builds log input RMS, pitch frequency/confidence, Android capture-silencing status and AEC status once per second under the `PitchPopAudio` tag. No waveform or speech content is logged.

## Implementation

- `Music.kt`: song timeline, YIN detector, strictly positive scoring, adaptive guide, and centralized tuning configurations.
- `AudioEngine.kt`: 48 kHz mono PCM stem mixing with `AudioTrack`, `AudioRecord` capture, software/platform AEC and audio focus. Playback uses `USAGE_GAME` with music content and the system's normal media output route; microphone capture uses `MIC`. The app does not enter communication mode or force a communication speaker route. Volume buttons control media volume. The playback head drives timeline positions; pitch frames use the analysis-window midpoint on that same timeline. A 48 ms analysis window at 16 kHz updates every 20 ms after averaging/downsampling input.
- `SongIntroductions.kt`: published piano intros transcribed from moneko’s ちょうちょう (bars 1–2) and the Mahoroba edition of Yamada’s さくらさくら (bars 1–4). The latter preserves MIDI rolled-chord timing and relative velocities. Both use synthesized sound, with catalog tempo rather than the original score tempo. Sources and usage restrictions are in `app/src/main/assets/licenses/Introductions.txt`, also available in settings. **The ちょうちょう arrangement is currently for personal prototype use; public/commercial distribution permission has not been obtained.**
- `PlaybackArrangement.kt`: prepares the musical intro and maps playback to the original song timeline without shifting note timings.
- `EchoControl.kt`, `SoftwareEchoCanceller.kt`, and `app/src/main/cpp/`: playback-reference synchronization, delay estimation from normal playback, SpeexDSP echo removal, and residual/voice-stability gates. Lost synchronization during singing stops the session with a retry message.
- `SongRepository.kt`: bundled JSON and PCM WAV loading.
- `MainActivity.kt`: song selection, settings, pitch canvas, results, permission handling and lifecycle cleanup. Standard Android Views are used instead of the specification's suggested Compose stack to keep the initial app small and dependency-light.
- `app/src/main/assets/`: two song timelines and four independently mixable, newly synthesized WAV stems.

Returning to the background, navigating away, or losing audio focus stops the session and releases audio resources. A new session starts from the beginning; there is no background recording or resume feature.

## Song authoring

Edit `tools/song_sources.json`, then run:

```sh
python3 tools/generate_audio.py
python3 tools/test_assets.py
```

Tokens contain `MIDI-note:beats`, with `0` representing a rest. The melody strings are successive lines of the full song. The generator writes aligned 48 kHz PCM16 guide and simple alternating-bass accompaniment stems, with a one-second initial lead-in. At runtime, playback replaces this silence with the sourced piano intro before the first sung note. These are provisional manually entered melody versions and simple original synthesized arrangements, not commercial recordings. Melody transcription, musical phrasing and arrangements still need listening review; verify rights for the chosen versions before public distribution, as required by the specification. No lyrics are bundled.

## Validation and remaining device checks

JVM tests cover pitch estimation across 82–880 Hz including harmonics, rejection of silence/noise/DC, score accuracy and silence behavior, tunable tolerances, note/rest boundaries, adaptive fade and recovery. The native/JVM pipeline tests also check zero pitch credit for simulated playback leakage at multiple delays, recognition of overlapping singing (including the guide’s pitch), reference buffering (including the final partial playback frame) and gating. `tools/test_echo.py` measures suppression and sustained-voice preservation using the production C implementation. Synthetic inputs cannot establish actual device performance. The Python asset check covers all four songs, WAV format, duration alignment, lead-in silence and unclipped nonempty stems.

For a disposable emulator with the APK already installed, run `python3 tools/smoke_test.py emulator-5554`. It changes PitchPop's microphone permission/settings and exercises permission cancellation, full playback of each song, all guide-mode selections, stop/restart and background cleanup. It expects silent emulator microphone input and verifies a zero score; it does not validate acoustic echo cancellation or audible guide quality.

Before treating Issue #2 as accepted, test on physical devices:

1. In airplane mode, play all four songs; verify the arrangements and visible target notes by ear.
2. Sing with speaker playback at several volumes. Check responsiveness, octave errors and timing, then remain silent to check whether speaker leakage earns points. Software echo cancellation uses the actual playback reference; platform AEC is also requested where supported. Their combined effectiveness with normal media playback remains device-dependent. Verify whether unexpected volume changes have improved and whether leakage worsens. These acoustic behaviors cannot be validated by JVM tests or a silent emulator.
3. Exercise guide on/off/adaptive modes and note labels, including persistence after relaunch.
4. Deny/revoke microphone permission, interrupt playback with another audio app, background/rotate the app, and repeatedly stop/restart sessions.
5. Check smaller displays and both low/high vocal ranges. Scoring and the voice trace accept octave-equivalent notes: singing one octave below the target places the trace at the target's octave while preserving cents errors. The guide audio stays in its authored key, and raw detected pitch remains available in debug diagnostics. This accepts octave differences without diagnosing whether they came from the singer or a detector octave error. During rests, the trace uses the previous target as its octave reference. Automatic key selection is absent.

48 kHz full-duplex audio is required by this initial engine. Unsupported or unavailable audio devices produce a recoverable error. Scores, voicing thresholds and guide timing are provisional; `ScoringConfig`, `PitchConfig` and `GuideConfig` are the tuning entry points. Input downsampling uses a simple three-sample average; a stronger anti-alias filter and device latency calibration may be needed after listening tests.

API references: [AudioTrack playback position](https://developer.android.com/reference/android/media/AudioTrack), [AcousticEchoCanceler](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler).

The other three provisional melodies have been removed pending transcription review. Their editable sources are kept in `tools/archived_song_sources.json`, outside the app and active generator input. All four active songs have sourced piano intros in `SongIntroductions.kt`; see the attribution and distribution limitation above.


### Personal-use song additions

かえるの歌 (96 BPM) uses the standard eight-bar melody checked against こどもMusiQ♪, including the four separated calls and paired eighth notes. Its eight-beat intro uses the closing two bars from page 2 of こどもMusiQ♪'s accompaniment score (alternative left hand), following the cited childcare-piano guidance to use this closing figure as the lead-in. Melody register matches the singing entry; the final right-hand beat is a rest. The main backing remains the prototype's simple bass pattern. チューリップ (90 BPM) follows Yoshida Naomi's published C-major score, including the rests and the left-hand accompaniment. The arranger explicitly marks bars 9–12 as the intro; these supply the sixteen-beat lead-in. Both use synthesized tones. Attribution is bundled in settings. This is the user's private practice app; no public-distribution clearance is claimed.
