package com.tftguide.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Minimal floating bubble implementation that exposes a simple API for subclasses.
 */
open class ExpandableBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private lateinit var titleView: TextView
    private lateinit var messageView: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        inflateBubble()
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    private fun inflateBubble() {
        if (windowManager != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_overlay_bubble, null)
        titleView = view.findViewById(R.id.bubble_title)
        messageView = view.findViewById(R.id.bubble_message)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 32
        params.y = 200

        view.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0
            private var lastY = 0
            private var initialX = 0
            private var initialY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val layoutParams = params
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX.toInt() - lastX
                        val deltaY = event.rawY.toInt() - lastY
                        layoutParams.x = initialX - deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(v, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(view, params)
        rootView = view
    }

    fun updateBubble(title: String, message: String) {
        titleView.text = title
        messageView.text = message
    }

    private fun removeBubble() {
        rootView?.let { view ->
            windowManager?.removeView(view)
            rootView = null
        }
    }

    private fun createNotification(): Notification {
        val channelId = "tft-guide-bubble"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.bubble_content_description),
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.bubble_content_description))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
