package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore

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

    private val _images = MutableLiveData<List<String>>()
    val images: LiveData<List<String>> = _images

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

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
        
        loadDataFromFirestore()
    }

    private fun loadDataFromFirestore() {
        if (memoryId.isEmpty()) return

        db.collection("memories").document(memoryId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val list = document.get("images") as? List<String>
                    
                    if (list != null) {
                        _images.value = list
                    } else {
                        val cover = _coverUrl.value ?: ""
                        if (cover.isNotEmpty()) {
                            _images.value = listOf(cover)
                        }
                    }
                    
                    document.getString("title")?.let { _title.value = it }
                    document.getString("description")?.let { _description.value = it }
                    document.getLong("timestamp")?.let { _timestamp.value = it }
                    document.getString("imageUrl")?.let { _coverUrl.value = it }
                }
            }
            .addOnFailureListener {
                _toastMessage.value = "Ошибка загрузки: ${it.message}"
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
