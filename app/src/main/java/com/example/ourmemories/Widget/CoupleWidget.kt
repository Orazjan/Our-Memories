package com.example.ourmemories.Widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
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
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "CoupleWidget"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget: Starting update for ID $appWidgetId")
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_couple)

                // Интент для открытия приложения
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.ivWidgetPhoto, pendingIntent)

                // Читаем данные из КЭША
                val prefs = context.getSharedPreferences("AppCache", Context.MODE_PRIVATE)
                val date = prefs.getLong("relationship_date", 0)
                val photoUrl =
                    prefs.getString("partner_photo", null) ?: prefs.getString("my_photo", null)

                Log.d(TAG, "Loaded from cache: date=$date, photoUrl=$photoUrl")

                // Текст (Дни)
                val days = if (date > 0) calculateDays(date) else 0
                views.setTextViewText(R.id.tvWidgetDays, days.toString())
                Log.d(TAG, "Calculated days: $days")

                // Ставим безопасную заглушку сразу
                views.setImageViewResource(R.id.ivWidgetPhoto, R.mipmap.logotype)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // Грузим фото асинхронно
                if (!photoUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Starting Glide load for URL: $photoUrl")

                    val widgetTarget = object : CustomTarget<Bitmap>(200, 200) {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            Log.d(
                                TAG,
                                "Glide: onResourceReady. Bitmap size: ${resource.width}x${resource.height}, byteCount: ${resource.byteCount}"
                            )
                            try {
                                views.setImageViewBitmap(R.id.ivWidgetPhoto, resource)
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                                Log.d(TAG, "Widget updated with bitmap successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating widget with bitmap", e)
                                e.printStackTrace()
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            Log.d(TAG, "Glide: onLoadCleared")
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            Log.e(TAG, "Glide: onLoadFailed")
                            try {
                                views.setImageViewResource(R.id.ivWidgetPhoto, R.mipmap.logotype)
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting fallback image", e)
                                e.printStackTrace()
                            }
                        }
                    }

                    // Используем applicationContext
                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(photoUrl)
                        .dontAnimate()
                        .override(200, 200)
                        .centerCrop()
                        .into(widgetTarget)
                } else {
                    Log.d(TAG, "Photo URL is empty or null, skipping Glide load")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Critical error in updateAppWidget", e)
                e.printStackTrace()
            }
        }

        private fun calculateDays(startTimeInMillis: Long): Long {
            if (startTimeInMillis == 0L) return 0
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
                Calendar.MILLISECOND,
                0
            )
            }
            val start = Calendar.getInstance().apply {
                timeInMillis = startTimeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(
                Calendar.MILLISECOND,
                0
            )
            }
            val diff = today.timeInMillis - start.timeInMillis
            return if (diff < 0) 0 else TimeUnit.MILLISECONDS.toDays(diff)
        }
    }
}