package com.example.ourmemories.ViewModels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * ViewModel для создания нового воспоминания [AddMemoryFragment].
 *
 * Функции:
 * - Выбор и хранение списка изображений (Uri).
 * - Извлечение даты съемки из EXIF-метаданных фото.
 * - Сжатие изображений перед отправкой.
 * - Параллельная загрузка фото в Firebase Storage.
 * - Сохранение записи в Firestore и начисление баллов.
 */
class AddMemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val contentResolver = application.contentResolver

    private val _selectedUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val selectedUris: LiveData<MutableList<Uri>> = _selectedUris

    private val _coverUri = MutableLiveData<Uri?>()
    val coverUri: LiveData<Uri?> = _coverUri

    private val _eventDate = MutableLiveData<Long>(System.currentTimeMillis())
    val eventDate: LiveData<Long> = _eventDate

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    /**
     * Добавляет выбранные фото в список.
     * При первой загрузке пытается извлечь дату из первого фото.
     */
    fun addImages(uris: List<Uri>) {
        val currentList = _selectedUris.value ?: mutableListOf()
        val isFirstLoad = currentList.isEmpty()
        
        currentList.addAll(uris)
        _selectedUris.value = currentList

        if (_coverUri.value == null && currentList.isNotEmpty()) {
            _coverUri.value = currentList[0]
        }

        if (isFirstLoad && uris.isNotEmpty()) {
            extractDateFromImage(uris[0])
        }
    }

    /**
     * Удаляет фото из списка.
     */
    fun removeImage(position: Int) {
        val currentList = _selectedUris.value ?: return
        if (position in currentList.indices) {
            val removedUri = currentList[position]
            currentList.removeAt(position)
            
            if (removedUri == _coverUri.value) {
                _coverUri.value = currentList.firstOrNull()
            }
            _selectedUris.value = currentList
        }
    }

    /**
     * Устанавливает обложку.
     */
    fun setCover(position: Int) {
        val list = _selectedUris.value ?: return
        if (position in list.indices) {
            _coverUri.value = list[position]
        }
    }

    /**
     * Устанавливает дату события.
     */
    fun setEventDate(timestamp: Long) {
        _eventDate.value = timestamp
    }

    /**
     * Основной метод сохранения. Запускает процесс загрузки фото и создания документа.
     */
    fun saveMemory(title: String, description: String) {
        val uris = _selectedUris.value
        val user = auth.currentUser

        if (uris.isNullOrEmpty()) {
            _toastMessage.value = "Выберите хотя бы одно фото"
            return
        }
        if (title.isEmpty()) {
            _toastMessage.value = "Введите название"
            return
        }
        if (user == null) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val uploadJobs = uris.map { uri ->
                    async {
                        val url = uploadSingleImage(uri, user.uid)
                        Pair(uri, url)
                    }
                }

                val uploadedPairs = uploadJobs.awaitAll()
                val allUrls = uploadedPairs.map { it.second }

                val coverLocal = _coverUri.value
                val coverPair = uploadedPairs.find { it.first == coverLocal }
                val finalCoverUrl = coverPair?.second ?: allUrls.firstOrNull() ?: ""

                val memoryMap = hashMapOf(
                    "uploaderUid" to user.uid,
                    "title" to title,
                    "description" to description,
                    "timestamp" to (_eventDate.value ?: System.currentTimeMillis()),
                    "createdAt" to System.currentTimeMillis(),
                    "images" to allUrls,
                    "imageUrl" to finalCoverUrl
                )

                db.collection("memories").add(memoryMap).await()

                val pointsToAdd = uris.size * 5L
                db.collection("users").document(user.uid)
                    .update("treePoints", FieldValue.increment(pointsToAdd))

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _toastMessage.value = "Альбом сохранен! +$pointsToAdd очков"
                    _saveSuccess.value = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val msg = if (e.message?.contains("Unable to resolve host") == true) "Нет интернета" else e.localizedMessage
                    _toastMessage.value = "Ошибка: $msg"
                }
            }
        }
    }

    /**
     * Загружает одно фото в Firebase Storage.
     */
    private suspend fun uploadSingleImage(uri: Uri, uid: String): String {
        return withContext(Dispatchers.IO) {
            val compressedData = compressImage(uri)
            val fileName = UUID.randomUUID().toString()
            val ref = storage.reference.child("memories/$uid/$fileName.jpg")
            ref.putBytes(compressedData).await()
            ref.downloadUrl.await().toString()
        }
    }

    /**
     * Сжатие изображения до 1280x1280.
     */
    private fun compressImage(uri: Uri): ByteArray {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val scaledBitmap = scaleBitmap(bitmap, 1280)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            return outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("AddMemoryVM", "Error compressing image", e)
            throw e
        }
    }

    /**
     * Сжатие изображения до заданного размера
     */
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

    /**
     *
     */
    private fun extractDateFromImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    val dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                    if (dateString != null) {
                        val format = if (dateString.contains("-")) "yyyy-MM-dd HH:mm:ss" else "yyyy:MM:dd HH:mm:ss"
                        val sdf = SimpleDateFormat(format, Locale.US)
                        val date = sdf.parse(dateString)
                        if (date != null) {
                            withContext(Dispatchers.Main) {
                                _eventDate.value = date.time
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AddMemoryVM", "Error extracting date", e)
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
