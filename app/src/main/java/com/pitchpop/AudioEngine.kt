package com.pitchpop

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.os.Process
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.*

data class SessionState(val positionMs: Long = 0, val score: Double = 0.0,
                        val pitch: PitchFrame? = null, val guideVolume: Double = 1.0,
                        val inputLevel: Double = 0.0,
                        val echoCalibrating: Boolean = false, val echoRejected: Boolean = false,
                        val aecEnabled: Boolean = false, val finished: Boolean = false, val error: String? = null)

/** Owns the audio resources on one worker; stop is nonblocking and cleanup always precedes completion. */
class AudioEngine(private val context: Context, private val repository: SongRepository,
                  private val detector: PitchDetector = YinDetector()) {
    companion object { const val SAMPLE_RATE = 48000; const val HOP = 960 }
    @Volatile var state = SessionState(); private set
    @Volatile private var running = false
    @Volatile private var failure: String? = null
    @Volatile private var guideGain = 1.0
    @Volatile private var worker: Thread? = null
    val isActive: Boolean get() = worker?.isAlive == true

    fun start(song: Song, mode: GuideMode, allowOctaveDifferences: Boolean = true) {
        check(!isActive)
        val soundStart = song.notes.first().startMs
        val start = soundStart - SongIntroductions.durationMs(song)
        val end = song.durationMs
        running = true
        failure = null
        guideGain = if (mode == GuideMode.OFF) 0.0 else 1.0
        state = SessionState(positionMs = start, guideVolume = guideGain)
        worker = thread(name = "PitchPop audio") {
            var track: AudioTrack? = null
            var record: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var capture: Thread? = null
            val manager = context.getSystemService(AudioManager::class.java)
            // Use the normal media route/volume; do not acquire or restore a communication route.
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                    if (change < 0) { failure = "ほかのアプリが音声を使用しています。もう一度遊んでね。"; running = false }
                }.build()
            try {
                val accompaniment = repository.pcm(song.accompaniment)
                val melody = repository.pcm(song.guideMelody)
                val arrangement = PlaybackArrangement(song, accompaniment, melody, SAMPLE_RATE)
                check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "音声を開始できませんでした。" }
                val format = AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                val outputMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(outputMin > 0) { "48 kHz の音声再生に対応していません。" }
                val output = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                    .setBufferSizeInBytes(max(outputMin, HOP * 4)).setTransferMode(AudioTrack.MODE_STREAM).build()
                track = output
                check(output.state == AudioTrack.STATE_INITIALIZED)
                val inputMin = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(inputMin > 0) { "48 kHz のマイク入力に対応していません。" }
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    throw SecurityException("マイクの使用を許可してください。")
                }
                val input = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(inputMin, SAMPLE_RATE * 2))
                record = input
                check(input.state == AudioRecord.STATE_INITIALIZED) { "マイクを開始できませんでした。" }
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = runCatching { AcousticEchoCanceler.create(input.audioSessionId)?.also { it.enabled = true } }.getOrNull()
                }
                state = state.copy(aecEnabled = aec?.enabled == true)
                if (!running) return@thread
                input.startRecording()
                check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "マイクが使用できません。" }
                output.play()
                val scoring = ScoringEngine(ScoringConfig(allowOctaveDifferences = allowOctaveDifferences))
                val adaptive = AdaptiveGuide()
                val reference = PlaybackReference()
                capture = thread(name = "PitchPop pitch") {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                    try {
                        SoftwareEchoCanceller().use { echo ->
                            val raw = ShortArray(HOP)
                            val rendered = ShortArray(HOP)
                            val mic16 = ShortArray(SoftwareEchoCanceller.FRAME_SIZE)
                            val render16 = ShortArray(SoftwareEchoCanceller.FRAME_SIZE)
                            val cleaned = ShortArray(SoftwareEchoCanceller.FRAME_SIZE)
                            val referenceClock = EchoReferenceClock()
                            var gate = EchoGate()
                            var voiceGate = StableVoiceGate()
                            val window = ShortArray(768) // 48 ms at 16 kHz, updated every 20 ms.
                            var filled = 0
                            var lastDiagnosticMs = 0L
                            var previousMicLevel = 0.0
                            var previousReferenceLevel = 0.0
                            while (running) {
                                var read = 0
                                while (read < raw.size && running) {
                                    val count = input.read(raw, read, raw.size - read)
                                    check(count > 0) { "マイクの読み取りが中断されました。" }
                                    read += count
                                }
                                if (!running) break
                                val inputLevel = pcmRms(raw)
                                val playbackHead = output.playbackHeadPosition.toLong() and 0xffffffffL
                                val position = start + playbackHead * 1000 / SAMPLE_RATE
                                val referenceStart = referenceClock.nextFrame((playbackHead - HOP).coerceAtLeast(0))
                                if (referenceClock.discontinuity) {
                                    check(position < soundStart || filled == 0) { "音声の同期が変わりました。もう一度遊んでね。" }
                                    echo.reset(); gate = EchoGate(); voiceGate = StableVoiceGate(); window.fill(0); filled = 0
                                    previousMicLevel = 0.0; previousReferenceLevel = 0.0
                                }
                                val hasReference = reference.read(referenceStart, rendered)
                                downsampleForPitch(raw, mic16)
                                downsampleForPitch(rendered, render16)
                                if (hasReference) echo.process(mic16, render16, cleaned)
                                else { cleaned.fill(0); referenceClock.invalidate() }
                                val residualLevel = pcmRms(cleaned)
                                val allowVoice = hasReference && gate.allow(previousMicLevel, residualLevel, previousReferenceLevel)
                                previousMicLevel = pcmRms(mic16)
                                previousReferenceLevel = pcmRms(render16)
                                window.copyInto(window, 0, 320, window.size)
                                for (i in cleaned.indices) window[window.size - 320 + i] = if (allowVoice) cleaned[i] else 0
                                filled += 320
                                val timestamp = position - 24 - SoftwareEchoCanceller.PROCESSING_DELAY_MS
                                val candidate = if (allowVoice && filled >= window.size) detector.detect(window, 16000, timestamp)
                                    else PitchFrame(timestamp, 0.0, 0.0, false)
                                val frame = voiceGate.filter(candidate)
                                val target = if (frame.timestampMs >= soundStart && frame.timestampMs < end) song.targetAt(frame.timestampMs) else null
                                val accuracy = scoring.add(frame, target, HOP.toDouble() / SAMPLE_RATE)
                                guideGain = adaptive.update(mode, accuracy, HOP.toDouble() / SAMPLE_RATE, target != null)
                                state = state.copy(positionMs = position.coerceAtMost(end), score = scoring.score, pitch = frame,
                                    guideVolume = guideGain, inputLevel = inputLevel, echoCalibrating = position < soundStart || !hasReference || gate.calibrating,
                                    echoRejected = !allowVoice)
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && now - lastDiagnosticMs >= 1000) {
                                    val silenced = if (Build.VERSION.SDK_INT >= 29) input.activeRecordingConfiguration?.isClientSilenced else null
                                    Log.d("PitchPopAudio", "rms=$inputLevel residual=$residualLevel echoRejected=${!allowVoice} hz=${frame.frequencyHz} confidence=${frame.confidence} silenced=$silenced aec=${state.aecEnabled}")
                                    lastDiagnosticMs = now
                                }
                            }
                        }
                    } catch (e: LinkageError) {
                        if (running) { failure = "エコー除去を読み込めませんでした。アプリを再インストールしてください。"; running = false }
                    } catch (e: Exception) {
                        if (running) { failure = e.message ?: "マイクのエラーが発生しました。"; running = false }
                    }
                }
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val firstSample = arrangement.firstSample.toInt()
                val lastSample = arrangement.lastSample.toInt()
                val mix = ShortArray(HOP)
                var cursor = firstSample
                var smoothedGain = guideGain
                while (running && cursor < lastSample) {
                    val count = min(HOP, lastSample - cursor)
                    for (i in 0 until count) {
                        smoothedGain += (guideGain - smoothedGain) * 0.002
                        val index = cursor + i
                        mix[i] = arrangement.sampleAt(index.toLong(), smoothedGain)
                    }
                    var written = 0
                    while (running && written < count) {
                        val result = output.write(mix, written, count - written, AudioTrack.WRITE_NON_BLOCKING)
                        check(result >= 0) { "音声の再生が中断されました。" }
                        if (result == 0) { Thread.sleep(2); continue }
                        reference.append(mix, written, result)
                        written += result
                    }
                    cursor += written
                }
                reference.finish()
                val drainStart = android.os.SystemClock.elapsedRealtime()
                while (running && (output.playbackHeadPosition.toLong() and 0xffffffffL) < lastSample - firstSample) {
                    check(android.os.SystemClock.elapsedRealtime() - drainStart < 5000) { "音声の再生が停止しました。" }
                    Thread.sleep(5)
                }
            } catch (e: Exception) {
                if (running) failure = e.message ?: "音声のエラーが発生しました。"
            } finally {
                running = false
                runCatching { record?.stop() }
                capture?.join()
                runCatching { aec?.release() }
                runCatching { record?.release() }
                runCatching { track?.stop() }
                runCatching { track?.release() }
                runCatching {
                    manager.abandonAudioFocusRequest(focus)
                }
                state = state.copy(finished = true, error = failure)
            }
        }
    }
    fun stop() { running = false }
}
