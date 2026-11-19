package com.bulkyeo.english

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** 첫 화면: 최근 문장 미리보기 + 알림 시간 설정 */
class MainActivity : ComponentActivity() {

    private lateinit var timesList: LinearLayout
    private lateinit var permissionCard: View

    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val content = findViewById<View>(R.id.content)
        val pad = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.updatePadding(top = pad + bars.top, bottom = pad + bars.bottom)
            insets
        }

        timesList = findViewById(R.id.timesList)
        permissionCard = findViewById(R.id.permissionCard)

        SentenceNotifier.ensureChannel(this)
        AlarmScheduler.scheduleAll(this)

        findViewById<View>(R.id.previewCard).apply {
            clipToOutline = true // 둥근 모서리로 그림 자르기
            setOnClickListener { openSentence() }
        }
        findViewById<View>(R.id.btnOpen).setOnClickListener { openSentence() }
        findViewById<View>(R.id.btnAddTime).setOnClickListener { addTime() }
        findViewById<View>(R.id.btnPermission).setOnClickListener { askNotificationPermission(force = true) }
        findViewById<View>(R.id.btnTest).setOnClickListener {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                askNotificationPermission(force = true)
            } else {
                SentenceNotifier.show(this, Sentences.get(this, Prefs.takeNextIndex(this)))
                Toast.makeText(this, "알림을 보냈어요. 화면 위쪽을 확인해 보세요!", Toast.LENGTH_SHORT).show()
            }
        }

        askNotificationPermission(force = false)
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
        refreshTimes()
        refreshPermission()
    }

    private fun openSentence() {
        startActivity(
            Intent(this, SentenceActivity::class.java).putExtra(SentenceActivity.EXTRA_INDEX, Prefs.lastIndex(this))
        )
    }

    private fun refreshPreview() {
        val s = Sentences.get(this, Prefs.lastIndex(this))
        findViewById<ImageView>(R.id.previewImage).setImageBitmap(Sentences.loadBackground(this, s, 900))
        findViewById<TextView>(R.id.previewEnglish).text = s.en
        findViewById<TextView>(R.id.previewKorean).text = s.ko
    }

    // ---- 알림 시간 목록 ----

    private fun refreshTimes() {
        timesList.removeAllViews()
        val times = Prefs.times(this)
        if (times.isEmpty()) {
            timesList.addView(TextView(this).apply {
                text = "알림 시간이 없어요. 아래 버튼으로 추가해 주세요."
                textSize = 17f
                setTextColor(ContextCompat.getColor(context, R.color.inkSoft))
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        times.forEach { minutes -> timesList.addView(timeRow(minutes)) }
    }

    private fun timeRow(minutes: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.card)
            setPadding(dp(20), dp(14), dp(12), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val label = TextView(this).apply {
            text = formatTime(minutes)
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { changeTime(minutes) }
        }
        val change = TextView(this).apply {
            text = "바꾸기"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.accent))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { changeTime(minutes) }
        }
        val delete = TextView(this).apply {
            text = "삭제"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.danger))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { confirmDelete(minutes) }
        }
        row.addView(label)
        row.addView(change)
        row.addView(delete)
        return row
    }

    private fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    private fun addTime() {
        pickTime(12 * 60) { minutes ->
            val times = Prefs.times(this)
            if (minutes in times) {
                Toast.makeText(this, "이미 있는 시간이에요.", Toast.LENGTH_SHORT).show()
                return@pickTime
            }
            Prefs.setTimes(this, times + minutes)
            AlarmScheduler.schedule(this, minutes)
            refreshTimes()
            Toast.makeText(this, "${formatTime(minutes)}에 알려드릴게요!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun changeTime(old: Int) {
        pickTime(old) { minutes ->
            if (minutes == old) return@pickTime
            AlarmScheduler.cancel(this, old)
            Prefs.setTimes(this, Prefs.times(this) - old + minutes)
            AlarmScheduler.schedule(this, minutes)
            refreshTimes()
        }
    }

    private fun confirmDelete(minutes: Int) {
        AlertDialog.Builder(this)
            .setMessage("${formatTime(minutes)} 알림을 지울까요?")
            .setPositiveButton("지우기") { _, _ ->
                AlarmScheduler.cancel(this, minutes)
                Prefs.setTimes(this, Prefs.times(this) - minutes)
                refreshTimes()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---- 알림 권한 ----

    private fun askNotificationPermission(force: Boolean) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canAsk && (!force || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) || !askedBefore())) {
            markAsked()
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (force) {
            // 이미 거절했으면 설정 화면으로 안내
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun askedBefore() = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("asked_notif", false)
    private fun markAsked() = getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("asked_notif", true).apply()

    private fun refreshPermission() {
        permissionCard.visibility =
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) View.GONE else View.VISIBLE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
