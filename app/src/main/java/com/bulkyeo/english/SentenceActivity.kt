package com.bulkyeo.english

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.util.Locale

/** 배경 그림 위에 자막처럼 영어 문장을 보여주는 화면 */
class SentenceActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_INDEX = "index"
    }

    private var index = 0
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var background: ImageView
    private lateinit var situation: TextView
    private lateinit var english: TextView
    private lateinit var korean: TextView
    private lateinit var counter: TextView

    // 엄마가 갤러리에서 사진(영화 장면 캡처 등)을 골라 배경으로 쓸 수 있다. 권한이 필요 없는 사진 선택기.
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) saveCustomBackground(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sentence)

        background = findViewById(R.id.background)
        situation = findViewById(R.id.situation)
        english = findViewById(R.id.english)
        korean = findViewById(R.id.korean)
        counter = findViewById(R.id.counter)

        val top = findViewById<View>(R.id.topArea)
        val bottom = findViewById<View>(R.id.bottomArea)
        val topPad = top.paddingTop
        val bottomPad = bottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.updatePadding(top = topPad + bars.top)
            bottom.updatePadding(bottom = bottomPad + bars.bottom)
            insets
        }

        index = savedInstanceState?.getInt(EXTRA_INDEX)
            ?: intent.getIntExtra(EXTRA_INDEX, Prefs.lastIndex(this))

        findViewById<View>(R.id.btnPrev).setOnClickListener { move(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { move(+1) }
        findViewById<View>(R.id.btnSpeak).setOnClickListener { speak(0.9f) }
        findViewById<View>(R.id.btnSlow).setOnClickListener { speak(0.6f) }
        english.setOnClickListener { speak(0.9f) }
        findViewById<View>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<View>(R.id.btnBackground).setOnClickListener { askBackground() }

        tts = TextToSpeech(this, this)
        show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        index = intent.getIntExtra(EXTRA_INDEX, index)
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    private fun move(delta: Int) {
        val total = Sentences.all(this).size
        index = Math.floorMod(index + delta, total)
        show()
    }

    private fun show() {
        val s = Sentences.get(this, index)
        index = s.index
        Prefs.setLastIndex(this, index)
        background.setImageBitmap(Sentences.loadBackground(this, s))
        situation.text = "💡 ${s.situation}"
        english.text = s.en
        korean.text = s.ko
        counter.text = "${index + 1} / ${Sentences.all(this).size}"
    }

    // ---- 영어 읽어주기 (폰에 들어 있는 음성 엔진 사용, 인터넷 불필요) ----

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val r = t.setLanguage(Locale.US)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speak(rate: Float) {
        val t = tts
        if (t == null || !ttsReady) {
            Toast.makeText(this, "영어 음성을 쓸 수 없어요. 설정 > 텍스트 음성 변환에서 영어를 설치해 주세요.", Toast.LENGTH_LONG).show()
            return
        }
        t.setSpeechRate(rate)
        t.speak(Sentences.get(this, index).en, TextToSpeech.QUEUE_FLUSH, null, "sentence")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ---- 배경 바꾸기 ----

    private fun askBackground() {
        val s = Sentences.get(this, index)
        val hasCustom = Sentences.customBackgroundFile(this, s).exists()
        val items = if (hasCustom) arrayOf("내 사진에서 고르기", "원래 그림으로 되돌리기") else arrayOf("내 사진에서 고르기")
        AlertDialog.Builder(this)
            .setTitle("이 문장의 배경 그림")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> {
                        Sentences.customBackgroundFile(this, s).delete()
                        show()
                    }
                }
            }
            .show()
    }

    private fun saveCustomBackground(uri: Uri) {
        val s = Sentences.get(this, index)
        try {
            // 크기를 줄이고, 사진의 회전 정보(EXIF)대로 바로 세워서 저장한다
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IllegalStateException("decode failed")
            val rotation = contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val bmp = if (rotation == 0f) raw else
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
            Sentences.customBackgroundFile(this, s).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
            show()
            Toast.makeText(this, "배경을 바꿨어요!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "사진을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
        }
    }
}
