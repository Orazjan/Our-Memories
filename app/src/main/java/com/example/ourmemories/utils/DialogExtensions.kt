package com.example.ourmemories.utils

import android.app.Dialog
import android.os.Build
import android.view.WindowManager

fun Dialog.enableBlur(radius: Int = 30) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.window?.let { window ->
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attributes = window.attributes
            attributes.blurBehindRadius = radius
            window.attributes = attributes
        }
    }
}