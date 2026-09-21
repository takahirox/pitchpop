# Initial prototype implementation status

Validated on 2026-09-21. The implementation is in the working tree; this report does not close GitHub Issue #2.

## Implemented

- Standalone Android app with no network permission or runtime network dependency.
- Two bundled provisional songs (ちょうちょう and さくらさくら), full-song play and authored phrase selection.
- Independently mixed accompaniment/guide PCM stems and on/off/adaptive guide settings.
- Microphone permission flow, simultaneous capture/playback, speaker routing and best-effort platform AEC.
- YIN pitch detection, scrolling target bars, voice trace and optional note names.
- Positive configurable scoring and configurable adaptive guide behavior.
- Audio-focus handling, stop/restart and background cleanup.

## Automated evidence

- `assembleDebug`: succeeded; APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest`: 6 tests passed, none skipped.
- `python3 tools/test_assets.py`: passed for all five songs and ten stems.
- `lintDebug`: passed with no errors and six warnings (SDK/Gradle versions, backup configuration, programmatic view constructor, and string localization).
- APK permission inspection: only `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`.
- Android API 36 emulator: microphone explanation/cancel, all five first phrases completing with zero score on silent input, a complete song with guide off, and stop/restart into a later phrase with guide on passed.

The first emulator run stopped responding to both UIAutomator and ordinary ADB shell commands after the five phrase tests. Remaining tests were resumed using a fresh disposable emulator with host graphics and Vulkan disabled. Both runs used no host audio; they do not establish acoustic performance.

The background test exposed a quick home/return transition that could bypass the original `onStop` cleanup. Cleanup now starts in `onPause`; the revised full-song background/return test passed, so phrase completion cannot mask the result.

## Normal media playback update

Playback now uses `USAGE_GAME` / `CONTENT_TYPE_MUSIC` and capture uses `MIC`. The app no longer enters communication mode or selects a communication device; it follows normal media output routing. Hardware volume buttons control media volume. Platform AEC is still requested where supported. Whether this resolves the reported volume drops, and whether speaker leakage changes, require device listening tests.

## Octave setting update

The settings screen now offers `オクターブ違いを許容する`, enabled by default and saved across restarts. Switching it off restores absolute-pitch scoring and visualization. Adaptive-guide accuracy uses the same selected scoring behavior. The build, lint and all 8 JVM tests pass, including strict-mode octave rejection and unchanged cents-error scoring. No device was connected for installing this update.

## Acceptance still requiring physical-device playtesting

Subsequent Pixel 7a feedback confirmed microphone recognition, but the voice trace stayed below the targets. Without requiring user-collected logs, scoring and visualization now accept octave-equivalent pitches while retaining cents errors. The root cause (vocal register versus detector octave error) has not been established. The updated build passes all 7 JVM tests plus lint; guide audio is not transposed.

- Audible guide on/off and adaptive transitions; arrangement and melody transcription review.
- Real singing accuracy, latency, target alignment and behavior across vocal ranges.
- Speaker leakage rejection with and without platform AEC at representative playback volumes.
- Headphone-free playability on representative devices, especially those without effective AEC.

The UI uses Kotlin with standard Android Views/Canvas instead of the suggested Compose stack. Song transcriptions, bass accompaniment and tuning values are provisional, as permitted by Issue #2. See the README for reproducible build/test commands and the device checklist.

## Playback leakage update

Added vendored SpeexDSP 1.2.1 with JNI, exact mixed-playback reference buffering, startup delay estimation/filter training, and residual/stable-voice gates before pitch detection, scoring and adaptive-guide feedback. A quiet 600 ms calibration probe fits inside the existing one-second lead-in; the UI asks the player to wait silently. Normal music/game playback remains enabled. The third-party license is bundled and available in settings.

Validation for this update:

- Android debug build and lint passed; all 17 JVM tests passed, including the production JNI echo-to-scoring pipeline.
- Simulated playback alone at 0, 60 and approximately 150 ms delay produced no accepted pitch frames and zero score. Overlapping singing was detected, including singing the same note as the guide with vibrato.
- Three Python native acoustic tests passed. Simulated guide on/off/adaptive playback was reduced by 26.7–41.5 dB; overlapping and unaccompanied sustained voices survived. These are synthetic measurements, not physical-device results.
- API 36 disposable emulator: full-song completion with zero score on silent input, guide-on stop/restart into another phrase, and background cleanup passed. This caught and fixed reference publication timing and final partial-frame handling. No host audio was enabled, so this does not measure acoustic rejection.
- Native builds include arm64-v8a, armeabi-v7a and x86_64, with 16 KB-compatible ELF alignment.

This update supersedes the original platform-AEC-only scope following the user's request. Physical speaker/microphone behavior, clipping, route latency and changing acoustic conditions remain to be verified. No physical device was connected during this update.

## Startup noise refinement

Replaced the random-noise calibration burst with a 600 ms C-major chord with smooth 40 ms attack and 80 ms release. Calibration still makes a short musical sound; this is not silent calibration. Extended onset confirmation from four to five frames (20 ms extra) to reject short residual pitches with the new excitation. No changes to output routing or song timing.

All 17 JVM tests and three native acoustic tests passed. Simulated echo reduction was 21.5–34.0 dB, less than with the broadband probe; the full pitch/scoring pipeline still accepted zero echo-only frames for the tested delays and preserved singing, including the guide's pitch. Build and lint passed. Real-device sound preference and acoustic effectiveness still require listening confirmation.

## Removed independent calibration sounds

Supersedes both startup-probe variants above. No noise or chord is generated; the original silent lead-in is restored for full songs and phrases. The first 800 ms of audible song playback supplies delay estimation; SpeexDSP continues adapting to the actual mix. Users may sing immediately. The first 1.2 seconds of audible playback are excluded from pitch credit while the filter learns, and stable onset confirmation is now six frames (100 ms extra).

All 17 JVM tests and three native acoustic tests passed, plus debug build/lint. Pipeline tests use unmodified song stems with silent lead-in and require zero echo-only credit at 0/60/~150 ms delays, including guide off and adaptive fading. Singing begins with the song in double-talk tests and remains detectable, including matching the guide pitch.

Without a broadband probe, simulated acoustic reduction is lower (17.2–33.9 dB). The acoustic threshold was explicitly revised from 18 to 15 dB to reflect passive learning; the independent end-to-end requirement of zero echo-only pitch credit remains unchanged. Actual device acoustics and hearing comfort still require user verification. No confirmation sound remains in the playback path.

## Musical introduction before singing

Supersedes the silent-start behavior above. Full-song play and every phrase now start with four beats of soft instrumental chords using catalog tempo and bass/key information. An 80 ms entrance fade and shaped note envelopes avoid abrupt starts. The guide melody remains silent during the intro regardless of guide setting; a beat countdown shows when to sing. Original note/phrase timestamps are preserved by mapping the prelude before the selected start, including negative timeline positions for full songs.

The existing learning interval now occurs before singing rather than excluding the first sung notes. Highly correlated intro input also initializes a conservative direct-path subtraction; SpeexDSP retains responsibility for reflections and changing paths. Stable-voice confirmation is seven frames (120 ms).

Build/lint and all 18 JVM tests passed. New production-arrangement/JNI pipeline coverage checks full-song and later-phrase entry, guide-free intro, first sung pitch accepted within 250 ms for simulated 0/60/~150 ms delays, and zero echo-only score in those cases. Existing reflected-echo, overlapping voice and guide-mode tests still pass. Asset validation and three native acoustic tests passed. These are synthetic checks; they do not establish physical-device latency or acoustic accuracy.

An exploratory perfectly stationary voice matching the guide was sometimes attenuated after its initial onset; natural pitch variation is covered, but sustained matching-pitch robustness remains a real-device limitation to investigate.

The Android smoke check exposed a short processing backlog being treated as a stream discontinuity. Capture now keeps a one-second buffer (still read in 20 ms chunks), and the reference clock tolerates a one-second backlog within its two-second history. This preserves sample continuity during intro analysis without adding a one-second read delay. The clock regression test covers this backlog.

Final API 36 emulator smoke checks passed: complete song with guide off and zero silent-input score, guide-on stop/restart into a later phrase, and background cleanup. Updated APK installed successfully on the USB-connected Pixel 7a and launched; physical acoustic/listening verification is left to the user.

## Reduced song selection

Removed むすんでひらいて, かごめかごめ and あんたがたどこさ at the user’s request because the transcriptions sound incorrect. Removed their catalog entries and six WAV assets, and removed them from the active generator input. Editable source entries are archived outside the app for possible corrected restoration. Catalog/asset checks and the emulator song list now expect only ちょうちょう and さくらさくら. Earlier five-song evidence above describes the previous build.

The existing intro is a generic chord pattern derived from key/tempo rather than a song-specific arrangement; the user has correctly identified this limitation. This catalog-removal update does not claim to resolve that musical arrangement issue.

Catalog-removal validation: asset checks and debug build passed; APK inspection confirmed exactly two catalog entries and four WAV assets. Installed successfully on the connected Pixel 7a.

## Individually authored introductions

Replaced the inferred repeating triad with two explicit instrumental scores in SongIntroductions.kt. ちょうちょう has light broken chords moving from C to G7; さくらさくら takes E–F–B–A–F–E from the bundled melody’s closing figure, over sparse E/B support with a plucked-string envelope. The intros differ in notes, rhythm, layering, decay and harmonic balance. Both retain four beats, a gentle entrance, beat countdown and pre-singing echo learning. Removed songs remain absent. These are original new arrangements based on the bundled versions; musical preference still requires listening review.

Build/lint and all 19 JVM tests passed. The intro-to-pitch pipeline now tests BOTH songs, full-song and later-phrase starts, and 0/60/~150 ms simulated acoustic delays. First sung pitch is accepted within 250 ms, with zero echo-only credit in these cases. New score/render checks cover four-beat bounds, distinct arrangements, the sakura pitch collection, unclipped output, smooth edges and enough audible material for learning. No native echo thresholds or scoring delays were changed for this update.

The individually authored-intro APK was successfully installed on the connected Pixel 7a and launched. Physical listening preference and acoustic response remain to be confirmed by the user.


## Published piano introductions (2026-09-21)

Supersedes the invented introductions above at the user's request. Inspected the published piano PDFs rather than inferring a new arrangement:

- ちょうちょう: moneko / まいにち、おんがく。 bars 1–2 before the lyrics begin; 8 beats, 20 notes across both hands, C major. Original tempo 80 is adapted to catalog tempo 112 (4.286 s).
- さくらさくら: Yamada Kosaku arrangement, Art Studio Mahoroba / 簡単移調楽譜屋 edition; all piano voices in bars 1–4 while the vocal staff rests. The source's transposition facility was used at -5 semitones. The first 16 beats of its MIDI supply 74 notes including rolled-chord offsets, durations and relative velocities. Original tempo 63 is adapted to catalog tempo 100 (9.6 s).

Both use synthesized piano-like tones, not recorded piano performances. Source URLs, attribution, adaptations and usage terms are bundled in `assets/licenses/Introductions.txt` and accessible from settings. Original PDFs and MIDI are not bundled. **The moneko arrangement is for the user's personal prototype use; permission for public/commercial distribution has NOT been obtained.** Mahoroba permits derived works with source attribution. No publication or permission-request message was sent.

Intro duration is now derived from each score's bar count in both the engine and playback arrangement; the countdown no longer clamps to four beats. Song/phrase note timestamps remain aligned to the selected singing start.

The longer intro exposed an existing echo-processing issue: the adaptive filter was trained on input from which the direct echo path had already been subtracted, then attenuated the singer on entry. It now trains on the actual microphone/reference pair. For the previously selected almost-pure direct-path case (>.995 calibration correlation), the delayed direct residual is used for output; other paths use SpeexDSP. No scoring threshold or required test latency was relaxed. The fixed direct-path case still requires stable acoustics; movement and volume changes require real-device review/recalibration.

Validation: debug build and lint passed; all 19 JVM tests passed, including both new intros, full-song and later-phrase entry, first sung pitch within 250 ms at simulated 0/60/~150 ms delays, and zero echo-only credit. Score/render tests verify 8/16-beat lengths, source note counts, rolled-chord timing, soft edges and audible calibration material. Asset checks and all three native acoustic tests passed (17.2–33.9 dB simulated suppression). Physical listening and acoustic behavior still require user confirmation.

Final APK (including source credits) installed successfully on the USB-connected Pixel 7a.


## Two personal-use song additions (2026-09-21)

Added かえるの歌 (29 melody notes, four phrases, 96 BPM) and チューリップ (38 melody notes, six phrases, 90 BPM), bringing the active catalog to four songs/eight WAV stems. The three previously removed songs remain archived and absent. The user explicitly intends private personal use, with no store/public distribution.

Checked the frog melody against こどもMusiQ♪'s eight-bar score, retaining the quarter-note rests between calls and the repeated eighth-note pairs. Its intro uses the first two bars of Calm Piano's beginner score/MIDI (seven notes, including the final upper pickup); only the intro comes from that arrangement, with tempo adapted from 63 to 96. The main backing stays the prototype's simple bass pattern.

Tulip follows Yoshida Naomi / Harmony Music School's published C-major score: bars 1–12 for the melody and left-hand backing, with bars 9–12 explicitly marked by the arranger as the prelude. Preserved the quarter-note rests, final dotted half note and 90 BPM tempo. Added optional explicit accompaniment notes to the generator; existing songs retain their previous generation path. Source URLs and personal-use context are bundled in the credits. No lyrics, source PDFs/MIDI/images or recordings are included.

Extended the existing introduction/JNI pipeline coverage to all four songs. The frog intro exposed periodic delay ambiguity for a low sustained chord at a simulated 150 ms delay; latency correlation and direct-gain estimation now include the attack instead of discarding the first 250 ms. This resolves the regression without changing score/echo gate thresholds, adding calibration sounds or changing the sourced notes.

Validation: debug APK build and lint, all 19 JVM tests (now covering all four intros), asset validation and all three native acoustic tests passed. APK inspection confirmed four catalog entries, eight WAVs and source credits. The new melodies and their full/phrase entry pass the same first-note-within-250-ms and zero-echo-only-credit synthetic checks. No physical device was connected; updated APK is ready but has not been installed on the user's device. The updated emulator smoke script includes both added songs but was not run this turn.

## Removed phrase selection (2026-09-21)

At the user's request, choosing a song now immediately starts its introduction and full-song playback (after microphone permission if needed). Removed the duration/phrase selection screen and the result screen's length-selection action. Stop, system Back during playback and backgrounding return to the song list; replay starts the same full song from its intro.

Removed Phrase and all partial-playback parameters from the runtime model, repository, engine, arrangement and pitch view. The catalog no longer ships phrase ranges. Authoring melody lines remain as editable notation under `melody`; the generator emits only whole-song timelines. Existing WAVs and note timings are unchanged. Updated the asset checks, remaining full-song pipeline tests, smoke script and README to match.

Debug build, lint, all 19 JVM tests and asset checks passed. Independently reconstructed all four timelines from authoring data and confirmed unchanged note timestamps/durations. APK inspection confirms four songs without phrase metadata.

API 36 emulator check: microphone-permission cancellation and direct song-to-playback navigation passed. Two attempts at complete かえるの歌 playback stopped with the existing audio synchronization error, so completion on this emulator is not verified. No audio synchronization thresholds or DSP behavior were changed in this UI-removal update. This remains an emulator validation limitation, not a passing end-to-end playback result.

Targeted navigation checks additionally passed: replay from the result screen, stop back to the song list, system Back during playback, and background cleanup returning to the song list. The disposable emulator was shut down after validation. No physical device was connected; the APK is built but not installed on the user's device.

## Corrected frog introduction selection (2026-09-21)

The user reported that the frog intro sounded wrong. The previous Calm Piano transcription used sparse low-register sustained notes and rests, ending on C5 before the game's C4 singing entry. Its healing-arrangement style was an unsuitable choice for this singing lead-in, even though it followed that source's note events.

Replaced it with the closing two bars of the published こどもMusiQ♪ accompaniment score, page 2 bars 7–8, using its alternative left-hand staff. The cited childcare piano article explicitly identifies this closing figure as a frog-song introduction. Preserved the source's final right-hand quarter rest. The intro remains eight beats at 96 BPM, now in the same melody register as the game. Attribution and README updated; no original source PDF is bundled.

Build, lint and all 19 JVM tests passed, including the revised note/register/rest regression check and full-song echo/first-note tests for all four songs. No DSP, scoring thresholds, main song notes, backing stems or other introductions changed. Actual listening preference still needs the user's confirmation.

The corrected-intro APK installed successfully on the USB-connected Pixel 7a.
