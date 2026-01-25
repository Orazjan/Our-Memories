package com.example.ourmemories.ViewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * ViewModel для экрана деталей альбома [MemoryDetailFragment].
 *
 * Отвечает за:
 * - Загрузку данных альбома (заголовок, описание, фото) из Firestore.
 * - Сохранение изменений (редактирование текста и даты).
 * - Назначение новой обложки альбома.
 */
class MemoryDetailViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

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

        db.collection("memories").document(memoryId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    document.getString("title")?.let { _title.value = it }
                    document.getString("description")?.let { _description.value = it }
                    document.getLong("timestamp")?.let { _timestamp.value = it }

                    val fetchedCover = document.getString("imageUrl")
                    fetchedCover?.let { _coverUrl.value = it }

                    val list = document.get("images") as? List<String>

                    if (list != null && list.isNotEmpty()) {
                        _images.value = list
                    } else {
                        if (!fetchedCover.isNullOrEmpty()) {
                            _images.value = listOf(fetchedCover)
                        }
                    }
                }
            }
            .addOnFailureListener {
                _toastMessage.value = "Ошибка загрузки: ${it.message}"
            }
    }

    /**
     * Удаляет фото из списка и БД.
     */
    fun deletePhoto(url: String) {
        if (memoryId.isEmpty()) return

        db.collection("memories").document(memoryId)
            .update("images", FieldValue.arrayRemove(url))
            .addOnSuccessListener {
                try {
                    storage.getReferenceFromUrl(url).delete()
                } catch (e: Exception) { }

                val currentList = _images.value?.toMutableList() ?: mutableListOf()
                currentList.remove(url)
                _images.value = currentList

                if (_coverUrl.value == url) {
                    val newCover = currentList.firstOrNull() ?: ""
                    db.collection("memories").document(memoryId).update("imageUrl", newCover)
                    _coverUrl.value = newCover
                }

                _toastMessage.value = "Фото удалено"
            }
            .addOnFailureListener {
                _toastMessage.value = "Ошибка удаления фото"
            }
    }

    /**
     * Удаляет альбом из Firestore и хранилища.
     */
    fun deleteAlbum() {
        val imagesToDelete = mutableSetOf<String>()
        _images.value?.let { imagesToDelete.addAll(it) }
        _coverUrl.value?.let { if (it.isNotEmpty()) imagesToDelete.add(it) }

        db.collection("memories").document(memoryId).delete().addOnSuccessListener {
                _toastMessage.value = "Альбом удален"
                _isDeleted.value = true

                cleanupStorage(imagesToDelete.toList())
            }.addOnFailureListener {
                _toastMessage.value = "Ошибка удаления: ${it.message}"
            }
    }

    private fun cleanupStorage(urls: List<String>) {
        urls.forEach { url ->
            try {
                storage.getReferenceFromUrl(url).delete()
            } catch (e: Exception) {
                Log.e("MemoryDetailViewModel", "Не удалось удалить фото: $url", e)
            }
        }
    }

    /**
     * Сохраняет отредактированные данные альбома.
     */
    fun saveChanges(newTitle: String, newDesc: String, newTimestamp: Long) {
        db.collection("memories").document(memoryId).update(
            mapOf(
                "title" to newTitle,
                "description" to newDesc,
                "timestamp" to newTimestamp
            )
        ).addOnSuccessListener {
            _title.value = newTitle
            _description.value = newDesc
            _timestamp.value = newTimestamp
            _toastMessage.value = "Сохранено"
        }.addOnFailureListener {
            _toastMessage.value = "Ошибка сохранения"
        }
    }

    /**
     * Устанавливает выбранное фото в качестве обложки альбома.
     */
    fun setCoverImage(url: String) {
        db.collection("memories").document(memoryId).update("imageUrl", url)
            .addOnSuccessListener {
                _coverUrl.value = url
                _toastMessage.value = "Обложка обновлена"
            }
            .addOnFailureListener {
                _toastMessage.value = "Ошибка обновления обложки"
            }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
