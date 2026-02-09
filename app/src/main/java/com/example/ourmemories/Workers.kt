package com.example.ourmemories.Workers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Utils.Constants
import com.example.ourmemories.Widget.CoupleWidget
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    context: Context, workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("WidgetWorker", "Начинаем фоновое обновление виджета...")

        return try {
            val context = applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids =
                appWidgetManager.getAppWidgetIds(ComponentName(context, CoupleWidget::class.java))

            if (ids.isEmpty()) return Result.success()

            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

            val inputUrl = inputData.getString(Constants.ARG_IMAGE_URL)
            if (inputUrl != null) {
                prefs.edit().putString("widget_live_photo", inputUrl).apply()
            }

            val date = prefs.getLong("relationship_date", 0)
            val photoUrl = inputUrl ?: prefs.getString("widget_live_photo", null)
            ?: prefs.getString("partner_photo", null) ?: prefs.getString("my_photo", null)

            val days = if (date > 0) calculateDays(date) else 0

            val views = RemoteViews(context.packageName, R.layout.widget_couple)
            views.setTextViewText(R.id.tvWidgetDays, days.toString())

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.ivWidgetPhoto, pendingIntent)

            var bitmap: Bitmap? = null

            if (!photoUrl.isNullOrEmpty()) {
                var attempt = 0
                val maxRetries = 3

                while (bitmap == null && attempt < maxRetries) {
                    try {
                        attempt++
                        bitmap = Glide.with(context).asBitmap().load(photoUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transform(CenterCrop(), RoundedCorners(40)).override(300, 300)
                            .timeout(60000).submit().get()
                    } catch (e: Exception) {
                        Log.e("WidgetWorker", "Попытка $attempt загрузки не удалась: ${e.message}")

                        if (attempt < maxRetries) {
                            Log.d("WidgetWorker", "Ждем 1.5 сек перед повтором...")
                            delay(1500)
                        } else {
                            Log.e("WidgetWorker", "Все внутренние попытки исчерпаны.")

                            if (inputUrl != null && runAttemptCount < 3) {
                                Log.w("WidgetWorker", "Откладываем на потом (Result.retry)...")
                                return Result.retry()
                            }
                        }
                    }
                }
            }

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.ivWidgetPhoto, bitmap)
            } else {
                views.setImageViewResource(R.id.ivWidgetPhoto, R.mipmap.logotype)
            }

            appWidgetManager.updateAppWidget(ids, views)
            Log.d("WidgetWorker", "Виджет успешно обновлен!")

            Result.success()
        } catch (e: Exception) {
            Log.e("WidgetWorker", "Критическая ошибка в Worker", e)
            Result.failure()
        }
    }

    private fun calculateDays(startTimeInMillis: Long): Long {
        if (startTimeInMillis == 0L) return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
            Calendar.MILLISECOND, 0
        )
        }
        val start = Calendar.getInstance().apply {
            timeInMillis = startTimeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
            Calendar.MILLISECOND, 0
        )
        }
        val diff = today.timeInMillis - start.timeInMillis
        return if (diff < 0) 0 else TimeUnit.MILLISECONDS.toDays(diff)
    }
}