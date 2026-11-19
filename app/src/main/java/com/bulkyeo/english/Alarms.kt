package com.bulkyeo.english

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/** 폰의 알람 기능(AlarmManager)으로 정해진 시간마다 알림을 예약한다. 서버가 필요 없다. */
object AlarmScheduler {
    const val EXTRA_MINUTES = "minutes"

    private fun pending(c: Context, minutes: Int, flags: Int): PendingIntent? {
        val intent = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_MINUTES, minutes)
        return PendingIntent.getBroadcast(c, minutes, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun nextTrigger(minutes: Int, now: Calendar = Calendar.getInstance()): Long {
        val cal = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!cal.after(now)) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context, minutes: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c, minutes, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = nextTrigger(minutes)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(c: Context, minutes: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        pending(c, minutes, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    fun scheduleAll(c: Context) {
        Prefs.times(c).forEach { schedule(c, it) }
    }
}

/** 예약한 시간이 되면 실행: 다음 문장으로 알림을 띄우고, 내일 같은 시간을 다시 예약한다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES, -1)
        // 그 사이에 삭제된 시간이면 무시
        if (minutes >= 0 && minutes !in Prefs.times(context)) return
        val idx = Prefs.takeNextIndex(context)
        SentenceNotifier.show(context, Sentences.get(context, idx))
        if (minutes >= 0) AlarmScheduler.schedule(context, minutes)
    }
}

/** 폰을 재부팅하거나 앱을 업데이트하면 예약이 지워지므로 다시 등록한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
    }
}

object SentenceNotifier {
    private const val CHANNEL = "daily_sentence"
    private const val NOTIFICATION_ID = 1

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "오늘의 영어 문장", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "정해진 시간마다 영어 문장을 알려줘요"
            nm.createNotificationChannel(ch)
        }
    }

    fun show(c: Context, s: Sentence) {
        ensureChannel(c)
        val open = Intent(c, SentenceActivity::class.java)
            .putExtra(SentenceActivity.EXTRA_INDEX, s.index)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            c, s.index, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.en)
            .setContentText(s.ko)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)

        val picture = Sentences.loadBackground(c, s, 1200)?.let { landscapeCrop(it) }
        if (picture != null) {
            builder.setLargeIcon(picture)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(s.ko)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("${s.ko}\n\n${s.situation}"))
        }

        try {
            NotificationManagerCompat.from(c).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // 알림 권한이 없으면 조용히 넘어간다 (앱 첫 화면에서 안내)
        }
    }

    /** 알림의 큰 그림은 가로로 길어서, 세로 그림의 가운데 부분을 2:1로 잘라 쓴다. */
    private fun landscapeCrop(src: Bitmap): Bitmap {
        val targetH = src.width / 2
        if (src.height <= targetH) return src
        // 세로 그림은 주요 장면이 위~가운데에 있으므로 40% 지점을 중심으로 자른다
        val top = ((src.height * 0.4f) - targetH / 2f).toInt().coerceIn(0, src.height - targetH)
        return Bitmap.createBitmap(src, 0, top, src.width, targetH)
    }
}
