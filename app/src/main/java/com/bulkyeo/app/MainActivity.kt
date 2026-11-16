package com.bulkyeo.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 설정 화면. 처음 한 번 권한을 허용하고 "시작" 을 누르면,
 * 그 다음부터는 앱을 닫아도 "불켜 / 불꺼" 를 계속 듣는다.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var fullScreenButton: Button
    private lateinit var overlayButton: Button
    private var askedPermissions = false
    private lateinit var prefs: Prefs
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var batteryInfo: TextView
    private lateinit var heard: TextView
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            status.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(64, 96, 64, 96)
        }
        val title = TextView(this).apply {
            text = "불켜"
            textSize = 44f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.rgb(230, 230, 230))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            setPadding(0, 48, 0, 48)
        }
        fullScreenButton = bigButton("① 전체 화면 알림 허용하기") { openFullScreenSettings() }
        overlayButton = bigButton("② 다른 앱 위에 표시 허용하기 (불켜 때 바로 하얀 화면)") {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        val startButton = bigButton("시작 (앱을 닫아도 계속 들어요)") { startListening() }
        val stopButton = bigButton("중지") {
            ListenService.stop(this)
            status.postDelayed({ refresh() }, 300)
        }
        val testButton = bigButton("조명 시험해 보기") {
            startActivity(Intent(this, LightActivity::class.java))
        }
        val batteryButton = bigButton("배터리 설정 열기 (제한 없음으로)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        // ---- 배터리 절약 설정
        val chargeBox = bigCheck("충전기에 꽂혀 있을 때만 듣기", prefs.chargingOnly) {
            prefs.chargingOnly = it
            ListenService.recheck(this)
            status.postDelayed({ refresh() }, 300)
        }
        startTimeButton = bigButton("") { pickTime(true) }
        endTimeButton = bigButton("") { pickTime(false) }
        val scheduleBox = bigCheck("정해 둔 시간에만 듣기", prefs.scheduleOn) {
            prefs.scheduleOn = it
            updateTimeButtons()
            ListenService.recheck(this)
            status.postDelayed({ refresh() }, 300)
        }
        updateTimeButtons()
        val ecoBox = bigCheck("배터리 효율 모드 (소리 센서 8kHz)", prefs.ecoMode) {
            prefs.ecoMode = it
            ListenService.recheck(this)
            status.postDelayed({ refresh() }, 300)
        }
        val sysBox = bigCheck("구글/삼성 음성 인식 함께 쓰기 (더 정확함)", prefs.useSystemRecognizer) {
            prefs.useSystemRecognizer = it
        }
        val testButton2 = bigButton("10분 테스트 모드 (시간·충전 조건 무시)") {
            prefs.testUntil = System.currentTimeMillis() + 10 * 60 * 1000
            if (ListenService.isRunning) ListenService.recheck(this) else startListening()
        }
        heard = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.rgb(255, 220, 120))
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 24)
        }
        val recBox = bigCheck("다른 소리 녹음해 두기 (날짜별)", prefs.saveRecordings) {
            prefs.saveRecordings = it
        }
        val recButton = bigButton("녹음 모음 보기") {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
        batteryInfo = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(170, 170, 170))
            setPadding(16, 16, 16, 0)
        }

        col.addView(title)
        col.addView(status)
        col.addView(heard)
        col.addView(testButton2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(sectionLabel("배터리 절약"))
        for (v in listOf(chargeBox, ecoBox, sysBox, scheduleBox, startTimeButton, endTimeButton, batteryInfo)) {
            col.addView(
                v,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = 16 }
            )
        }
        col.addView(sectionLabel("녹음"))
        for (v in listOf(recBox, recButton)) {
            col.addView(
                v,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = 16 }
            )
        }
        col.addView(sectionLabel("동작"))
        for (b in listOf(fullScreenButton, overlayButton, startButton, stopButton, testButton, batteryButton)) {
            col.addView(
                b,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = 24 }
            )
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 20))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        LightState.main = java.lang.ref.WeakReference(this)
        LightState.mainVisible = true
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            // 권한 창은 화면을 열 때 한 번만 자동으로 띄운다 (거절하면 '시작' 을 눌러 다시)
            if (!askedPermissions) {
                askedPermissions = true
                requestPermissions(missing.toTypedArray(), REQ_PERMS)
            }
        } else if (canFullScreen() && !ListenService.isRunning) {
            // 준비가 다 되어 있으면 앱을 열기만 해도 자동으로 시작
            ListenService.start(this)
        }
        status.removeCallbacks(ticker)
        status.postDelayed(ticker, 300)
    }

    override fun onPause() {
        super.onPause()
        LightState.mainVisible = false
        status.removeCallbacks(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (missingPermissions().isEmpty() && canFullScreen()) ListenService.start(this)
        status.postDelayed({ refresh() }, 300)
    }

    private fun startListening() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQ_PERMS)
            return
        }
        ListenService.start(this)
        status.postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val missing = missingPermissions()
        val fs = canFullScreen()
        fullScreenButton.visibility = if (fs) View.GONE else View.VISIBLE
        val overlay = Settings.canDrawOverlays(this)
        overlayButton.visibility = if (overlay) View.GONE else View.VISIBLE
        val h = ListenService.lastHeard
        heard.text = if (h.isEmpty()) "마지막으로 들은 말: (아직 없음)" else "마지막으로 들은 말: $h"
        val report = prefs.lastBatteryReport
        batteryInfo.text = if (report.isEmpty()) {
            "배터리 측정: 충전기 없이 30분 넘게 들으면 여기에 실제 사용량이 표시돼요."
        } else {
            "최근 측정: $report"
        }
        status.text = when {
            Manifest.permission.RECORD_AUDIO in missing ->
                "마이크 권한이 필요해요.\n설정 > 애플리케이션 > 불켜 > 권한 에서\n마이크를 허용해 주세요."
            missing.isNotEmpty() ->
                "알림 권한이 필요해요.\n(듣고 있다는 표시와 조명을 띄우는 데 써요)"
            !overlay ->
                "\"불켜\" 때 바로 하얀 화면이 뜨려면\n'다른 앱 위에 표시' 허용이 필요해요.\n아래 ② 버튼을 눌러 「불켜」를 켜 주세요."
            !fs ->
                "화면이 꺼져 있을 때 불을 켜려면\n'전체 화면 알림' 허용이 필요해요.\n아래 ① 버튼을 눌러 켜 주세요."
            ListenService.isRunning && prefs.inTestMode -> {
                val left = (prefs.testUntil - System.currentTimeMillis()) / 1000
                "🧪 테스트 모드 (${left / 60}분 ${left % 60}초 남음)\n\"불켜\" / \"불꺼\" 라고 말해 보세요."
            }
            ListenService.isRunning && ListenService.isActive ->
                "✅ 듣고 있어요.\n\n이제 앱을 닫아도 돼요.\n\"불켜\" / \"불꺼\" 라고 말하세요."
            ListenService.isRunning ->
                "✅ 준비됐어요 (지금은 쉬는 중).\n\n" + waitReason() + "\n이제 앱을 닫아도 돼요."
            else ->
                "멈춰 있어요.\n'시작' 을 눌러 주세요."
        }
    }

    private fun waitReason(): String = when {
        !prefs.inSchedule() -> "${Prefs.fmt(prefs.startMin)}부터 자동으로 들어요."
        prefs.chargingOnly && !Prefs.isPluggedIn(this) -> "충전기를 꽂으면 자동으로 들어요."
        else -> ""
    }

    private fun updateTimeButtons() {
        startTimeButton.text = "시작 시각: ${Prefs.fmt(prefs.startMin)}"
        endTimeButton.text = "끝 시각: ${Prefs.fmt(prefs.endMin)}"
        val v = if (prefs.scheduleOn) View.VISIBLE else View.GONE
        startTimeButton.visibility = v
        endTimeButton.visibility = v
    }

    private fun pickTime(isStart: Boolean) {
        val cur = if (isStart) prefs.startMin else prefs.endMin
        TimePickerDialog(this, { _, h, m ->
            if (isStart) prefs.startMin = h * 60 + m else prefs.endMin = h * 60 + m
            updateTimeButtons()
            ListenService.recheck(this)
            status.postDelayed({ refresh() }, 300)
        }, cur / 60, cur % 60, false).show()
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setTextColor(Color.rgb(150, 150, 150))
        setPadding(0, 48, 0, 0)
    }

    private fun bigCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        textSize = 22f
        setTextColor(Color.WHITE)
        isChecked = checked
        setPadding(16, 24, 16, 24)
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun missingPermissions(): List<String> {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        return perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun canFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun bigButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 22f
        isAllCaps = false
        setPadding(24, 36, 24, 36)
        setOnClickListener { onClick() }
    }

    companion object {
        private const val REQ_PERMS = 1
    }
}
