package com.tftguide.assistant

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tftguide.assistant.overlay.GuideBubbleService
import com.tftguide.assistant.screen.ScreenCaptureService

class MainActivity : AppCompatActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val captureIntent = ScreenCaptureService.createStartIntent(
                    this,
                    result.resultCode,
                    result.data!!
                )
                ContextCompat.startForegroundService(this, captureIntent)
                ContextCompat.startForegroundService(this, GuideBubbleService.createIntent(this))
            } else {
                Toast.makeText(this, R.string.capture_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.start_capture).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            val manager = getSystemService(MediaProjectionManager::class.java)
            val intent = manager?.createScreenCaptureIntent()
            if (intent != null) {
                projectionLauncher.launch(intent)
            } else {
                Toast.makeText(this, R.string.media_projection_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.stop_capture).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, GuideBubbleService::class.java))
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
