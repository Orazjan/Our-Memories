package com.example.ourmemories.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Widget.CoupleWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"]

        Log.d("FCM", "Получено сообщение типа: $type")

        if (type == "widget_update") {
            val imageUrl = data["imageUrl"]
            if (imageUrl != null) {
                Log.d("FCM", "Получено фото для виджета: $imageUrl")


                val prefs = getSharedPreferences("AppCache", Context.MODE_PRIVATE)
                prefs.edit().putString("widget_live_photo", imageUrl).apply()


                forceUpdateWidget()
            }
            return
        }

        remoteMessage.notification?.let {
            sendNotification(it.title ?: "Our Memories", it.body ?: "Новое событие!")
        }
    }

    /**
     * Прямое обновление виджета без использования Broadcast.
     * Это надежнее работает из фонового сервиса.
     */
    private fun forceUpdateWidget() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val componentName = ComponentName(this, CoupleWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)

            if (ids.isNotEmpty()) {
                Log.d("FCM", "Найдено виджетов для обновления: ${ids.size}")
                for (id in ids) {
                    CoupleWidget.updateAppWidget(this, appWidgetManager, id)
                }
            } else {
                Log.d("FCM", "Виджеты не найдены на рабочем столе")
            }
        } catch (e: Exception) {
            Log.e("FCM", "Ошибка обновления виджета", e)
            e.printStackTrace()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            Firebase.firestore.collection("users").document(user.uid).update("fcmToken", token)
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val channelId = "our_memories_channel"
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val color = try {
            ContextCompat.getColor(this, R.color.baby_pink)
        } catch (e: Exception) {
            0xFFC1E3
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.logotype_round)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources, R.mipmap.logotype
                )
            ).setContentTitle(title).setContentText(messageBody).setAutoCancel(true)
            .setContentIntent(pendingIntent).setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Уведомления от партнера", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(0, notificationBuilder.build())
    }
}