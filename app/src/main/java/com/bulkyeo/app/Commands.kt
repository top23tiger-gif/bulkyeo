package com.bulkyeo.app

import java.lang.ref.WeakReference

/** 인식된 문장을 "켜기 / 끄기" 명령으로 바꾼다. 인식된 문장은 어디에도 저장하지 않는다. */
object Commands {
    private val LIGHT_WORDS = listOf("불", "라이트", "조명", "전등")
    private val ON_WORDS = listOf("켜", "키라", "키어", "밝게")
    private val OFF_WORDS = listOf("꺼", "끄라", "끄어", "어둡게")

    /** true = 켜기, false = 끄기, null = 명령 아님 */
    fun parse(text: String): Boolean? {
        val s = text.replace(" ", "")
        val hasLightWord = LIGHT_WORDS.any { s.contains(it) } || s.length <= 3
        if (!hasLightWord) return null
        if (OFF_WORDS.any { s.contains(it) }) return false
        if (ON_WORDS.any { s.contains(it) }) return true
        return null
    }
}

/** 조명 화면이 지금 떠 있는지 서비스와 화면이 공유하는 상태 (같은 앱 안에서만 사용) */
object LightState {
    var activity: WeakReference<LightActivity>? = null

    val isShowing: Boolean
        get() = activity?.get()?.let { !it.isFinishing && !it.isDestroyed } == true
}
