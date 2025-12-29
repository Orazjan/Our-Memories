package com.example.ourmemories.Widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.AppWidgetTarget
import com.bumptech.glide.request.transition.Transition
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CoupleWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "CoupleWidget"

        // Метод для принудительного обновления всех виджетов из приложения
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            intent.component = ComponentName(context, CoupleWidget::class.java)
            context.sendBroadcast(intent)
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_couple)

                // 1. Клик открывает приложение
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.ivWidgetPhoto, pendingIntent)

                // 2. Читаем данные
                val prefs = context.getSharedPreferences("AppCache", Context.MODE_PRIVATE)
                val date = prefs.getLong("relationship_date", 0)
                // Приоритет фото: партнера -> мое -> дефолт
                val photoUrl = prefs.getString("partner_photo", null) 
                    ?: prefs.getString("my_photo", null)

                // 3. Считаем дни
                val days = if (date > 0) calculateDays(date) else 0
                views.setTextViewText(R.id.tvWidgetDays, days.toString())

                // 4. Устанавливаем фото
                // Сначала ставим заглушку, чтобы не было пустого места пока грузится
                views.setImageViewResource(R.id.ivWidgetPhoto, R.mipmap.logotype)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                if (!photoUrl.isNullOrEmpty()) {
                    // Используем AppWidgetTarget - он корректно работает с виджетами
                    val widgetTarget = object : AppWidgetTarget(context.applicationContext, R.id.ivWidgetPhoto, views, appWidgetId) {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            super.onResourceReady(resource, transition)
                            // AppWidgetTarget сам обновит ImageView, но иногда нужно подтолкнуть
                            Log.d(TAG, "Image loaded for widget $appWidgetId")
                        }
                    }

                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(photoUrl)
                        .override(300, 300) // Оптимизация памяти
                        .centerCrop()
                        .dontAnimate() // Анимации в виджетах не работают и могут вызывать баги
                        .into(widgetTarget)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
            }
        }

        private fun calculateDays(startTimeInMillis: Long): Long {
            if (startTimeInMillis == 0L) return 0
            
            // Сбрасываем часы/минуты для корректного подсчета дней
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = Calendar.getInstance().apply {
                timeInMillis = startTimeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            
            val diff = today.timeInMillis - start.timeInMillis
            return if (diff < 0) 0 else TimeUnit.MILLISECONDS.toDays(diff)
        }
    }
}
