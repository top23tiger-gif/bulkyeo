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
 * - 앱 전용 저장소에만 저장 (다른 앱은 읽을 수 없음, 백업되지 않음).
 * - 사용자가 "내보내기" 를 눌렀을 때만 내 파일 > Recordings > 불켜 로 복사한다.
 * - 날짜는 "밤 기준" (낮 12시 전 녹음은 전날 밤으로 묶음).
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

    /** PCM 16bit 모노 소리를 WAV 로 저장. 저장했으면 true */
    fun save(context: Context, frames: List<ShortArray>, rate: Int): Boolean {
        val now = Calendar.getInstance()
        val dir = File(root(context), nightKey(now))
        dir.mkdirs()
        if ((dir.list()?.size ?: 0) >= MAX_PER_NIGHT) return false
        val name = SimpleDateFormat("HH-mm-ss", Locale.US).format(now.time)
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
        return true
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

    /** 내 파일 > Recordings(또는 Music) > 불켜 > 날짜 로 복사. 복사한 개수 */
    fun export(context: Context, night: File): Int {
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
                n++
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
            }
        }
        return n
    }
}
