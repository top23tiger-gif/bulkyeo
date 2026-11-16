package com.bulkyeo.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** 날짜별로 모인 녹음을 보고, 듣고, 내보내고, 지우는 화면 */
class RecordingsActivity : Activity() {

    private lateinit var col: LinearLayout
    private var player: MediaPlayer? = null
    private var openNight: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 20))
            addView(col)
        })
        showNights()
    }

    override fun onPause() {
        super.onPause()
        player?.release()
        player = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (openNight != null) showNights() else super.onBackPressed()
    }

    private fun showNights() {
        openNight = null
        col.removeAllViews()
        col.addView(label("녹음 모음 (밤 기준 날짜)", 30f, Color.WHITE))
        col.addView(label("\"불켜/불꺼\" 가 아닌 소리만 모았어요.\n일주일마다 자동으로 내 파일 > Recordings > 불켜 로 옮겨지고,\n옮겨진 녹음은 앱에서 지워져요.", 18f, Color.GRAY))
        col.addView(button("지금 모두 기기 파일로 옮기기") {
            val skip = if (ListenService.isActive) Recordings.nightKey() else null
            Thread {
                val n = Recordings.moveAll(this, skip)
                runOnUiThread {
                    Toast.makeText(this, "${n}개를 옮겼어요 (내 파일 > Recordings > 불켜)", Toast.LENGTH_LONG).show()
                    showNights()
                }
            }.start()
        })
        val nights = Recordings.nights(this)
        if (nights.isEmpty()) col.addView(label("아직 녹음이 없어요.", 22f, Color.LTGRAY))
        for (n in nights) {
            col.addView(button("${n.name}  ·  ${Recordings.files(n).size}개  ·  ${Recordings.sizeText(n)}") { showNight(n) })
        }
    }

    private fun showNight(night: File) {
        openNight = night
        col.removeAllViews()
        col.addView(label(night.name, 30f, Color.WHITE))
        col.addView(button("← 날짜 목록") { showNights() })
        col.addView(button("이 날짜 기기 파일로 옮기기") {
            Thread {
                val n = Recordings.moveNight(this, night)
                runOnUiThread {
                    Toast.makeText(this, "${n}개를 옮겼어요 (내 파일 > Recordings > 불켜 > ${night.name})", Toast.LENGTH_LONG).show()
                    showNights()
                }
            }.start()
        })
        col.addView(button("이 날짜 모두 지우기") {
            AlertDialog.Builder(this)
                .setMessage("${night.name} 녹음을 모두 지울까요? 되돌릴 수 없어요.")
                .setPositiveButton("지우기") { _, _ ->
                    night.deleteRecursively()
                    showNights()
                }
                .setNegativeButton("취소", null)
                .show()
        })
        for (f in Recordings.files(night)) {
            val sec = (f.length() - 44) / 2.0 / guessRate(f)
            val time = f.nameWithoutExtension.substringAfter('_').replace('-', ':')
            col.addView(button("▶ $time  (${"%.1f".format(sec)}초)") { play(f) })
        }
    }

    private fun play(f: File) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(f.absolutePath)
            setOnCompletionListener { it.release(); if (player === it) player = null }
            prepare()
            start()
        }
    }

    private fun guessRate(f: File): Int = try {
        f.inputStream().use { s ->
            val h = ByteArray(28)
            s.read(h)
            (h[24].toInt() and 0xff) or ((h[25].toInt() and 0xff) shl 8) or
                ((h[26].toInt() and 0xff) shl 16) or ((h[27].toInt() and 0xff) shl 24)
        }.takeIf { it > 0 } ?: 16000
    } catch (e: Exception) {
        16000
    }

    private fun label(t: String, size: Float, color: Int) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        setPadding(0, 16, 0, 16)
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        textSize = 20f
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = 12 }
    }
}
