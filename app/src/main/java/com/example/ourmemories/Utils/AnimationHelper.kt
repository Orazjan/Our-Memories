package com.example.ourmemories.Utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator

object AnimationHelper {

    /**
     * Добавляет тактильный отклик
     */
    @SuppressLint("ClickableViewAccessibility")
    fun addTouchBounce(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
            }
            false
        }
    }

    /**
     * Анимация подпрыгивание
     */
    fun animateJelly(view: View) {
        view.scaleX = 0.5f
        view.scaleY = 0.5f
        view.alpha = 0f

        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800)
            .setInterpolator(BounceInterpolator()).start()
    }
}