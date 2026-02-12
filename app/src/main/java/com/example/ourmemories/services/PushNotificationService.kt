package com.example.ourmemories.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.utils.Constants
import com.example.ourmemories.Workers.WidgetUpdateWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

class PushNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"]

        Log.d("FCM", "Получено сообщение типа: $type")

        if (type == "widget_update") {
            val imageUrl = data[Constants.ARG_IMAGE_URL]
            if (imageUrl != null) {
                Log.d("FCM", "Получено фото для виджета: $imageUrl")
                val inputData = Data.Builder().putString(Constants.ARG_IMAGE_URL, imageUrl).build()

                val constraints =
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()


                val request =
                    OneTimeWorkRequestBuilder<WidgetUpdateWorker>().setInputData(inputData)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setConstraints(constraints).setBackoffCriteria(
                            BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS
                        ).build()

                WorkManager.getInstance(this).enqueueUniqueWork(
                    "widget_update_work", ExistingWorkPolicy.REPLACE, request
                )
            }
            return
        }

        remoteMessage.notification?.let {
            val title = it.title ?: getString(R.string.app_name)
            val body = it.body ?: getString(R.string.notification_new_event)
            sendNotification(title, body)
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
            val channelName = getString(R.string.notification_channel_name)
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(0, notificationBuilder.build())
    }
}