package com.bulkyeo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 잠자리용 음성 조명 앱.
 *
 * - 앱을 켜 두면 화면은 아주 어두운 검정 상태로 대기하며 계속 음성을 듣는다.
 * - "불켜" → 화면 전체를 흰색 + 최대 밝기로 (기기에 플래시가 있으면 플래시도 켬)
 * - "불꺼" → 다시 어두운 검정 화면으로
 * - 화면을 두 번 톡톡 눌러도 켜고 끌 수 있다 (음성이 안 될 때 대비).
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var resumed = false
    private var lightOn = false
    private var lastCommandAt = 0L
    private var lastTapAt = 0L

    private lateinit var root: FrameLayout
    private lateinit var hint: TextView
    private lateinit var status: TextView

    private var cameraManager: CameraManager? = null
    private var torchId: String? = null

    private lateinit var audioManager: AudioManager
    private val mutedStreams = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        root = FrameLayout(this)
        hint = TextView(this).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
        }
        status = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = 48 }
        )
        root.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTapAt < 400) setLight(!lightOn)
            lastTapAt = now
        }
        setContentView(root)

        findTorch()
        applyLight()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        hideSystemBars()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognizer()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        stopRecognizer()
        unmuteBeeps()
    }

    override fun onDestroy() {
        super.onDestroy()
        setTorch(false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startRecognizer()
            } else {
                status.text = "마이크 권한이 필요해요. 설정 > 애플리케이션 > 불켜 > 권한에서 허용해 주세요."
            }
        }
    }

    // ---------------------------------------------------------------- 조명

    private fun setLight(on: Boolean) {
        lightOn = on
        applyLight()
    }

    private fun applyLight() {
        val lp = window.attributes
        if (lightOn) {
            root.setBackgroundColor(Color.WHITE)
            hint.setTextColor(Color.rgb(200, 200, 200))
            status.setTextColor(Color.rgb(200, 200, 200))
            hint.text = "\"불꺼\" 라고 말하면 꺼져요"
            lp.screenBrightness = 1.0f
        } else {
            root.setBackgroundColor(Color.BLACK)
            // 밤에 눈부시지 않게 아주 어두운 글씨
            hint.setTextColor(Color.rgb(45, 45, 45))
            status.setTextColor(Color.rgb(35, 35, 35))
            hint.text = "\"불켜\" 라고 말하면 켜져요"
            lp.screenBrightness = 0.01f
        }
        window.attributes = lp
        setTorch(lightOn)
    }

    private fun findTorch() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraManager = cm
        torchId = try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            cameraManager?.setTorchMode(id, on)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- 음성 인식

    private fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "이 기기에서 음성 인식을 쓸 수 없어요. (Google 앱 / 음성 인식 설정 확인)"
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
        }
        muteBeeps()
        listen()
    }

    private fun stopRecognizer() {
        handler.removeCallbacksAndMessages(null)
        listening = false
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    private fun listen() {
        if (!resumed || listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            recognizer?.startListening(intent)
            listening = true
            status.text = "듣고 있어요…"
        } catch (e: Exception) {
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        listening = false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private val listener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            handleTexts(partialResults)
        }

        override fun onResults(results: Bundle?) {
            handleTexts(results)
            scheduleRestart(200)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    // 인식기가 꼬였을 때는 새로 만든다
                    listening = false
                    recognizer?.destroy()
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity).apply {
                        setRecognitionListener(this@MainActivity.listener)
                    }
                    scheduleRestart(1000)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listening = false
                    status.text = "마이크 권한이 필요해요."
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> scheduleRestart(3000)
                else -> scheduleRestart(200) // 말이 없어서 끝난 경우 등: 바로 다시 듣기
            }
        }
    }

    private fun handleTexts(bundle: Bundle?) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (t in texts) {
            val cmd = parseCommand(t) ?: continue
            val now = SystemClock.elapsedRealtime()
            // 같은 말이 부분결과/최종결과로 두 번 들어오는 것을 막는다
            if (now - lastCommandAt < 1500) return
            lastCommandAt = now
            setLight(cmd)
            return
        }
    }

    /** true = 켜기, false = 끄기, null = 명령 아님 */
    private fun parseCommand(text: String): Boolean? {
        val s = text.replace(" ", "")
        val hasLightWord = LIGHT_WORDS.any { s.contains(it) } || s.length <= 3
        if (!hasLightWord) return null
        if (OFF_WORDS.any { s.contains(it) }) return false
        if (ON_WORDS.any { s.contains(it) }) return true
        return null
    }

    // 음성 인식이 다시 시작될 때마다 나는 "띵" 소리를 끈다 (앱을 벗어나면 원래대로)
    private fun muteBeeps() {
        if (mutedStreams.isNotEmpty()) return
        for (stream in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM)) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                mutedStreams.add(stream)
            } catch (_: Exception) {
            }
        }
    }

    private fun unmuteBeeps() {
        for (stream in mutedStreams) {
            try {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            } catch (_: Exception) {
            }
        }
        mutedStreams.clear()
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val REQ_AUDIO = 1
        private val LIGHT_WORDS = listOf("불", "라이트", "조명", "전등")
        private val ON_WORDS = listOf("켜", "켜줘", "켜라", "키라", "키어", "밝게")
        private val OFF_WORDS = listOf("꺼", "꺼줘", "꺼라", "끄라", "끄어", "어둡게")
    }
}
