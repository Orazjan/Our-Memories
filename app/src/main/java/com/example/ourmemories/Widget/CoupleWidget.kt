package com.example.ourmemories.Widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
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

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_couple)

                val intent = Intent(context, EnterActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.ivWidgetPhoto, pendingIntent)

                val prefs = context.getSharedPreferences("AppCache", Context.MODE_PRIVATE)
                val date = prefs.getLong("relationship_date", 0)
                val photoUrl = prefs.getString("widget_live_photo", null) ?: prefs.getString(
                    "partner_photo", null
                ) ?: prefs.getString("my_photo", null)

                val days = if (date > 0) calculateDays(date) else 0
                views.setTextViewText(R.id.tvWidgetDays, days.toString())

                views.setImageViewResource(R.id.ivWidgetPhoto, R.mipmap.logotype_round)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                if (!photoUrl.isNullOrEmpty()) {
                    val target = object : CustomTarget<Bitmap>(300, 300) {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            try {
                                views.setImageViewBitmap(R.id.ivWidgetPhoto, resource)
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка установки Bitmap в виджет", e)
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Очистка не требуется для виджета
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            Log.e(TAG, "Ошибка загрузки Glide для виджета")
                        }
                    }

                    GlideHelper.loadWidgetImage(context, photoUrl, target)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
            }
        }

//        private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
//            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
//            val canvas = Canvas(output)
//            val color = -0xbdbdbe
//            val paint = Paint()
//            val rect = Rect(0, 0, bitmap.width, bitmap.height)
//            val rectF = RectF(rect)
//
//            paint.isAntiAlias = true
//            canvas.drawARGB(0, 0, 0, 0)
//            paint.color = color
//            canvas.drawRoundRect(rectF, pixels, pixels, paint)
//
//            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
//            canvas.drawBitmap(bitmap, rect, rect, paint)
//
//            return output
//        }

        /**
         * Подсчёт дней
         */
        private fun calculateDays(startTimeInMillis: Long): Long {
            if (startTimeInMillis == 0L) return 0

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

        fun sendRefreshBroadcast(context: Context) {
            try {
                val intent = Intent(context, CoupleWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, CoupleWidget::class.java))

                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления виджета", e)
            }
        }
    }
}
