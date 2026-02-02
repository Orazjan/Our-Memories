package com.example.ourmemories.Utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Класс-помощник для работы с изображениями.
 * Вынесен отдельно, чтобы его можно было "замокать" в тестах.
 */
open class ImageHandler(private val context: Context) {

    open fun compressImage(uri: Uri): ByteArray {
        val bitmap = getBitmapFromUri(uri)
        val scaledBitmap = scaleBitmap(bitmap, 1280)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return outputStream.toByteArray()
    }

    open fun extractDateFromImage(uri: Uri): Long? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                if (dateString != null) {
                    val format =
                        if (dateString.contains("-")) "yyyy-MM-dd HH:mm:ss" else "yyyy:MM:dd HH:mm:ss"
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.parse(dateString)?.time
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                newWidth = maxDimension
                newHeight = (newWidth * originalHeight) / originalWidth
            } else {
                newHeight = maxDimension
                newWidth = (newHeight * originalWidth) / originalHeight
            }
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}