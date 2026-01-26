package com.example.ourmemories.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * ViewModel для экрана галереи [GalleryFragment].
 *
 * Отвечает за:
 * - Загрузку списка воспоминаний из Firestore (с поддержкой пагинации).
 * - Фильтрацию по пользователям (показывать фото свои и партнера).
 * - Локальный поиск/фильтрацию по названию и описанию.
 * - Сортировку списка.
 * - Удаление воспоминаний (из БД и Storage).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val context = application.applicationContext
    private val TAG = "GalleryViewModel"

    private val _memories = MutableLiveData<List<Memory>>()
    val memories: LiveData<List<Memory>> = _memories

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private var allLoadedMemories = listOf<Memory>()
    private var currentUidsToLoad: List<String>? = null
    private var queryLimit: Long = 20
    private var isNewestFirst = true
    private var currentSearchQuery = ""

    private var userListener: ListenerRegistration? = null
    private var memoriesListener: ListenerRegistration? = null

    init {
        startListeningForUser()
    }

    /**
     * Подписывается на профиль пользователя, чтобы узнать UID партнера.
     * После получения обновляет список загружаемых авторов.
     */
    fun startListeningForUser() {
        val myUid = auth.currentUser?.uid ?: return
        _isRefreshing.value = true
        
        userListener?.remove()
        userListener = db.collection("users").document(myUid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                _isRefreshing.value = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val partnerUid = snapshot.getString("partnerUid")
                val uids = mutableListOf(myUid)
                if (!partnerUid.isNullOrEmpty()) uids.add(partnerUid)

                if (currentUidsToLoad != uids) {
                    currentUidsToLoad = uids
                    setupMemoriesListener()
                } else {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * Настраивает слушатель коллекции `memories` с учетом фильтров и лимитов.
     */
    private fun setupMemoriesListener() {
        val uids = currentUidsToLoad ?: return
        memoriesListener?.remove()

        val direction = if (isNewestFirst) Query.Direction.DESCENDING else Query.Direction.ASCENDING

        _isLoading.value = true
        
        memoriesListener = db.collection("memories")
            .whereIn("uploaderUid", uids)
            .orderBy("timestamp", direction)
            .limit(queryLimit)
            .addSnapshotListener { snapshots, e ->
                _isLoading.value = false
                _isRefreshing.value = false
                
                if (e != null) {
                    Log.e(TAG, "Error loading memories", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    allLoadedMemories = snapshots.map { doc ->
                        doc.toObject(Memory::class.java).copy(id = doc.id)
                    }
                    applyFilterAndSort()
                }
            }
    }

    /**
     * Применяет локальный текстовый фильтр к загруженному списку.
     */
    private fun applyFilterAndSort() {
        val filtered = if (currentSearchQuery.isEmpty()) {
            allLoadedMemories
        } else {
            allLoadedMemories.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) || 
                it.description.contains(currentSearchQuery, ignoreCase = true)
            }
        }
        _memories.value = filtered
    }

    fun loadMore() {
        if (_isLoading.value == true) return
        queryLimit += 20
        setupMemoriesListener()
    }

    fun refresh() {
        queryLimit = 20
        startListeningForUser()
    }

    fun setSearchQuery(query: String) {
        currentSearchQuery = query
        applyFilterAndSort()
    }

    fun setSortOrder(newestFirst: Boolean) {
        if (isNewestFirst != newestFirst) {
            isNewestFirst = newestFirst
            setupMemoriesListener()
        }
    }

    /**
     * TODO добавить
     * Удаляет память из Firestore и Storage.
     */
    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("memories").document(memory.id).get().await()

                val imagesToDelete = snapshot.get("images") as? List<String>
                    ?: listOf(memory.imageUrl).filter { it.isNotEmpty() }

                db.collection("memories").document(memory.id).delete().await()

                imagesToDelete.forEach { url ->
                    try {
                        storage.getReferenceFromUrl(url).delete().await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Не удалось удалить фото: $url", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    _toastMessage.value = context.getString(R.string.album_deleted)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toastMessage.value = context.getString(R.string.error_generic, e.message)
                }
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        memoriesListener?.remove()
    }
}
