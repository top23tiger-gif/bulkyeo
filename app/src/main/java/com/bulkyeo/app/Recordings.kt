package com.bulkyeo.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "불켜/불꺼" 가 아닌 소리를 날짜별 폴더에 WAV 로 모아 둔다.
 *
 * - 먼저 앱 전용 저장소에 모아 둔다 (다른 앱은 읽을 수 없음, 백업되지 않음).
 * - 일주일마다 이 기기의 파일(내 파일 > Recordings > 불켜 > 날짜 폴더)로 옮기고,
 *   옮기기에 성공한 파일은 앱 안에서 지운다.
 * - 폴더 이름: 날짜 (밤 기준, 낮 12시 전 녹음은 전날 밤 폴더)
 * - 파일 이름: 실제 녹음한 날짜와 시간 (예: 2026-11-17_02-10-33.wav)
 */
object Recordings {
    private const val DIR = "recordings"
    private const val MAX_PER_NIGHT = 300

    fun root(context: Context) = File(context.noBackupFilesDir, DIR)

    fun nightKey(c: Calendar = Calendar.getInstance()): String {
        val d = c.clone() as Calendar
        if (d.get(Calendar.HOUR_OF_DAY) < 12) d.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d.time)
    }

    /** PCM 16bit 모노 소리를 WAV 로 저장. 저장한 파일 (못 했으면 null) */
    fun save(context: Context, frames: List<ShortArray>, rate: Int): File? {
        val now = Calendar.getInstance()
        val dir = File(root(context), nightKey(now))
        dir.mkdirs()
        if ((dir.list()?.size ?: 0) >= MAX_PER_NIGHT) return null
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now.time)
        var f = File(dir, "$name.wav")
        var i = 1
        while (f.exists()) f = File(dir, "$name-${i++}.wav")

        val samples = frames.sumOf { it.size }
        val dataBytes = samples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        FileOutputStream(f).use { out ->
            out.write(header.array())
            val bb = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (fr in frames) for (s in fr) bb.putShort(s)
            out.write(bb.array())
        }
        return f
    }

    /** 날짜 폴더 목록 (최근 날짜가 위로) */
    fun nights(context: Context): List<File> =
        root(context).listFiles { f -> f.isDirectory }?.sortedByDescending { it.name } ?: emptyList()

    fun files(night: File): List<File> =
        night.listFiles { f -> f.name.endsWith(".wav") }?.sortedBy { it.name } ?: emptyList()

    fun sizeText(night: File): String {
        val bytes = files(night).sumOf { it.length() }
        return if (bytes > 1024 * 1024) "%.1fMB".format(bytes / 1024.0 / 1024.0) else "${bytes / 1024}KB"
    }

    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * 일주일이 지났으면 모인 녹음을 모두 기기 파일로 옮긴다.
     * [skipNight] 는 지금 녹음 중인 밤 (옮기지 않음).
     */
    fun weeklyMoveIfDue(context: Context, prefs: Prefs, skipNight: String?): Int {
        val now = System.currentTimeMillis()
        if (prefs.lastExportAt == 0L) prefs.lastExportAt = now // 설치 후 첫 주 시작
        if (now - prefs.lastExportAt < WEEK_MS) return 0
        val moved = moveAll(context, skipNight)
        prefs.lastExportAt = now
        return moved
    }

    /** 모든 날짜 폴더를 기기 파일로 옮긴다. 옮긴 개수 */
    @Synchronized
    fun moveAll(context: Context, skipNight: String?): Int {
        var n = 0
        for (night in nights(context)) {
            if (night.name == skipNight) continue
            n += moveNight(context, night)
        }
        return n
    }

    /** 한 날짜를 내 파일 > Recordings(또는 Music) > 불켜 > 날짜 로 옮긴다. 성공한 파일만 앱에서 지운다. */
    @Synchronized
    fun moveNight(context: Context, night: File): Int {
        val base = if (Build.VERSION.SDK_INT >= 31) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC
        var n = 0
        for (f in files(night)) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "$base/불켜/${night.name}")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            ) ?: continue
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                f.delete() // 기기 파일에 저장이 끝난 것만 앱에서 지움
                n++
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
            }
        }
        if (files(night).isEmpty()) night.deleteRecursively()
        return n
    }
}
