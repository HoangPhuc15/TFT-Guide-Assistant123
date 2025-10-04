package com.example.tft.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.tft.R
import com.example.tft.ui.BubbleActivity

object BubbleNotifier {
    private const val CHANNEL_ID = "tft_bubble_chat"

    fun post(context: Context, conversationId: String, tips: List<String>) {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            Intent(context, BubbleActivity::class.java)
                .putStringArrayListExtra(BubbleActivity.EXTRA_TIPS, ArrayList(tips))
                .putExtra(BubbleActivity.EXTRA_CONVERSATION_ID, conversationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName(context.getString(R.string.notification_title))
            .build()

        val style = NotificationCompat.MessagingStyle(person).apply {
            val first = tips.firstOrNull() ?: context.getString(R.string.notification_placeholder)
            addMessage(first, System.currentTimeMillis(), person)
        }

        val bubble = NotificationCompat.BubbleMetadata.Builder()
            .setDesiredHeight(600)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.ic_stat_tip))
            .setIntent(contentIntent)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tip)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(tips.firstOrNull() ?: context.getString(R.string.notification_placeholder))
            .setStyle(style)
            .setShortcutId(conversationId)
            .setBubbleMetadata(bubble)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
