package com.bulkyeo.app

import java.lang.ref.WeakReference

/** 인식된 문장을 "켜기 / 끄기" 명령으로 바꾼다. 인식된 문장은 어디에도 저장하지 않는다. */
object Commands {
    // "불" 처럼 들릴 수 있는 말 + "켜/꺼" 처럼 들릴 수 있는 말 (인식기가 조금 틀려도 알아듣도록)
    private const val LIGHT = "(불|뿔|풀|볼|전등|조명|라이트)"
    private val ON = Regex("$LIGHT(좀|을|를)?(켜|껴|켰|키|커|켜줘|켜요)")
    private val OFF = Regex("$LIGHT(좀|을|를)?(꺼|껐|끄|거|꺼줘|꺼요)")
    private val ON_ALONE = setOf("켜", "켜줘", "켜요", "켜라", "불켜", "밝게")
    private val OFF_ALONE = setOf("꺼", "꺼줘", "꺼요", "꺼라", "불꺼", "어둡게")

    /** true = 켜기, false = 끄기, null = 명령 아님 */
    fun parse(text: String): Boolean? {
        val s = text.replace(" ", "").replace(".", "").replace("!", "")
        if (s.isEmpty()) return null
        if (s in OFF_ALONE || OFF.containsMatchIn(s)) return false
        if (s in ON_ALONE || ON.containsMatchIn(s)) return true
        return null
    }
}

/** 조명 화면이 지금 떠 있는지 서비스와 화면이 공유하는 상태 (같은 앱 안에서만 사용) */
object LightState {
    var activity: WeakReference<LightActivity>? = null

    /** 설정 화면 ("불꺼" 때 같이 닫는다) */
    var main: WeakReference<MainActivity>? = null

    @Volatile
    var mainVisible = false

    val isShowing: Boolean
        get() = activity?.get()?.let { !it.isFinishing && !it.isDestroyed } == true
}
