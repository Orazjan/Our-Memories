package com.example.ourmemories.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.MemoryDetailRepository

/**
 * ViewModel для экрана деталей альбома [com.example.ourmemories.Fragments.MemoryDetailFragment].
 *
 * Отвечает за:
 * - Загрузку данных альбома (заголовок, описание, фото) из Firestore.
 * - Сохранение изменений (редактирование текста и даты).
 * - Назначение новой обложки альбома.
 */
class MemoryDetailViewModel(
    application: Application,
    private val repository: MemoryDetailRepository,
) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _images = MutableLiveData<List<String>?>()
    val images: LiveData<List<String>> = _images as LiveData<List<String>>

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _isDeleted = MutableLiveData<Boolean>()
    val isDeleted: LiveData<Boolean> = _isDeleted

    private val _description = MutableLiveData<String>()
    val description: LiveData<String> = _description

    private val _timestamp = MutableLiveData<Long>()
    val timestamp: LiveData<Long> = _timestamp

    private val _coverUrl = MutableLiveData<String>()
    val coverUrl: LiveData<String> = _coverUrl

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var memoryId: String = ""

    /**
     * Инициализирует ViewModel начальными данными, переданными из аргументов фрагмента,
     * и запускает загрузку актуальных данных с сервера.
     */
    fun init(id: String, initialTitle: String, initialDesc: String, initialTimestamp: Long, initialCover: String) {
        if (memoryId.isNotEmpty() && memoryId == id) return

        memoryId = id
        _title.value = initialTitle
        _description.value = initialDesc
        _timestamp.value = initialTimestamp
        _coverUrl.value = initialCover

        if (initialCover.isNotEmpty()) {
            _images.value = listOf(initialCover)
        }

        loadDataFromFirestore()
    }

    /**
     * Загружает данные альбома из Firestore.
     */
    private fun loadDataFromFirestore() {
        if (memoryId.isEmpty()) return

        repository.getMemoryDetail(memoryId, onSuccess = { title, desc, time, cover, imgList ->
            _title.value = title
            _description.value = desc
            _timestamp.value = time
            _coverUrl.value = cover
            _images.value = imgList
        }, onFailure = { errorMsg ->
            _toastMessage.value = errorMsg
        })

    }

    /**
     * Удаляет фото из списка и БД.
     */
    fun deletePhoto(url: String) {
        if (memoryId.isEmpty()) return
        repository.deletePhoto(memoryId, url, onSuccess = {
                val currentList = _images.value?.toMutableList() ?: mutableListOf()
                currentList.remove(url)
                _images.value = currentList

                if (_coverUrl.value == url) {
                    val newCover = currentList.firstOrNull() ?: ""
                    _coverUrl.value = newCover
                    repository.updateCoverUrl(memoryId, newCover)
                }

                _toastMessage.value = context.getString(R.string.photo_deleted)
        }, onFailure = {
            _toastMessage.value = context.getString(R.string.error_delete_photo)
        })
    }

    /**
     * Удаляет альбом из Firestore и хранилища.
     */
    fun deleteAlbum() {
        val imagesToDelete = mutableSetOf<String>()
        _images.value?.let { imagesToDelete.addAll(it) }
        _coverUrl.value?.let { if (it.isNotEmpty()) imagesToDelete.add(it) }

        repository.deleteAlbum(memoryId, onSuccess = {
            _toastMessage.value = context.getString(R.string.album_deleted)
            _isDeleted.value = true
        }, onFailure = {
            _toastMessage.value = context.getString(R.string.error_generic, it.message)

        })
    }

    /**
     * Сохраняет отредактированные данные альбома.
     */
    fun saveChanges(newTitle: String, newDesc: String, newTimestamp: Long) {
        repository.saveChanges(newTitle, newDesc, newTimestamp, memoryId, onSuccess = {
            _title.value = newTitle
            _description.value = newDesc
            _timestamp.value = newTimestamp
            _toastMessage.value = context.getString(R.string.saved)

        }, onFailure = {
            _toastMessage.value = context.getString(R.string.error_save)
        })
    }

    /**
     * Устанавливает выбранное фото в качестве обложки альбома.
     */
    fun setCoverImage(url: String) {
        repository.setCover(url, memoryId, onSuccess = {
            _coverUrl.value = url
            _toastMessage.value = context.getString(R.string.cover_updated)

        }, onFailure = {
            _toastMessage.value = context.getString(R.string.error_cover_update)
        })
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
