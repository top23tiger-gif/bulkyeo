package com.bulkyeo.app

import android.content.Context
import org.tensorflow.lite.task.audio.classifier.AudioClassifier

/**
 * AI 소리 분류기 (구글 YAMNet, 기기 안에서만 동작).
 * 소리 조각을 듣고 "말소리(잠꼬대)" 인지 "코골이/숨소리" 인지 점수를 매긴다.
 */
class SoundClassifier(context: Context) {

    private val classifier: AudioClassifier = AudioClassifier.createFromFileAndOptions(
        context, "yamnet.tflite",
        AudioClassifier.AudioClassifierOptions.builder()
            .setMaxResults(521)
            .setScoreThreshold(0f)
            .build()
    )
    private val tensor = classifier.createInputTensorAudio()
    private val window = classifier.requiredInputBufferSize.toInt() // 15600 = 0.975초 (16kHz)

    /** 0~1 점수. speech = 말소리, snore = 코골이·숨소리 */
    data class Verdict(val speech: Float, val snore: Float)

    @Synchronized
    fun classify(frames: List<ShortArray>, rate: Int): Verdict {
        val pcm = to16k(frames, rate)
        var speechMax = 0f
        var snoreMax = 0f
        var start = 0
        do {
            val win = FloatArray(window)
            val n = minOf(window, pcm.size - start)
            if (n > 0) System.arraycopy(pcm, start, win, 0, n)
            tensor.load(win)
            val cats = classifier.classify(tensor).firstOrNull()?.categories ?: emptyList()
            var speech = 0f
            var snore = 0f
            for (c in cats) {
                when (c.label) {
                    in SPEECH -> speech += c.score
                    in SNORE -> snore += c.score
                }
            }
            speechMax = maxOf(speechMax, speech)
            snoreMax = maxOf(snoreMax, snore)
            start += window / 2
        } while (start + window / 2 < pcm.size)
        return Verdict(speechMax.coerceAtMost(1f), snoreMax.coerceAtMost(1f))
    }

    /** 8kHz(효율 모드) 소리는 16kHz 로 늘려서 넣는다 */
    private fun to16k(frames: List<ShortArray>, rate: Int): FloatArray {
        val total = frames.sumOf { it.size }
        val src = FloatArray(total)
        var i = 0
        for (f in frames) for (s in f) src[i++] = s / 32768f
        if (rate == 16000) return src
        val ratio = 16000.0 / rate
        val out = FloatArray((total * ratio).toInt())
        for (j in out.indices) {
            val x = j / ratio
            val k = x.toInt()
            val t = (x - k).toFloat()
            val a = src[k.coerceAtMost(total - 1)]
            val b = src[(k + 1).coerceAtMost(total - 1)]
            out[j] = a + (b - a) * t
        }
        return out
    }

    companion object {
        // YAMNet 분류 이름 중 "사람이 말하는 소리"
        private val SPEECH = setOf(
            "Speech", "Child speech, kid speaking", "Conversation", "Narration, monologue",
            "Babbling", "Whispering", "Shout", "Yell", "Screaming", "Laughter", "Crying, sobbing",
        )

        // "코골이 / 숨소리" 계열
        private val SNORE = setOf(
            "Snoring", "Breathing", "Snort", "Gasp", "Wheeze", "Pant", "Sniff",
        )

        /**
         * 잠꼬대로 볼지 결정.
         *  - AI 가 말소리라고 확실히 판단하고, 코골이 점수보다 높으면 → 잠꼬대
         *  - 구글 인식기가 글자로 알아들었고, 코골이가 아니면 → 잠꼬대
         */
        fun isSleepTalk(v: Verdict, heardWords: Boolean): Boolean =
            (v.speech >= 0.35f && v.speech > v.snore) ||
                (heardWords && v.speech >= 0.1f && v.snore < 0.5f)
    }
}
