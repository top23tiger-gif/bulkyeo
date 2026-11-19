package com.bulkyeo.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Calendar

/** 배터리 절약 설정 (앱 안에만 저장, 백업되지 않음) */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var chargingOnly: Boolean
        get() = sp.getBoolean("chargingOnly", true)
        set(v) = sp.edit().putBoolean("chargingOnly", v).apply()

    var scheduleOn: Boolean
        get() = sp.getBoolean("scheduleOn", true)
        set(v) = sp.edit().putBoolean("scheduleOn", v).apply()

    /** 하루 중 분 (0..1439). 기본: 밤 9시 ~ 아침 6시 */
    var startMin: Int
        get() = sp.getInt("startMin", 21 * 60)
        set(v) = sp.edit().putInt("startMin", v).apply()

    var endMin: Int
        get() = sp.getInt("endMin", 6 * 60)
        set(v) = sp.edit().putInt("endMin", v).apply()

    /** 배터리 효율 모드: 소리 센서 샘플링 레이트를 16kHz -> 8kHz 로 낮춘다 */
    var ecoMode: Boolean
        get() = sp.getBoolean("ecoMode", false)
        set(v) = sp.edit().putBoolean("ecoMode", v).apply()

    /** "불켜/불꺼" 가 아닌 소리를 날짜별로 녹음해 둔다 */
    var saveRecordings: Boolean
        get() = sp.getBoolean("saveRecordings", true)
        set(v) = sp.edit().putBoolean("saveRecordings", v).apply()

    /** 마지막으로 측정한 배터리 사용 기록 (충전기 없이 들었던 구간) */
    var lastBatteryReport: String
        get() = sp.getString("lastBatteryReport", "") ?: ""
        set(v) = sp.edit().putString("lastBatteryReport", v).apply()

    /** 마지막으로 녹음을 기기 파일로 옮긴 시각 */
    var lastExportAt: Long
        get() = sp.getLong("lastExportAt", 0L)
        set(v) = sp.edit().putLong("lastExportAt", v).apply()

    /** 구글/삼성 음성 인식을 함께 쓸지 (처음 버전에서 잘 되던 인식기). 소리가 날 때만 잠깐 사용 */
    var useSystemRecognizer: Boolean
        get() = sp.getBoolean("useSystem", true)
        set(v) = sp.edit().putBoolean("useSystem", v).apply()

    /** 테스트 모드가 끝나는 시각 (이 시각 전까지는 시간·충전 조건을 무시하고 듣는다) */
    var testUntil: Long
        get() = sp.getLong("testUntil", 0L)
        set(v) = sp.edit().putLong("testUntil", v).apply()

    val inTestMode: Boolean
        get() = System.currentTimeMillis() < testUntil

    /** 코골이를 시간대별 긴 파일로 모아 둘지 */
    var saveSnoring: Boolean
        get() = sp.getBoolean("saveSnoring", true)
        set(v) = sp.edit().putBoolean("saveSnoring", v).apply()

    /** 이미 Documents 로 저장한 잠꼬대 기록 (밤 날짜) */
    var exportedTranscripts: Set<String>
        get() = sp.getStringSet("exportedTranscripts", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("exportedTranscripts", HashSet(v)).apply()

    fun inSchedule(now: Calendar = Calendar.getInstance()): Boolean {
        if (!scheduleOn) return true
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = startMin
        val e = endMin
        return when {
            s == e -> true
            s < e -> m in s until e
            else -> m >= s || m < e // 자정을 넘기는 경우 (예: 22:00 ~ 07:00)
        }
    }

    /** 다음에 시간 조건이 바뀌는 시각 (밀리초). 시간 설정을 안 쓰면 null */
    fun nextBoundaryMillis(): Long? {
        val t = testUntil
        val test = if (t > System.currentTimeMillis()) t + 1000 else null
        val sched = scheduleBoundary()
        return listOfNotNull(test, sched).minOrNull()
    }

    private fun scheduleBoundary(): Long? {
        if (!scheduleOn || startMin == endMin) return null
        val now = Calendar.getInstance()
        var best: Long? = null
        for (target in intArrayOf(startMin, endMin)) {
            val c = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, target / 60)
                set(Calendar.MINUTE, target % 60)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            if (best == null || c.timeInMillis < best) best = c.timeInMillis
        }
        return best
    }

    companion object {
        fun isPluggedIn(context: Context): Boolean {
            // 배터리 보호(85%)로 충전이 멈춰 있어도 "꽂혀 있음" 으로 본다
            val b: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            return (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        }

        fun fmt(min: Int): String {
            val h = min / 60
            val m = min % 60
            val ampm = if (h < 12) "오전" else "오후"
            val h12 = if (h % 12 == 0) 12 else h % 12
            return if (m == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${m}분"
        }
    }
}
