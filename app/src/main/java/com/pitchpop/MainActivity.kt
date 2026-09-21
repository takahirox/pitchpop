package com.pitchpop

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var songs: List<Song>
    private lateinit var engine: AudioEngine
    private lateinit var root: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var song: Song? = null
    private var playing = false
    private var pendingPermission = false
    private var noteNames = false
    private var allowOctaveDifferences = true
    private var guideMode = GuideMode.ON
    private var scoreView: TextView? = null
    private var progressView: TextView? = null
    private var pitchView: PitchTimelineView? = null
    private var microphoneView: TextView? = null
    private val ink = Color.rgb(35, 61, 56)
    private val poll = object : Runnable {
        override fun run() {
            if (!playing) return
            val state = engine.state
            scoreView?.text = "${state.score.toInt()} 点"
            val start = song!!.notes.first().startMs
            val end = song!!.durationMs
            progressView?.text = if (state.positionMs < start) "前奏 · あと ${ceil((start - state.positionMs) * song!!.bpm / 60000.0).toInt()} 拍" else
                "${max(0, (state.positionMs - start) / 1000)} / ${(end - start) / 1000} 秒　・　声を重ねてみよう"
            pitchView?.update(state)
            microphoneView?.text = when {
                state.positionMs < start -> "前奏を聴いて、歌い出しを待ってね"
                state.echoCalibrating -> "伴奏に合わせて歌ってね · 音を調整中"
                state.inputLevel < 0.0001 -> "マイクに音が届いていません"
                state.echoRejected -> "お手本に合わせて歌ってみよう"
                state.pitch?.voiced == true -> "♪ 歌声が聞こえているよ"
                else -> "マイク入力あり · 声を長くのばしてみよう"
            }
            if (state.finished) {
                playing = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                showResult(state)
            } else handler.postDelayed(this, 20)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        noteNames = preferences.getBoolean("noteNames", false)
        allowOctaveDifferences = preferences.getBoolean("allowOctaveDifferences", true)
        guideMode = GuideMode.values().firstOrNull { it.name == preferences.getString("guideMode", "ON") } ?: GuideMode.ON
        val repository = SongRepository(assets)
        engine = AudioEngine(this, repository)
        try { songs = repository.load(); showSongs() } catch (e: Exception) {
            newScreen(); label("楽曲を読み込めませんでした。", 24); label(e.message ?: "", 16)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun newScreen(scroll: Boolean = true) {
        scoreView = null; progressView = null; pitchView = null; microphoneView = null
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
            setBackgroundColor(Color.rgb(255, 249, 237))
        }
        val container: View = if (scroll) ScrollView(this).apply { isFillViewport = true; addView(root) } else root
        container.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(if (view === root) dp(24) else 0,
                insets.systemWindowInsetTop + if (view === root) dp(20) else 0,
                if (view === root) dp(24) else 0, insets.systemWindowInsetBottom + if (view === root) dp(16) else 0)
            insets
        }
        setContentView(container)
    }
    private fun label(text: String, size: Int = 18): TextView = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(ink)
        setPadding(0, dp(8), 0, dp(8)); root.addView(this)
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 18f; minHeight = dp(56)
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setOnClickListener { action() }
    }
    private fun showSongs() {
        song = null
        newScreen()
        label("PitchPop", 36).setTypeface(null, Typeface.BOLD)
        label("うたって、音をつかまえよう。", 20)
        label("好きなうたをえらんでね", 16)
        songs.forEach { entry -> button("♪  ${entry.title}") { song = entry; requestPlay() } }
        button("設定") { showSettings() }
    }
    private fun showSettings() {
        newScreen(); label("設定", 30)
        val toggle = Switch(this).apply {
            text = "音名を表示する"; textSize = 20f; isChecked = noteNames
            setPadding(0, dp(16), 0, dp(16))
            setOnCheckedChangeListener { _, checked -> noteNames = checked; preferences.edit().putBoolean("noteNames", checked).apply() }
        }
        root.addView(toggle)
        root.addView(Switch(this).apply {
            text = "オクターブ違いを許容する"; textSize = 18f; isChecked = allowOctaveDifferences
            setPadding(0, dp(16), 0, dp(16))
            setOnCheckedChangeListener { _, checked ->
                allowOctaveDifferences = checked
                preferences.edit().putBoolean("allowOctaveDifferences", checked).apply()
            }
        })
        label("オン：同じ音名ならオクターブ違いもOK\nオフ：お手本と同じ高さで表示・採点", 14)
        label("お手本のメロディ", 22)
        val group = RadioGroup(this)
        GuideMode.values().forEach { mode ->
            val radio = RadioButton(this).apply { id = View.generateViewId(); text = mode.label; textSize = 18f; minHeight = dp(56) }
            group.addView(radio); radio.isChecked = mode == guideMode
            radio.setOnClickListener { guideMode = mode; preferences.edit().putString("guideMode", mode.name).apply() }
        }
        root.addView(group)
        button("前奏の楽譜・出典") {
            val credit = assets.open("licenses/Introductions.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle("前奏の楽譜・出典").setMessage(credit).setPositiveButton("閉じる", null).show()
        }
        button("ライセンス") {
            val license = assets.open("licenses/SpeexDSP.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle("SpeexDSP 1.2.1").setMessage(license).setPositiveButton("閉じる", null).show()
        }
        button("もどる") { showSongs() }
    }
    private fun requestPlay() {
        if (engine.isActive) { Toast.makeText(this, "音声を準備しています。少し待ってね。", Toast.LENGTH_SHORT).show(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("歌声を見えるようにしよう")
                .setMessage("音程を表示するためにマイクを使います。歌声は保存・送信しません。")
                .setPositiveButton("つづける") { _, _ -> pendingPermission = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
                .setNegativeButton("もどる", null).show()
        } else startPlay()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && pendingPermission) {
            pendingPermission = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && song != null) startPlay()
            else AlertDialog.Builder(this).setMessage("歌うにはマイクの許可が必要です。許可できない場合は端末の設定から PitchPop のマイクを有効にしてください。")
                .setPositiveButton("OK", null).show()
        }
    }
    private fun startPlay() {
        val selected = song ?: return
        newScreen(scroll = false)
        label(selected.title, 24)
        scoreView = label("0 点", 32)
        progressView = label("準備しています…", 14)
        microphoneView = label("マイクを準備しています…", 14)
        pitchView = PitchTimelineView(this, selected, noteNames, allowOctaveDifferences).also {
            root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        label("緑がお手本・オレンジがあなたの声\n" +
            if (allowOctaveDifferences) "オクターブ違いは合わせて表示" else "声の高さをそのまま表示・採点", 14)
        button("やめる") { stopPlay(); showSongs() }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine.start(selected, guideMode, allowOctaveDifferences)
        playing = true
        handler.post(poll)
    }
    private fun showResult(state: SessionState) {
        newScreen()
        label(if (state.error == null) "歌ってくれてありがとう！" else "音声を確認してね", 28)
        label(song!!.title, 22)
        label("${state.score.toInt()} 点", 48)
        label(state.error ?: "もう一度、声を重ねてみよう。")
        button("もう一度歌う") { requestPlay() }
        button("曲をえらぶ") { showSongs() }
    }
    private fun stopPlay() {
        playing = false; handler.removeCallbacks(poll); engine.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onPause() {
        super.onPause()
        if (playing) { stopPlay(); showSongs() }
    }
    override fun onDestroy() { stopPlay(); super.onDestroy() }
    @Deprecated("Platform back callback retained for API 26 compatibility")
    override fun onBackPressed() {
        if (playing) { stopPlay(); showSongs() }
        else if (song != null) showSongs()
        else super.onBackPressed()
    }
}

class PitchTimelineView(context: android.content.Context, private val song: Song,
                        private val noteNames: Boolean,
                        private val allowOctaveDifferences: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var state = SessionState()
    private val history = ArrayDeque<PitchFrame>()
    private var referenceMidi = song.notes.first().midiNote
    private val low = song.notes.minOf { it.midiNote } - 4
    private val high = song.notes.maxOf { it.midiNote } + 4
    private val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    init { contentDescription = "お手本の音程と歌声を表示するタイムライン" }
    fun update(value: SessionState) {
        state = value
        value.pitch?.let { frame ->
            if (history.lastOrNull()?.timestampMs != frame.timestampMs) {
                song.targetAt(frame.timestampMs)?.let { referenceMidi = it.midiNote }
                val aligned = octaveAlignedPitch(frame.midiPitch, referenceMidi, allowOctaveDifferences)
                // Keep raw detection in SessionState/logs; only the visual trace changes octaves.
                val displayed = if (frame.voiced && aligned.isFinite())
                    frame.copy(frequencyHz = 440 * 2.0.pow((aligned - 69) / 12)) else frame
                history.addLast(displayed)
            }
        }
        while (history.isNotEmpty() && history.first().timestampMs < value.positionMs - 2000) history.removeFirst()
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nowX = width * 0.28f
        val scale = width / 6500f
        fun x(ms: Long) = nowX + (ms - state.positionMs) * scale
        fun y(midi: Double) = height * 0.9f - ((midi - low) / (high - low) * height * 0.8).toFloat()
        val noteHeight = min(30f * resources.displayMetrics.density, height * 0.7f / (high - low))
        canvas.drawColor(Color.rgb(242, 241, 225))
        paint.strokeWidth = resources.displayMetrics.density
        for (midi in low..high) {
            paint.color = Color.rgb(220, 223, 211)
            canvas.drawLine(0f, y(midi.toDouble()), width.toFloat(), y(midi.toDouble()), paint)
        }
        song.notes.forEach { note ->
            val left = x(note.startMs); val right = x(note.endMs)
            if (right < 0 || left > width) return@forEach
            paint.color = if (state.positionMs in note.startMs until note.endMs) Color.rgb(55, 135, 109) else Color.rgb(139, 181, 143)
            val top = y(note.midiNote.toDouble()) - noteHeight / 2
            canvas.drawRoundRect(left, top, right - 2, top + noteHeight, 8f, 8f, paint)
            if (noteNames) {
                paint.color = Color.rgb(25, 58, 46); paint.textSize = min(noteHeight * 0.9f, 16f * resources.displayMetrics.scaledDensity)
                canvas.drawText(names[note.midiNote % 12], left + 4, top + noteHeight * 0.82f, paint)
            }
        }
        paint.color = Color.rgb(71, 101, 89); paint.strokeWidth = 2 * resources.displayMetrics.density
        canvas.drawLine(nowX, 0f, nowX, height.toFloat(), paint)
        paint.color = Color.rgb(229, 135, 61); paint.strokeWidth = 4 * resources.displayMetrics.density
        var previous: PitchFrame? = null
        history.forEach { frame ->
            if (frame.voiced && frame.confidence >= 0.8 && frame.midiPitch.isFinite()) {
                val py = y(frame.midiPitch).coerceIn(8f, max(8f, height - 8f))
                previous?.let { old -> if (frame.timestampMs - old.timestampMs in 1..60)
                    canvas.drawLine(x(old.timestampMs), y(old.midiPitch).coerceIn(8f, max(8f, height - 8f)), x(frame.timestampMs), py, paint) }
                canvas.drawCircle(x(frame.timestampMs), py, 3 * resources.displayMetrics.density, paint)
                previous = frame
            } else previous = null
        }
    }
}
