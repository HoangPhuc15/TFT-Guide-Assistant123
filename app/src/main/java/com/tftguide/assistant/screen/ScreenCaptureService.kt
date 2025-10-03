package com.tftguide.assistant.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.MediaProjection
import android.hardware.display.MediaProjectionManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import com.tftguide.assistant.R

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var captureHandler: Handler? = null
    private val analyzer = BoardAnalyzer()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        if (mediaProjection != null) return START_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA_INTENT)
        }
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager?.getMediaProjection(resultCode, dataIntent!!)
        if (mediaProjection == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        setupCapture()
        return START_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun setupCapture() {
        val displayMetrics = resources.displayMetrics
        val size = Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val handlerThread = HandlerThread("tft-screen-capture")
        handlerThread.start()
        captureHandler = Handler(handlerThread.looper)

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            PixelFormat.RGBA_8888,
            2
        )

        mediaProjection?.createVirtualDisplay(
            "tft-guide-capture",
            size.width,
            size.height,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val result = analyzer.analyze(image)
                BoardAnalysisBus.publish(result)
            } finally {
                image.close()
            }
        }, captureHandler)
    }

    private fun teardown() {
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        captureHandler?.looper?.quitSafely()
        captureHandler = null
    }

    private fun createNotification(): Notification {
        val channelId = "tft-screen-capture"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.start_capture))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA_INTENT = "extra_data_intent"
        private const val NOTIFICATION_ID = 2001

        fun createStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
            }
    }
}
