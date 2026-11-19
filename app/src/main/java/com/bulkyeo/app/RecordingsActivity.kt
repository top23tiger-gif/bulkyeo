package com.bulkyeo.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * 잠꼬대 플레이어.
 * 앱 안에 있는 녹음과, 일주일마다 기기 파일(내 파일 > Recordings > 불켜)로 옮겨진 녹음을
 * 모두 밤 날짜별로 보여주고 들을 수 있다.
 */
class RecordingsActivity : Activity() {

    private lateinit var col: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var timeText: TextView
    private lateinit var seek: SeekBar
    private lateinit var playButton: Button
    private lateinit var continuousBox: CheckBox
    private lateinit var boostBox: CheckBox

    private var catalog: Map<String, List<Recordings.Clip>> = emptyMap()
    private var openNight: String? = null

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var queue: List<Recordings.Clip> = emptyList()
    private var index = -1
    private var userSeeking = false

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            seek.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.rgb(20, 20, 20)

        // ---- 위쪽: 플레이어 (항상 보임)
        nowTitle = label("들을 녹음을 아래에서 골라 주세요", 22f, Color.WHITE).apply { gravity = Gravity.CENTER }
        timeText = label("0:00 / 0:00", 18f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        seek = SeekBar(this).apply {
            setPadding(48, 24, 48, 24)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) timeText.text = "${fmt(progress.toLong())} / ${fmt(sb.max.toLong())}"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {
                    userSeeking = true
                }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    userSeeking = false
                    player?.seekTo(sb.progress)
                }
            })
        }
        val prev = button("⏮ 이전") { playAt(index - 1) }
        playButton = button("▶ 재생") { togglePlay() }
        val next = button("다음 ⏭") { playAt(index + 1) }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in listOf(prev, playButton, next)) {
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 8
                    marginEnd = 8
                })
            }
        }
        continuousBox = CheckBox(this).apply {
            text = "연속 재생 (그날 밤 녹음 차례로)"
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = true
        }
        boostBox = CheckBox(this).apply {
            text = "작은 소리 크게 듣기"
            textSize = 18f
            setTextColor(Color.WHITE)
            isChecked = true
            setOnCheckedChangeListener { _, on -> enhancer?.enabled = on }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
            setBackgroundColor(Color.rgb(35, 35, 45))
            addView(label("🌙 잠꼬대 듣기", 28f, Color.WHITE).apply { gravity = Gravity.CENTER })
            addView(nowTitle)
            addView(seek)
            addView(timeText)
            addView(controls)
            addView(continuousBox)
            addView(boostBox)
        }

        // ---- 아래쪽: 날짜 / 녹음 목록
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 64)
        }
        val scroll = ScrollView(this).apply { addView(col) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(panel)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })

        // 기기 파일로 옮겨진 녹음도 보려면 (앱을 다시 설치한 경우) 오디오 읽기 권한이 필요
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), REQ_READ)
        }
        reload()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        reload()
    }

    override fun onResume() {
        super.onResume()
        seek.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        seek.removeCallbacks(ticker)
        player?.pause()
        updatePlayButton()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (openNight != null) showNights() else super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DELETE) reload()
    }

    // ------------------------------------------------------------ 목록

    private fun reload() {
        Thread {
            val c = Recordings.catalog(this)
            runOnUiThread {
                catalog = c
                val n = openNight
                if (n != null && catalog.containsKey(n)) showNight(n) else showNights()
            }
        }.start()
    }

    private fun showNights() {
        openNight = null
        col.removeAllViews()
        col.addView(label("밤 날짜를 고르세요", 22f, Color.WHITE))
        col.addView(label("잠꼬대만 모았어요 (코골이·숨소리는 AI 가 걸러요).\n밤 9시 ~ 아침 6시 녹음은 시작한 밤의 날짜로 묶이고,\n불이 켜져 있을 때(깨어 있을 때)는 녹음하지 않아요.\n한 말 기록: 내 파일 > Documents > 불켜", 16f, Color.GRAY))
        if (catalog.isEmpty()) col.addView(label("아직 녹음이 없어요.", 22f, Color.LTGRAY))
        for ((night, clips) in catalog) {
            val talk = clips.filter { !it.snore }
            val snore = clips.filter { it.snore }.sumOf { it.durationMs }
            val snoreText = if (snore > 0) "  ·  😴 ${fmt(snore)}" else ""
            col.addView(button("📅 ${nightLabel(night)}  ·  잠꼬대 ${talk.size}개$snoreText") { showNight(night) })
        }
        col.addView(button("앱 안 녹음을 지금 기기 파일로 옮기기") {
            val skip = if (ListenService.isActive) Recordings.nightKey() else null
            Thread {
                val n = Recordings.moveAll(this, skip)
                runOnUiThread {
                    Toast.makeText(this, "${n}개를 옮겼어요 (내 파일 > Recordings > 불켜)", Toast.LENGTH_LONG).show()
                    reload()
                }
            }.start()
        })
    }

    private fun showNight(night: String) {
        openNight = night
        val all = catalog[night] ?: emptyList()
        val clips = all.filter { !it.snore }
        val snores = all.filter { it.snore }
        col.removeAllViews()
        col.addView(button("← 날짜 목록") { showNights() })
        col.addView(label("${nightLabel(night)}  (${clips.size}개)", 24f, Color.WHITE))
        col.addView(button("▶ 이 밤 처음부터 모두 듣기") {
            queue = clips
            playAt(0)
        })
        val said = Transcripts.lines(this, night)
        val tf = Transcripts.file(this, night)
        if (tf.exists()) {
            col.addView(button("📝 이 밤 잠꼬대 글로 보기") {
                AlertDialog.Builder(this)
                    .setTitle("${nightLabel(night)} 잠꼬대 기록")
                    .setMessage(tf.readText())
                    .setPositiveButton("닫기", null)
                    .show()
            })
        }
        clips.forEachIndexed { i, clip ->
            val where = if (clip.inApp) "" else "  ·  📁"
            val words = said[clip.timeText]?.let { "\n“$it”" } ?: ""
            col.addView(button("${clip.timeText}   (${fmt(clip.durationMs)})$where$words") {
                queue = clips
                playAt(i)
            }.apply {
                setOnLongClickListener {
                    confirmDelete(listOf(clip), "${clip.timeText} 녹음을 지울까요?")
                    true
                }
            })
        }
        if (snores.isNotEmpty()) {
            col.addView(label("😴 코골이 모음 (시간대별, 총 ${fmt(snores.sumOf { it.durationMs })})", 22f, Color.WHITE))
            snores.forEachIndexed { i, clip ->
                val where = if (clip.inApp) "" else "  ·  📁"
                col.addView(button("${clip.timeText} 코골이   (${fmt(clip.durationMs)})$where") {
                    queue = snores
                    playAt(i)
                }.apply {
                    setOnLongClickListener {
                        confirmDelete(listOf(clip), "${clip.timeText} 코골이 파일을 지울까요?")
                        true
                    }
                })
            }
        }
        col.addView(label("녹음을 길게 누르면 지울 수 있어요. 📁 = 기기 파일에 있는 녹음", 16f, Color.GRAY))
        col.addView(button("이 밤 녹음 모두 지우기") {
            confirmDelete(all, "${nightLabel(night)} 녹음 ${all.size}개(코골이 포함)를 모두 지울까요? 되돌릴 수 없어요.")
        })
    }

    private fun confirmDelete(clips: List<Recordings.Clip>, msg: String) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("지우기") { _, _ -> delete(clips) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun delete(clips: List<Recordings.Clip>) {
        if (queue.any { q -> clips.any { it.uri == q.uri } }) releasePlayer()
        for (c in clips) c.file?.delete()
        val device = clips.filter { !it.inApp }.map { it.uri }
        if (device.isNotEmpty()) {
            try {
                // 기기 파일은 안드로이드가 한 번 더 확인을 물어본다
                val req = MediaStore.createDeleteRequest(contentResolver, device)
                startIntentSenderForResult(req.intentSender, REQ_DELETE, null, 0, 0, 0)
                return
            } catch (e: Exception) {
                Toast.makeText(this, "기기 파일은 내 파일 앱에서 지워 주세요", Toast.LENGTH_LONG).show()
            }
        }
        reload()
    }

    // ------------------------------------------------------------ 재생

    private fun playAt(i: Int) {
        if (i < 0 || i >= queue.size) return
        releasePlayer()
        index = i
        val clip = queue[i]
        nowTitle.text = "${nightLabel(clip.night)}  ${clip.timeText}  (${i + 1}/${queue.size})"
        try {
            val mp = MediaPlayer()
            mp.setDataSource(this, clip.uri)
            mp.setOnCompletionListener {
                updatePlayButton()
                if (continuousBox.isChecked && index + 1 < queue.size) playAt(index + 1)
            }
            mp.prepare()
            enhancer = try {
                LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(1500) // +15dB: 작게 웅얼거린 잠꼬대도 잘 들리게
                    enabled = boostBox.isChecked
                }
            } catch (_: Exception) {
                null
            }
            mp.start()
            player = mp
            seek.max = mp.duration
        } catch (e: Exception) {
            Toast.makeText(this, "이 녹음을 재생할 수 없어요", Toast.LENGTH_SHORT).show()
        }
        updatePlayButton()
    }

    private fun togglePlay() {
        val mp = player
        when {
            mp == null -> if (queue.isNotEmpty()) playAt(index.coerceAtLeast(0))
            mp.isPlaying -> mp.pause()
            else -> mp.start()
        }
        updatePlayButton()
    }

    private fun releasePlayer() {
        enhancer?.release()
        enhancer = null
        player?.release()
        player = null
        updatePlayButton()
    }

    private fun updatePlayButton() {
        playButton.text = if (player?.isPlaying == true) "⏸ 멈춤" else "▶ 재생"
    }

    private fun updateProgress() {
        val mp = player ?: return
        if (userSeeking) return
        try {
            seek.progress = mp.currentPosition
            timeText.text = "${fmt(mp.currentPosition.toLong())} / ${fmt(mp.duration.toLong())}"
            updatePlayButton()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------ 도움 함수

    /** "2026-11-16" -> "11월 16일 밤" */
    private fun nightLabel(night: String): String {
        val p = night.split("-")
        return if (p.size == 3) "${p[1].toInt()}월 ${p[2].toInt()}일 밤" else night
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "${s / 60}:${"%02d".format(s % 60)}"
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        setPadding(0, 12, 0, 12)
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        textSize = 20f
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 12 }
    }

    companion object {
        private const val REQ_READ = 10
        private const val REQ_DELETE = 11
    }
}
