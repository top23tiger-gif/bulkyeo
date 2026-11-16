package com.bulkyeo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 앱을 닫아도 계속 "불켜 / 불꺼" 를 듣는 서비스.
 *
 * - 안드로이드 규칙상 마이크를 쓰는 동안 알림이 항상 표시된다 (몰래 듣지 않는다는 표시).
 * - 가능하면 기기 안에서만 음성을 인식한다 (소리가 인터넷으로 나가지 않음).
 * - 인식한 말은 저장하지 않고, 명령인지 확인한 뒤 바로 버린다.
 */
class ListenService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var useOnDevice = true
    private var listening = false
    private var running = false
    private var lastCommandAt = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var audio: AudioManager
    private lateinit var power: PowerManager
    private val mutedStreams = mutableListOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        power = getSystemService(PowerManager::class.java)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTI_LISTEN, listeningNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            // 안드로이드 14+ 에서는 앱 화면이 떠 있을 때만 마이크 서비스를 시작할 수 있다
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            isRunning = true
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bulkyeo:listen").apply {
                setReferenceCounted(false)
                acquire()
            }
            createRecognizer()
            listen()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        setMuted(false)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ 음성 인식

    private fun createRecognizer() {
        recognizer?.destroy()
        val onDevice = useOnDevice && Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!onDevice) useOnDevice = false
        recognizer = (if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }).apply { setRecognitionListener(listener) }
    }

    private fun listen() {
        if (!running || listening) return
        // 밤(화면 꺼짐)이나 조명 화면일 때만 "띵" 소리를 끈다. 평소에는 소리를 건드리지 않는다.
        setMuted(!power.isInteractive || LightState.isShowing)
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
        } catch (e: Exception) {
            restart(1000)
        }
    }

    private fun restart(delayMs: Long) {
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
            handle(partialResults)
        }

        override fun onResults(results: Bundle?) {
            handle(results)
            restart(200)
        }

        override fun onError(error: Int) {
            when (error) {
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    // 기기 안 인식에 한국어가 없으면 일반 음성 인식으로 바꾼다
                    useOnDevice = false
                    listening = false
                    createRecognizer()
                    restart(500)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    listening = false
                    createRecognizer()
                    restart(1000)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopSelf()
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                ERROR_TOO_MANY_REQUESTS -> restart(3000)
                else -> restart(200)
            }
        }
    }

    private fun handle(bundle: Bundle?) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (t in texts) {
            val on = Commands.parse(t) ?: continue
            val now = SystemClock.elapsedRealtime()
            if (now - lastCommandAt < 1500) return
            lastCommandAt = now
            if (on) lightOn() else lightOff()
            return
        }
    }

    // ------------------------------------------------------------ 조명

    private fun lightOn() {
        if (LightState.isShowing) return
        val intent = Intent(this, LightActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pi = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // 화면이 꺼져 있거나 잠겨 있을 때는 "전체 화면 알림" 으로 조명 화면을 띄운다 (알람 앱과 같은 방식)
        val n = Notification.Builder(this, CH_LIGHT)
            .setSmallIcon(R.drawable.ic_bulb)
            .setContentTitle("불을 켰어요")
            .setContentText("눌러서 조명 화면 열기")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTI_LIGHT, n)
    }

    private fun lightOff() {
        getSystemService(NotificationManager::class.java).cancel(NOTI_LIGHT)
        LightState.activity?.get()?.finish()
    }

    // ------------------------------------------------------------ 알림 / 소리

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "음성 듣는 중", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LIGHT, "조명 켜기", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun listeningNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, ListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH_LISTEN)
            .setSmallIcon(R.drawable.ic_bulb)
            .setContentTitle("불켜: 듣고 있어요")
            .setContentText("\"불켜\" / \"불꺼\" 라고 말하세요")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "중지", stop).build())
            .build()
    }

    private fun setMuted(mute: Boolean) {
        if (mute && mutedStreams.isEmpty()) {
            for (s in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM)) {
                try {
                    if (!audio.isStreamMute(s)) {
                        audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                        mutedStreams.add(s)
                    }
                } catch (_: Exception) {
                }
            }
        } else if (!mute && mutedStreams.isNotEmpty()) {
            for (s in mutedStreams) {
                try {
                    audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
                } catch (_: Exception) {
                }
            }
            mutedStreams.clear()
        }
    }

    companion object {
        const val ACTION_STOP = "com.bulkyeo.app.STOP"
        const val NOTI_LISTEN = 1
        const val NOTI_LIGHT = 2
        private const val CH_LISTEN = "listen"
        private const val CH_LIGHT = "light"

        // API 31 에 추가된 오류 코드 (minSdk 30 이라 숫자로 둔다)
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListenService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }
    }
}
