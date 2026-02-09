package com.example.ourmemories.ViewModels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.AddMemoryRepository
import com.example.ourmemories.Utils.Constants
import com.example.ourmemories.Utils.ImageHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel для создания нового воспоминания [com.example.ourmemories.Fragments.AddMemoryFragment].
 *
 * Функции:
 * - Выбор и хранение списка изображений (Uri).
 * - Извлечение даты съемки из EXIF-метаданных фото.
 * - Сжатие изображений перед отправкой.
 * - Параллельная загрузка фото в Firebase Storage.
 * - Сохранение записи в Firestore и начисление баллов.
 */
class AddMemoryViewModel(
    application: Application,
    private val repository: AddMemoryRepository,
    private val imageHandler: ImageHandler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _selectedUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val selectedUris: LiveData<MutableList<Uri>> = _selectedUris

    private val _coverUri = MutableLiveData<Uri?>()
    val coverUri: LiveData<Uri?> = _coverUri

    private val _eventDate = MutableLiveData(System.currentTimeMillis())
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
            viewModelScope.launch(ioDispatcher) {
                val date = imageHandler.extractDateFromImage(uris[0])
                if (date != null) {
                    withContext(Dispatchers.Main) {
                        _eventDate.value = date
                    }
                }
            }
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
        val uid = repository.getCurrentUserUid()

        if (uris.isNullOrEmpty()) {
            _toastMessage.value = context.getString(R.string.error_select_photo)
            return
        }
        if (title.isEmpty()) {
            _toastMessage.value = context.getString(R.string.error_enter_title)
            return
        }
        if (uid == null) return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val uploadJobs = uris.map { uri ->
                    async {
                        val bytes = withContext(ioDispatcher) {
                            imageHandler.compressImage(uri)
                        }
                        val url = repository.uploadImageBytes(bytes, uid)
                        Pair(uri, url)
                    }
                }

                val uploadedPairs = uploadJobs.awaitAll()
                val allUrls = uploadedPairs.map { it.second }

                val coverLocal = _coverUri.value
                val coverPair = uploadedPairs.find { it.first == coverLocal }
                val finalCoverUrl = coverPair?.second ?: allUrls.firstOrNull() ?: ""

                val memoryMap = hashMapOf(
                    "uploaderUid" to uid,
                    "title" to title,
                    "description" to description,
                    "timestamp" to (_eventDate.value ?: System.currentTimeMillis()),
                    "createdAt" to System.currentTimeMillis(),
                    "images" to allUrls,
                    Constants.ARG_IMAGE_URL to finalCoverUrl
                )

                repository.addMemory(memoryMap)
                val pointsToAdd = uris.size * 5L
                repository.incrementUserPoints(uid, pointsToAdd)

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _toastMessage.value =
                        context.getString(R.string.memory_saved_points, pointsToAdd)
                    _saveSuccess.value = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val msg = if (e.message?.contains("Unable to resolve host") == true) {
                        context.getString(R.string.error_no_internet)
                    } else {
                        e.localizedMessage ?: "Unknown error"
                    }
                    _toastMessage.value = context.getString(R.string.error_generic, msg)
                }
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
