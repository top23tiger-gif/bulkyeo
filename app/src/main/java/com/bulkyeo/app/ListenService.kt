package com.bulkyeo.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.os.BatteryManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 앱을 닫아도 "불켜 / 불꺼" 를 듣는 서비스. 배터리를 아끼기 위해:
 *
 *  1. 충전 중일 때만, 그리고 정해 둔 시간에만 마이크를 쓴다. 그 밖에는 완전히 쉰다.
 *  2. 마이크를 쓰는 동안에도 평소에는 "소리 크기" 만 가볍게 본다 (항상 적용).
 *     목소리 정도의 소리가 날 때만 음성 인식을 잠깐 돌린다.
 *  3. 배터리 효율 모드: 소리 센서 샘플링 레이트를 16kHz -> 8kHz 로 낮춘다.
 *  4. 음성 인식은 기기 안에서만 한다 (인터넷 사용 안 함).
 *     "불켜/불꺼" 가 아닌 소리는 (설정이 켜져 있으면) 앱 안에 날짜별로 녹음해 둔다.
 */
class ListenService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var power: PowerManager

    private var worker: Worker? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiverOn = false
    private var lastCommandAt = 0L

    // 배터리 측정 (충전기 없이 들은 구간만)
    private var batStartLevel = -1
    private var batStartTime = 0L
    private var batUnplugged = false

    @Volatile
    private var model: Model? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        power = getSystemService(PowerManager::class.java)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) {
            try {
                startForeground(NOTI_LISTEN, statusNotification("준비 중…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } catch (e: Exception) {
                // 안드로이드 14+ 에서는 앱 화면이 떠 있을 때만 마이크 서비스를 시작할 수 있다
                stopSelf()
                return START_NOT_STICKY
            }
        }
        isRunning = true
        registerPowerReceiver()
        evaluate()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isActive = false
        val w = worker
        stopWorker()
        if (receiverOn) {
            unregisterReceiver(powerReceiver)
            receiverOn = false
        }
        getSystemService(AlarmManager::class.java).cancel(checkIntent())
        // 듣기 스레드가 완전히 끝났을 때만 모델을 닫는다
        if (w == null || !w.isAlive) {
            model?.close()
            model = null
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ 언제 들을지 결정

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            evaluate()
        }
    }

    private fun registerPowerReceiver() {
        if (receiverOn) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerReceiver, f)
        }
        receiverOn = true
    }

    private fun evaluate() {
        val plugged = Prefs.isPluggedIn(this)
        val chargeOk = !prefs.chargingOnly || plugged
        val timeOk = prefs.inSchedule()
        val shouldListen = chargeOk && timeOk

        if (shouldListen) {
            // 효율 모드가 바뀌었으면 새 샘플링 레이트로 다시 시작
            if (worker != null && worker?.rate != currentRate()) stopWorker()
            startWorker()
        } else {
            stopWorker()
        }
        if (plugged) batUnplugged = false // 측정 중에 충전기를 꽂으면 이번 측정은 무효
        isActive = shouldListen

        // 일주일마다 녹음을 기기 파일로 옮긴다 (지금 듣는 밤은 제외)
        val skip = if (shouldListen) Recordings.nightKey() else null
        Thread {
            try {
                Recordings.weeklyMoveIfDue(this, prefs, skip)
            } catch (_: Exception) {
            }
        }.start()

        val text = when {
            shouldListen -> "듣고 있어요" + (if (prefs.ecoMode) " (효율 모드)" else "") + " · \"불켜\" / \"불꺼\""
            !timeOk -> "쉬는 중 · ${Prefs.fmt(prefs.startMin)}부터 들어요"
            else -> "쉬는 중 · 충전기를 꽂으면 들어요"
        }
        getSystemService(NotificationManager::class.java).notify(NOTI_LISTEN, statusNotification(text))
        scheduleNextCheck()
    }

    /** 시간 설정의 시작/끝 시각에 다시 확인하도록 예약 (정확한 알람 권한 없이, 몇 분 늦을 수 있음) */
    private fun scheduleNextCheck() {
        val am = getSystemService(AlarmManager::class.java)
        val pi = checkIntent()
        am.cancel(pi)
        val at = prefs.nextBoundaryMillis() ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun checkIntent(): PendingIntent = PendingIntent.getService(
        this, 3, Intent(this, ListenService::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun currentRate() = if (prefs.ecoMode) ECO_RATE else RATE

    private fun startWorker() {
        if (worker != null) return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bulkyeo:listen").apply {
            setReferenceCounted(false)
            acquire()
        }
        worker = Worker(currentRate()).also { it.start() }
        // 배터리 측정 시작
        batStartLevel = batteryLevel()
        batStartTime = System.currentTimeMillis()
        batUnplugged = !Prefs.isPluggedIn(this)
    }

    private fun batteryLevel(): Int =
        getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** 충전기 없이 30분 넘게 들었으면 "몇 시간에 몇 % 썼는지" 기록 */
    private fun finishBatteryMeasure() {
        if (batStartLevel < 0) return
        val hours = (System.currentTimeMillis() - batStartTime) / 3_600_000.0
        val used = batStartLevel - batteryLevel()
        if (batUnplugged && !Prefs.isPluggedIn(this) && hours >= 0.5 && used >= 0) {
            val perHour = used / hours
            prefs.lastBatteryReport = "%s · %.1f시간 동안 %d%% 사용 (시간당 약 %.1f%%, 9시간이면 약 %.0f%%)%s".format(
                Recordings.nightKey(), hours, used, perHour, perHour * 9, if (prefs.ecoMode) " · 효율 모드" else ""
            )
        }
        batStartLevel = -1
    }

    private fun stopWorker() {
        if (worker != null) finishBatteryMeasure()
        worker?.let {
            it.running = false
            it.interrupt()
            try {
                it.join(1500)
            } catch (_: InterruptedException) {
            }
        }
        worker = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------ 듣기 (별도 스레드)

    @Synchronized
    private fun loadModel(): Model? {
        model?.let { return it }
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val dir = ModelFiles.ensure(this)
            Model(dir.absolutePath).also { model = it }
        } catch (e: Exception) {
            main.post {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTI_LISTEN, statusNotification("음성 모델을 불러오지 못했어요"))
            }
            null
        }
    }

    private inner class Worker(val rate: Int) : Thread("bulkyeo-listen") {
        @Volatile
        var running = true

        @SuppressLint("MissingPermission")
        override fun run() {
            val m = loadModel() ?: return
            val rec = try {
                Recognizer(m, rate.toFloat())
            } catch (e: Exception) {
                return
            }
            val frame = rate / 10 // 0.1초
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val ar = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, frame * 2 * 4)
                )
            } catch (e: Exception) {
                rec.close()
                return
            }
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                rec.close()
                return
            }

            // 말 시작 부분을 놓치지 않도록 최근 0.8초 소리를 계속 기억해 둔다
            val ring = ArrayDeque<ShortArray>()
            var noiseFloor = -60.0
            var inSpeech = false
            var speechFrames = 0
            var quietFrames = 0
            val segment = ArrayList<ShortArray>() // 녹음용: 소리가 난 구간

            try {
                ar.startRecording()
                while (running) {
                    val buf = ShortArray(frame)
                    var n = 0
                    while (n < frame && running) {
                        val r = ar.read(buf, n, frame - n)
                        if (r <= 0) break
                        n += r
                    }
                    if (!running || n < frame) {
                        if (n <= 0) Thread.sleep(200)
                        continue
                    }
                    val db = levelDb(buf)

                    if (!inSpeech) {
                        // 조용할 때: 소리 크기만 확인 (아주 가벼움)
                        ring.addLast(buf)
                        if (ring.size > PRE_ROLL) ring.removeFirst()
                        noiseFloor = if (db < noiseFloor) db else noiseFloor + 0.02 * (db - noiseFloor)
                        if (db > max(noiseFloor + TRIGGER_DB, MIN_TRIGGER_DB)) {
                            inSpeech = true
                            speechFrames = 0
                            quietFrames = 0
                            rec.reset()
                            segment.clear()
                            for (f in ring) {
                                rec.acceptWaveForm(f, f.size)
                                segment.add(f)
                            }
                            ring.clear()
                        }
                    } else {
                        // 목소리가 들릴 때만: 음성 인식
                        speechFrames++
                        segment.add(buf)
                        quietFrames = if (db > noiseFloor + TRIGGER_DB / 2) 0 else quietFrames + 1
                        val done = rec.acceptWaveForm(buf, n)
                        var text = if (done) textOf(rec.result, "text") else textOf(rec.partialResult, "partial")
                        if (handle(text)) {
                            // 명령은 녹음하지 않는다
                            inSpeech = false
                            segment.clear()
                            rec.reset()
                            continue
                        }
                        if (quietFrames >= END_QUIET || speechFrames >= MAX_SPEECH) {
                            text = textOf(rec.finalResult, "text")
                            if (!handle(text) && prefs.saveRecordings && speechFrames >= MIN_SAVE) {
                                try {
                                    Recordings.save(this@ListenService, segment, rate)
                                } catch (_: Exception) {
                                }
                            }
                            segment.clear()
                            inSpeech = false
                            rec.reset()
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } catch (_: Exception) {
            } finally {
                try {
                    ar.stop()
                } catch (_: Exception) {
                }
                ar.release()
                rec.close()
            }
        }
    }

    private fun levelDb(buf: ShortArray): Double {
        var sum = 0.0
        for (s in buf) sum += s.toDouble() * s
        val rms = sqrt(sum / buf.size)
        return 20 * log10(max(rms, 1.0) / 32768.0)
    }

    private fun textOf(json: String, key: String): String = try {
        JSONObject(json).optString(key, "")
    } catch (e: Exception) {
        ""
    }

    /** 명령이면 실행하고 true. 인식된 말은 저장하지 않는다. */
    private fun handle(text: String): Boolean {
        if (text.isBlank()) return false
        val on = Commands.parse(text) ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastCommandAt < 1500) return true
        lastCommandAt = now
        main.post { if (on) lightOn() else lightOff() }
        return true
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
        // 화면이 꺼져 있거나 잠겨 있을 때 "전체 화면 알림" 으로 조명 화면을 띄운다 (알람 앱과 같은 방식)
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

    // ------------------------------------------------------------ 알림

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "음성 듣기 상태", NotificationManager.IMPORTANCE_LOW).apply {
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

    private fun statusNotification(text: String): Notification {
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
            .setContentTitle("불켜")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "중지", stop).build())
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.bulkyeo.app.STOP"
        const val ACTION_CHECK = "com.bulkyeo.app.CHECK"
        const val NOTI_LISTEN = 1
        const val NOTI_LIGHT = 2
        private const val CH_LISTEN = "listen"
        private const val CH_LIGHT = "light"

        private const val RATE = 16000          // 보통 모드
        private const val ECO_RATE = 8000       // 배터리 효율 모드
        private const val PRE_ROLL = 8          // 말 시작 전 0.8초 기억
        private const val TRIGGER_DB = 12.0     // 주변 소음보다 이만큼 크면 "목소리" 로 봄
        private const val MIN_TRIGGER_DB = -50.0
        private const val END_QUIET = 10        // 1초 조용하면 끝
        private const val MAX_SPEECH = 50       // 최대 5초까지만 인식
        private const val MIN_SAVE = 3          // 0.3초보다 짧은 소리는 녹음하지 않음

        /** 서비스가 켜져 있음 (쉬는 중 포함) */
        @Volatile
        var isRunning = false
            private set

        /** 지금 실제로 마이크로 듣는 중 */
        @Volatile
        var isActive = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListenService::class.java))
        }

        /** 설정이 바뀌었을 때 다시 판단하게 한다 */
        fun recheck(context: Context) {
            if (isRunning) {
                context.startService(Intent(context, ListenService::class.java).setAction(ACTION_CHECK))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenService::class.java))
        }
    }
}

/** 앱에 들어 있는 음성 모델을 처음 한 번 앱 전용 저장소로 꺼내 둔다 */
object ModelFiles {
    private const val ASSET_DIR = "model-ko"
    private const val VERSION = "ko-0.22"

    @Synchronized
    fun ensure(context: Context): File {
        val dir = File(context.noBackupFilesDir, ASSET_DIR)
        val mark = File(dir, ".ready-$VERSION")
        if (mark.exists()) return dir
        dir.deleteRecursively()
        copy(context, ASSET_DIR, dir)
        mark.createNewFile()
        return dir
    }

    private fun copy(context: Context, assetPath: String, out: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            out.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                out.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
        } else {
            out.mkdirs()
            for (c in children) copy(context, "$assetPath/$c", File(out, c))
        }
    }
}
