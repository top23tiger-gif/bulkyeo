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

    /** 하루 중 분 (0..1439). 기본: 밤 10시 ~ 아침 7시 */
    var startMin: Int
        get() = sp.getInt("startMin", 22 * 60)
        set(v) = sp.edit().putInt("startMin", v).apply()

    var endMin: Int
        get() = sp.getInt("endMin", 7 * 60)
        set(v) = sp.edit().putInt("endMin", v).apply()

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
