package com.bulkyeo.app

import android.app.Activity
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * 조명 화면. 흰 화면 + 최대 밝기.
 * "불꺼" 라고 말하거나 화면을 두 번 톡톡 누르면 닫힌다.
 * 잠금 화면 위에 떠도 잠금은 풀리지 않는다 (다른 앱이나 정보에는 접근 불가).
 */
class LightActivity : Activity() {

    private var lastTapAt = 0L
    private var torchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LightState.activity = WeakReference(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val text = TextView(this).apply {
            text = "\"불꺼\" 라고 말하면 꺼져요"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(200, 200, 200))
            setBackgroundColor(Color.WHITE)
            setOnClickListener {
                val now = SystemClock.elapsedRealtime()
                if (now - lastTapAt < 400) finish()
                lastTapAt = now
            }
        }
        setContentView(text)

        val lp = window.attributes
        lp.screenBrightness = 1.0f
        window.attributes = lp

        // 화면을 띄우려고 보낸 알림은 정리
        getSystemService(NotificationManager::class.java)?.cancel(ListenService.NOTI_LIGHT)

        findTorch()
        setTorch(true)
    }

    override fun onResume() {
        super.onResume()
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        setTorch(false)
        if (LightState.activity?.get() === this) LightState.activity = null
        super.onDestroy()
    }

    private fun findTorch() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
        val cm = getSystemService(CameraManager::class.java) ?: return
        torchId = try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            getSystemService(CameraManager::class.java)?.setTorchMode(id, on)
        } catch (_: Exception) {
        }
    }
}
