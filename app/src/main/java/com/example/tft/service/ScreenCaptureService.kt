package com.example.tft.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.IBinder
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.hardware.display.VirtualDisplay
import androidx.core.app.NotificationCompat
import com.example.tft.R
import com.example.tft.data.TftRepository
import com.example.tft.notif.BubbleNotifier
import com.example.tft.rules.TipEngine
import com.example.tft.rules.TipState
import com.example.tft.vision.ScreenAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    private val binder = CaptureBinder()
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private lateinit var analyzer: ScreenAnalyzer
    private lateinit var tipEngine: TipEngine
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val repo = TftRepository.from(this)
        analyzer = ScreenAnalyzer(repo)
        tipEngine = TipEngine.from(this)
        startForeground(NOTIFICATION_ID, foregroundNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        projection?.stop()
        projection = intent?.getParcelableExtra(EXTRA_PROJECTION, MediaProjection::class.java)
            ?: return START_NOT_STICKY
        val windowManager = getSystemService(WindowManager::class.java)
        val densityDpi = resources.displayMetrics.densityDpi
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
        reader = ImageReader.newInstance(width, height, ImageFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            DISPLAY_NAME,
            width,
            height,
            densityDpi,
            0,
            reader?.surface,
            null,
            null
        )
        scope.launch {
            captureLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        projection?.stop()
        virtualDisplay?.release()
        reader?.close()
        job.cancel()
    }

    private suspend fun captureLoop() {
        while (projection != null) {
            val image = reader?.acquireLatestImage()
            if (image != null) {
                try {
                    val plane = image.planes.first()
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * image.width
                    val bitmapWidth = image.width + rowPadding / pixelStride
                    val rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val bitmap = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                    try {
                        val state = analyzer.analyze(bitmap)
                        emitTips(state)
                    } finally {
                        bitmap.recycle()
                        rawBitmap.recycle()
                    }
                } finally {
                    image.close()
                }
            }
            delay(500L)
        }
    }

    private fun emitTips(state: TipState) {
        val session = tipEngine.evaluate(state)
        if (session.entries.isEmpty()) {
            Log.d(TAG, "No tips for state ${state.screen}")
            return
        }
        BubbleNotifier.post(this, session, tipEngine.patch)
    }

    private fun foregroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tip)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.action_start_capture))
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL,
            getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        manager?.createNotificationChannel(channel)
    }

    inner class CaptureBinder : Binder() {
        fun startWith(projection: MediaProjection) {
            val intent = Intent(this@ScreenCaptureService, ScreenCaptureService::class.java)
            intent.putExtra(EXTRA_PROJECTION, projection)
            startService(intent)
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 42
        private const val FOREGROUND_CHANNEL = "tft_capture"
        private const val EXTRA_PROJECTION = "projection"
        private const val DISPLAY_NAME = "tft-capture"
    }
}
