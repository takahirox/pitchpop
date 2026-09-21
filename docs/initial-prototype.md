# PitchPop Initial Prototype Specification

Status: Draft for implementation  
Related: #1 — Vision and Goals

## 1. Purpose

The first PitchPop version should validate the core product loop with the smallest complete standalone Android app:

1. Choose a familiar song.
2. Choose either full-song play or short phrase practice.
3. Sing while a karaoke-style pitch guide is shown.
4. See the detected voice pitch in real time.
5. Earn points continuously based on how closely the sung pitch matches the target.
6. Optionally use an audible guide melody, including an adaptive mode that fades the guide as the player becomes more accurate.

This version is a **playable prototype**, not the final product. Visual effects, content breadth, scoring balance, and advanced training logic can be refined after real use.

---

## 2. Prototype goals

The prototype should prove that:

- real-time vocal pitch detection is usable on-device;
- the pitch visualization is understandable while singing;
- positive, accuracy-weighted scoring feels rewarding without punishing mistakes;
- full-song and short-phrase play both work;
- the guide melody can be independently controlled;
- speaker playback and microphone capture are usable without requiring headphones;
- the architecture makes songs, scoring parameters, and guide behavior easy to tune later.

---

## 3. Initial song set

Use approximately five familiar songs for the first version:

- Chōchō (ちょうちょう)
- Sakura Sakura (さくらさくら)
- Musunde Hiraite (むすんでひらいて)
- Kagome Kagome (かごめかごめ)
- Antagata Doko Sa (あんたがたどこさ)

All accompaniment and guide-melody audio used by the app should be newly produced specifically for PitchPop rather than copied from commercial recordings or online sources.

Before any public release, the melody and lyrics rights for each included version must be verified again. For the prototype, these five are the working content set.

---

## 4. Play modes

### 4.1 Full Song

The player sings through the complete song from beginning to end.

Mistakes never end the session. The song continues until completion or until the user explicitly exits.

### 4.2 One Phrase

The player practices one short, predefined phrase from a song.

Phrase boundaries are authored as song data. Automatic phrase detection is not required for the first version.

The same pitch visualization, scoring, and guide-melody behavior are used in both play modes.

---

## 5. Guide melody modes

The app supports three guide-melody modes.

### A. Guide always on

The target melody is audible for the entire phrase or song.

This is intended to be the easiest mode.

### B. Guide always off

Only the accompaniment is audible.

The target pitch remains visible on screen.

### C. Adaptive guide

The guide melody starts audible.

When recent pitch accuracy remains good, the guide-melody volume gradually fades down. When accuracy becomes poor again, the guide may gradually return.

The exact thresholds, time windows, fade speed, minimum volume, and recovery behavior must be configurable and easy to tune. They are not considered final product constants.

---

## 6. Play screen

The play screen is the core interface.

It should contain:

- a karaoke-style target pitch timeline;
- target notes represented as horizontal bars positioned by pitch and time;
- a real-time visualization of the player's detected pitch;
- a clearly visible numeric score;
- optional note-name labels on the target bars;
- enough song/progress context to understand where the player is in the phrase or song.

The target timeline should scroll through time while the current singing position remains visually easy to track.

The first prototype should remain visually simple. Character animation, elaborate effects, achievements, and other entertainment layers are intentionally deferred.

### Note names

Note names are optional.

When enabled, target bars display note names such as C / D / E or the equivalent chosen notation.

When disabled, the musical interaction remains fully playable without requiring music-theory knowledge.

---

## 7. Scoring behavior

The scoring system is strictly positive.

### Principles

- The score never decreases.
- Matching the target more closely earns points faster.
- A small pitch error still earns points, but fewer.
- Larger errors earn little or no score.
- Silence does not earn pitch-match points.
- Missing a note never interrupts play.

### Calculation

For each valid voiced pitch frame while a target note is active:

1. Convert the detected frequency to a musical pitch value.
2. Measure the distance from the target pitch, preferably in cents.
3. Convert that distance to a score increment using an accuracy curve.
4. Add the increment to the current score.

The scoring curve should be smooth or use configurable bands. The exact tolerance values are deliberately **not fixed in the specification**.

All scoring thresholds and weights must be stored as tunable parameters rather than scattered hard-coded constants.

---

## 8. Settings

The first version only requires two user-facing settings:

### Note-name display

- On
- Off

### Guide melody mode

- Always on
- Always off
- Adaptive fade

Additional settings should not be added unless they are necessary to make the prototype function.

---

## 9. Audio assets

Each song should have at least two independently controllable audio components:

1. **Accompaniment**
2. **Guide melody**

Recommended prototype format:

- WAV / PCM assets
- consistent sample rate across all songs, preferably 48 kHz
- accompaniment and guide aligned to the same exact timeline

The guide melody must be independently mixable so that its volume can change during adaptive mode.

A practical approach for the prototype is to load/decode the two stems and mix them into a single playback stream inside the app.

---

## 10. Microphone input and speaker playback

PitchPop is designed to work **without headphones**.

The app must therefore support simultaneous:

- speaker playback of accompaniment / guide;
- microphone capture of the singer.

The playback audio will leak into the microphone, so the first implementation should use Android's platform acoustic echo cancellation when available.

### Initial approach

- Capture microphone audio with Android `AudioRecord`.
- Use `AcousticEchoCanceler` when supported by the device.
- Treat acoustic echo cancellation as best-effort because behavior differs by device.
- Do not implement a custom full software echo-cancellation system in the first prototype.

The implementation should keep audio capture and pitch detection sufficiently modular that more advanced echo suppression can be added later if device testing shows it is necessary.

A key prototype test is whether the app can distinguish the singer from its own speaker output well enough for useful pitch scoring.

---

## 11. Pitch detection

Pitch detection runs entirely on the Android device.

### Recommended approach

Use a monophonic fundamental-frequency detector such as **YIN** or an equivalent algorithm suitable for singing voice.

Suggested implementation targets:

- mono microphone input;
- approximately 48 kHz input where practical;
- analysis window approximately 20–50 ms or whatever is required for stable low-pitch detection;
- UI pitch updates roughly every 10–30 ms;
- confidence / voicing detection so silence and unreliable frames can be ignored;
- perceived visual response ideally below about 100 ms.

These are tuning targets, not strict audio API requirements.

The pitch detector should expose a small implementation-independent interface so the algorithm can be changed later without rewriting scoring or UI code.

Example logical output:

```
PitchFrame {
    timestampMs
    frequencyHz
    midiPitchFloat
    confidence
    voiced
}
```

---

## 12. Song data model

Song timing and target notes should be data-driven rather than embedded in UI code.

A song definition should contain enough information to synchronize audio, pitch bars, scoring, and phrase practice.

Example:

```json
{
  "id": "song-id",
  "title": "Song title",
  "accompaniment": "audio/song_accompaniment.wav",
  "guideMelody": "audio/song_guide.wav",
  "notes": [
    {
      "startMs": 1200,
      "endMs": 1800,
      "midiNote": 64
    }
  ],
  "phrases": [
    {
      "id": "phrase-1",
      "startMs": 1200,
      "endMs": 5200
    }
  ]
}
```

The exact storage format may change, but the important requirement is that adding or editing a song should mostly be a content/data task rather than an application-code change.

---

## 13. Playback synchronization

Audio playback time should be the source of truth for:

- target-note position;
- pitch-bar scrolling;
- phrase start/end;
- scoring target selection.

The UI should not maintain an independent approximate clock that can drift away from audio.

Accompaniment and guide melody must share the same timeline.

---

## 14. Suggested Android stack

For the first implementation:

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Microphone capture:** Android `AudioRecord`
- **Playback / mixing:** Android `AudioTrack` or an equivalent low-level playback layer that allows synchronized guide-volume control
- **Echo cancellation:** Android `AcousticEchoCanceler` where available
- **Pitch detection:** YIN or equivalent monophonic pitch detector behind an internal interface
- **Song definitions:** bundled JSON or equivalent structured assets
- **Audio assets:** bundled app resources/assets
- **Network:** not required

The prototype should function fully offline.

---

## 15. Minimal screen flow

The first version needs only the following functional flow:

1. **Song selection**
   - choose one of the initial songs;
2. **Play-mode selection**
   - Full Song;
   - One Phrase;
3. **Play screen**
   - target pitch bars;
   - detected pitch;
   - live score;
4. **Settings**
   - note-name display;
   - guide melody mode.

The exact navigation layout and visual styling can remain simple in the first version.

---

## 16. Architecture boundaries

The implementation should keep these responsibilities separated:

### Audio Playback Engine

- accompaniment + guide mixing;
- playback position;
- guide melody volume.

### Microphone / Pitch Detector

- microphone capture;
- pitch estimation;
- voicing/confidence output.

### Song Timeline

- target notes;
- phrase ranges;
- current target note for a playback timestamp.

### Scoring Engine

- pitch error calculation;
- configurable accuracy-to-points mapping;
- cumulative score.

### Adaptive Guide Controller

- observes recent accuracy;
- controls guide-melody volume.

### UI

- song/mode selection;
- pitch visualization;
- score display;
- settings.

This separation is important because audio and scoring behavior will almost certainly be tuned after device testing.

---

## 17. Prototype acceptance criteria

The first prototype is complete when:

- the app runs as a standalone Android application;
- it can work fully offline;
- the initial song set is available as selectable content;
- every song supports Full Song and One Phrase play;
- all three guide-melody modes function;
- microphone input is captured during speaker playback;
- the singer's detected pitch is visualized in real time;
- target pitch bars stay synchronized with playback;
- the score only increases and increases faster for more accurate pitch;
- scoring parameters can be tuned without restructuring the scoring engine;
- note-name display can be switched on or off;
- platform acoustic echo cancellation is used when supported;
- the app remains playable without headphones.

---

## 18. Explicit non-goals for the first version

Do not block the first prototype on:

- accounts or login;
- cloud storage;
- social features;
- leaderboards;
- AI diagnosis of why someone sings off pitch;
- personalized lesson plans;
- polished game characters or elaborate effects;
- achievements or progression systems;
- a large song catalog;
- automatic key selection;
- automatic vocal-range detection;
- final scoring thresholds;
- production-grade cross-device echo cancellation;
- monetization.

These can be explored only after the core singing interaction works.

---

## 19. Open questions to revisit after the first playable build

The following are intentionally left tunable or undecided:

- final scoring tolerance / accuracy curve;
- exact adaptive-guide thresholds and timing;
- whether adaptive guide should fully disappear or remain faintly audible;
- pitch display smoothing;
- key / octave / vocal-range handling;
- final note-name notation;
- visual effects added when accuracy is high;
- phrase-selection UX;
- AEC quality across representative Android devices;
- final song list and rights verification for public distribution.

These questions should be answered through actual playtesting rather than guessed in advance.
