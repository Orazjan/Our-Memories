package com.example.ourmemories.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationService : FirebaseMessagingService() {

    // Вызывается, когда приходит новое сообщение
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Проверяем, есть ли в сообщении уведомление (заголовок и текст)
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "Our Memories", it.body ?: "Новое событие!")
        }
    }

    // Вызывается, когда Firebase обновляет токен устройства
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    // Сохраняем токен в базу данных пользователя, чтобы знать, куда отправлять пуши
    private fun saveTokenToFirestore(token: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            Firebase.firestore.collection("users").document(user.uid).update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "Токен обновлен") }
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val channelId = "our_memories_channel"

        // Интент открывает MainActivity при клике на уведомление
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Создаем само уведомление
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.logotype_round) // Иконка в статус-баре
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources, R.mipmap.logotype
                )
            ) // Большая иконка справа
            .setContentTitle(title).setContentText(messageBody)
            .setAutoCancel(true) // Убирать при нажатии
            .setContentIntent(pendingIntent)
            .setColor(resources.getColor(R.color.baby_pink, theme)) // Цвет акцента

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Для Android 8.0+ нужен канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Уведомления от партнера", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}