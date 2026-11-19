package com.bulkyeo.app

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 잠꼬대로 한 말을 밤 날짜별 텍스트로 정리한다.
 *
 * 줄 형식:  02:10:33  (4.2초)  어디 가는 거야
 *
 * - 밤 동안은 앱 안에 적어 두고,
 * - 그 밤이 끝나면(아침에 듣기가 멈출 때) 내 파일 > Documents > 불켜 > 잠꼬대_2026-11-16.txt 로 저장한다.
 */
object Transcripts {
    private const val DIR = "transcripts"

    private fun dir(context: Context) = File(context.noBackupFilesDir, DIR).apply { mkdirs() }

    fun file(context: Context, night: String) = File(dir(context), "$night.txt")

    @Synchronized
    fun append(context: Context, start: Calendar, seconds: Double, text: String) {
        val night = Recordings.nightKey(start)
        val f = file(context, night)
        if (!f.exists()) f.writeText(header(night))
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(start.time)
        f.appendText("$time  (${"%.1f".format(seconds)}초)  $text\n")
    }

    /** 방금 적은 줄 지우기 (그 소리가 사실 "불켜" 명령의 앞부분이었을 때) */
    @Synchronized
    fun removeLine(context: Context, night: String, time: String) {
        val f = file(context, night)
        if (!f.exists()) return
        val lines = f.readLines().filterNot { it.startsWith("$time  ") }
        f.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    /** "02:10:33" -> 한 말 (플레이어에서 녹음 옆에 보여주기용) */
    fun lines(context: Context, night: String): Map<String, String> {
        val f = file(context, night)
        if (!f.exists()) return emptyMap()
        return f.readLines()
            .filter { it.length > 10 && it[2] == ':' && it[5] == ':' }
            .associate { it.substring(0, 8) to it.substringAfter("초)  ", "").trim() }
    }

    /**
     * 끝난 밤의 기록을 내 파일 > Documents > 불켜 로 저장 (밤마다 한 번).
     * [skipNight] 는 아직 진행 중인 밤.
     */
    @Synchronized
    fun exportFinished(context: Context, prefs: Prefs, skipNight: String?): Int {
        val done = prefs.exportedTranscripts.toMutableSet()
        var n = 0
        val nights = (dir(context).listFiles { x -> x.name.endsWith(".txt") }?.map { it.nameWithoutExtension } ?: emptyList()) +
            Recordings.nights(context).map { it.name }
        for (night in nights.distinct()) {
            if (night == skipNight || night in done) continue
            val f = file(context, night)
            if (!f.exists()) f.writeText(header(night) + "(잠꼬대 없음)\n")
            // 코골이 요약 한 줄
            val snore = Recordings.snoreSeconds(context, night)
            if (snore > 0) {
                val m = (snore / 60).toInt()
                val sec = (snore % 60).toInt()
                f.appendText("\n코골이: 총 ${m}분 ${sec}초 (시간대별 소리: 녹음 > 불켜 > $night > 코골이)\n")
            }
            if (writeToDocuments(context, night, f)) {
                done.add(night)
                n++
            }
        }
        prefs.exportedTranscripts = done
        return n
    }

    private fun writeToDocuments(context: Context, night: String, f: File): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "잠꼬대_$night.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/불켜")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        ) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            false
        }
    }

    private fun header(night: String): String {
        val p = night.split("-")
        val title = if (p.size == 3) "${p[0]}년 ${p[1].toInt()}월 ${p[2].toInt()}일 밤" else night
        return "$title 잠꼬대 기록\n시간      길이     한 말\n------------------------------------------\n"
    }
}
