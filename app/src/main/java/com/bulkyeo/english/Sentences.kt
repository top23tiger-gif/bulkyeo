package com.bulkyeo.english

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.io.File

data class Sentence(
    val index: Int,
    val en: String,
    val ko: String,
    val situation: String,
    val image: String,
)

/** 앱 안(assets)에 들어 있는 문장과 그림을 읽는다. 인터넷/API는 쓰지 않는다. */
object Sentences {
    @Volatile
    private var cache: List<Sentence>? = null

    fun all(context: Context): List<Sentence> {
        cache?.let { return it }
        val json = context.assets.open("sentences.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Sentence(i, o.getString("en"), o.getString("ko"), o.getString("situation"), o.getString("image"))
        }
        cache = list
        return list
    }

    fun get(context: Context, index: Int): Sentence {
        val list = all(context)
        return list[Math.floorMod(index, list.size)]
    }

    /** 엄마가 직접 고른 사진(배경 바꾸기)이 저장되는 파일 */
    fun customBackgroundFile(context: Context, s: Sentence): File =
        File(File(context.filesDir, "backgrounds").apply { mkdirs() }, "sentence_${s.index}.jpg")

    /** 배경 그림을 읽는다. 직접 고른 사진이 있으면 그걸 우선 쓴다. */
    fun loadBackground(context: Context, s: Sentence, maxSize: Int = 1600): Bitmap? {
        val custom = customBackgroundFile(context, s)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            if (custom.exists()) {
                BitmapFactory.decodeFile(custom.path, opts)
                opts.inSampleSize = sampleSize(opts, maxSize)
                opts.inJustDecodeBounds = false
                BitmapFactory.decodeFile(custom.path, opts)
            } else {
                val name = "images/${s.image}.jpg"
                context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
                opts.inSampleSize = sampleSize(opts, maxSize)
                opts.inJustDecodeBounds = false
                context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sampleSize(o: BitmapFactory.Options, maxSize: Int): Int {
        var size = 1
        while (o.outWidth / (size * 2) >= maxSize || o.outHeight / (size * 2) >= maxSize) size *= 2
        return size
    }
}
