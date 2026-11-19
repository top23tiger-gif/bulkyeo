package com.bulkyeo.app

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
 * - 코골이는 버리지 않고 한 시간 단위의 긴 파일로 이어 붙인다:
 *   날짜 폴더 > 코골이 > 코골이_2026-11-16_02시.wav (8kHz, 하룻밤 최대 2시간 분량)
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
    fun save(context: Context, frames: List<ShortArray>, rate: Int, start: Calendar = Calendar.getInstance()): File? {
        val now = start
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

    // ------------------------------------------------------------ 코골이 (시간대별 긴 파일)

    private const val SNORE_DIR = "코골이"
    private const val SNORE_RATE = 8000
    private const val SNORE_MAX_BYTES_PER_NIGHT = 2L * 60 * 60 * SNORE_RATE * 2 // 2시간
    private const val SNORE_GAP_SEC = 0.3

    fun snoreDir(night: File) = File(night, SNORE_DIR)

    fun snoreFiles(night: File): List<File> =
        snoreDir(night).listFiles { f -> f.name.endsWith(".wav") }?.sortedBy { it.name } ?: emptyList()

    fun snoreSeconds(context: Context, night: String): Double =
        snoreFiles(File(root(context), night)).sumOf { (it.length() - 44).coerceAtLeast(0) / 2.0 / SNORE_RATE }

    /** 코골이 조각을 그 시간대의 긴 파일 끝에 이어 붙인다 (조각 사이 0.3초 쉼) */
    @Synchronized
    fun appendSnore(context: Context, frames: List<ShortArray>, rate: Int, start: Calendar): Boolean {
        val night = nightKey(start)
        val dir = snoreDir(File(root(context), night))
        dir.mkdirs()
        val used = dir.listFiles()?.sumOf { it.length() } ?: 0L
        if (used >= SNORE_MAX_BYTES_PER_NIGHT) return false

        // 코골이는 낮은 소리라 8kHz 로 충분 (용량 절반)
        val step = if (rate >= 16000) rate / SNORE_RATE else 1
        val total = frames.sumOf { it.size } / step
        val gap = (SNORE_GAP_SEC * SNORE_RATE).toInt()
        val bb = ByteBuffer.allocate((gap + total) * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until gap) bb.putShort(0)
        var acc = 0
        var cnt = 0
        for (f in frames) for (s in f) {
            acc += s.toInt()
            if (++cnt == step) {
                bb.putShort((acc / step).toShort())
                acc = 0
                cnt = 0
            }
        }
        val hour = SimpleDateFormat("HH", Locale.US).format(start.time)
        val f = File(dir, "코골이_${night}_${hour}시.wav")
        RandomAccessFile(f, "rw").use { raf ->
            if (raf.length() < 44) {
                raf.setLength(0)
                raf.write(wavHeader(0, SNORE_RATE))
            }
            raf.seek(raf.length())
            raf.write(bb.array(), 0, bb.position())
            val data = (raf.length() - 44).toInt()
            raf.seek(0)
            raf.write(wavHeader(data, SNORE_RATE))
        }
        return true
    }

    private fun wavHeader(dataBytes: Int, rate: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()

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
        for (f in files(night)) if (moveFile(context, f, "$base/불켜/${night.name}")) n++
        for (f in snoreFiles(night)) if (moveFile(context, f, "$base/불켜/${night.name}/$SNORE_DIR")) n++
        if (files(night).isEmpty() && snoreFiles(night).isEmpty()) night.deleteRecursively()
        return n
    }

    private fun moveFile(context: Context, f: File, relPath: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            f.delete() // 기기 파일에 저장이 끝난 것만 앱에서 지움
            true
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            false
        }
    }

    // ------------------------------------------------------------ 플레이어용 목록

    /** 녹음 하나. [file] 이 있으면 앱 안, 없으면 기기 파일(내 파일) */
    data class Clip(
        val night: String,
        val name: String,
        val uri: Uri,
        val file: File?,
        val durationMs: Long,
        val snore: Boolean = false,
    ) {
        val inApp get() = file != null

        /** "02:10:33" */
        val timeText
            get() = if (snore) name.removeSuffix(".wav").substringAfterLast('_')
            else name.removeSuffix(".wav").substringAfter('_').take(8).replace('-', ':')
    }

    /** 앱 안 + 기기 파일의 녹음을 밤 날짜별로 묶는다 (최근 날짜 먼저, 날짜 안에서는 시간 순) */
    fun catalog(context: Context): Map<String, List<Clip>> {
        val all = ArrayList<Clip>()
        for (night in nights(context)) {
            for (f in files(night)) {
                val ms = ((f.length() - 44).coerceAtLeast(0) / 2.0 / wavRate(f) * 1000).toLong()
                all.add(Clip(night.name, f.name, Uri.fromFile(f), f, ms))
            }
            for (f in snoreFiles(night)) {
                val ms = ((f.length() - 44).coerceAtLeast(0) / 2.0 / SNORE_RATE * 1000).toLong()
                all.add(Clip(night.name, f.name, Uri.fromFile(f), f, ms, snore = true))
            }
        }
        try {
            val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val proj = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DURATION,
            )
            context.contentResolver.query(
                coll, proj, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("%/불켜/%"), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val parts = (c.getString(2) ?: "").trimEnd('/').split('/')
                    val snore = parts.lastOrNull() == SNORE_DIR
                    val night = if (snore) parts.getOrElse(parts.size - 2) { "" } else parts.lastOrNull() ?: ""
                    all.add(Clip(night, name, ContentUris.withAppendedId(coll, id), null, c.getLong(3), snore))
                }
            }
        } catch (_: Exception) {
        }
        return all.groupBy { it.night }
            .mapValues { (_, v) -> v.distinctBy { it.name }.sortedBy { it.name } }
            .toSortedMap(reverseOrder())
    }

    fun wavRate(f: File): Int = try {
        f.inputStream().use { s ->
            val h = ByteArray(28)
            s.read(h)
            (h[24].toInt() and 0xff) or ((h[25].toInt() and 0xff) shl 8) or
                ((h[26].toInt() and 0xff) shl 16) or ((h[27].toInt() and 0xff) shl 24)
        }.takeIf { it > 0 } ?: 16000
    } catch (e: Exception) {
        16000
    }
}
