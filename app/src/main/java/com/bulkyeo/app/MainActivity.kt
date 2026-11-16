package com.bulkyeo.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
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
    private var askedPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        col.addView(title)
        col.addView(status)
        for (b in listOf(fullScreenButton, startButton, stopButton, testButton, batteryButton)) {
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
        status.postDelayed({ refresh() }, 300)
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
        status.text = when {
            Manifest.permission.RECORD_AUDIO in missing ->
                "마이크 권한이 필요해요.\n설정 > 애플리케이션 > 불켜 > 권한 에서\n마이크를 허용해 주세요."
            missing.isNotEmpty() ->
                "알림 권한이 필요해요.\n(듣고 있다는 표시와 조명을 띄우는 데 써요)"
            !fs ->
                "화면이 꺼져 있을 때 불을 켜려면\n'전체 화면 알림' 허용이 필요해요.\n아래 ① 버튼을 눌러 켜 주세요."
            ListenService.isRunning ->
                "✅ 듣고 있어요.\n\n이제 앱을 닫아도 돼요.\n\"불켜\" / \"불꺼\" 라고 말하세요."
            else ->
                "멈춰 있어요.\n'시작' 을 눌러 주세요."
        }
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
