package com.bulkyeo.english

import android.content.Context

/** 알림 시간과 "다음에 보여줄 문장 번호"를 폰 안에 저장한다. */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_TIMES = "alarm_times"
    private const val KEY_NEXT = "next_index"
    private const val KEY_LAST = "last_index"
    private const val KEY_INIT = "initialized"

    /** 처음 설치했을 때 기본 알림 시간: 오전 9시, 오후 1시, 오후 8시 */
    private val DEFAULT_TIMES = listOf(9 * 60, 13 * 60, 20 * 60)

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 하루 중 분(0~1439) 단위로 저장된 알림 시간 목록 */
    fun times(c: Context): List<Int> {
        val p = sp(c)
        if (!p.getBoolean(KEY_INIT, false)) {
            setTimes(c, DEFAULT_TIMES)
            p.edit().putBoolean(KEY_INIT, true).apply()
            return DEFAULT_TIMES
        }
        val raw = p.getString(KEY_TIMES, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    }

    fun setTimes(c: Context, times: List<Int>) {
        sp(c).edit().putString(KEY_TIMES, times.distinct().sorted().joinToString(",")).apply()
    }

    /** 알림이 울릴 때마다 다음 문장으로 넘어간다. 마지막 문장 다음엔 처음으로. */
    fun takeNextIndex(c: Context): Int {
        val p = sp(c)
        val total = Sentences.all(c).size
        val idx = Math.floorMod(p.getInt(KEY_NEXT, 0), total)
        p.edit().putInt(KEY_NEXT, (idx + 1) % total).putInt(KEY_LAST, idx).apply()
        return idx
    }

    /** 가장 최근에 알림으로 받은 문장 (아직 없으면 첫 문장) */
    fun lastIndex(c: Context): Int = sp(c).getInt(KEY_LAST, 0)

    fun setLastIndex(c: Context, idx: Int) {
        sp(c).edit().putInt(KEY_LAST, idx).apply()
    }
}

fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return if (m == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${m}분"
}
