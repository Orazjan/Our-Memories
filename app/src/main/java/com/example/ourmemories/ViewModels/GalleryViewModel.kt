package com.example.ourmemories.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.Repositories.GalleryRepository
import com.example.ourmemories.Repositories.MainRepository
import com.google.firebase.firestore.ListenerRegistration

/**
 * ViewModel для экрана Галереи.
 * Управляет загрузкой, фильтрацией и удалением воспоминаний.
 */
class GalleryViewModel(
    application: Application,
    private val galleryRepository: GalleryRepository,
    private val userRepository: MainRepository
) : AndroidViewModel(application) {

    private val _memories = MutableLiveData<List<Memory>>()
    val memories: LiveData<List<Memory>> = _memories

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var allLoadedMemories = listOf<Memory>()

    private var isNewestFirst = true
    private var queryLimit = 20L
    private var currentSearchQuery = ""
    private var currentUidsToLoad = listOf<String>()

    private var memoriesListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    init {
        startListening()
    }

    /**
     * Слушатель пользователя через Репозиторий.
     */
    fun startListening() {
        val myUid = userRepository.getCurrentUserUid()
        if (myUid == null) {
            _isRefreshing.value = false
            return
        }

        _isRefreshing.value = true

        userListener?.remove()
        userListener = userRepository.listenToUser(myUid) { user ->
            if (user != null) {
                val uids = mutableListOf(myUid)
                if (!user.partnerUid.isNullOrEmpty()) {
                    uids.add(user.partnerUid)
                }

                if (currentUidsToLoad != uids) {
                    currentUidsToLoad = uids
                    setupMemoriesListener()
                } else {
                    _isRefreshing.value = false
                }
            } else {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Фильтрация и сортировка
     */
    private fun applyFilterAndSort() {
        val filtered = if (currentSearchQuery.isEmpty()) {
            allLoadedMemories
        } else {
            allLoadedMemories.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) || it.description.contains(
                    currentSearchQuery, ignoreCase = true
                )
            }
        }
        _memories.value = filtered
    }

    /**
     * Установка очереди
     */
    fun setSortOrder(newestFirst: Boolean) {
        if (isNewestFirst != newestFirst) {
            isNewestFirst = newestFirst
            queryLimit = 20L
            setupMemoriesListener()
        }
    }

    /**
     * Загрузка
     */
    fun loadMore() {
        if (_isLoading.value == true) return

        queryLimit += 20
        setupMemoriesListener()
    }

    /**
     *
     * Обновление
     */
    fun refresh() {
        _isRefreshing.value = true
        queryLimit = 20L
        setupMemoriesListener()
    }

    /**
     * Cлушатель воспоминаний.
     */
    private fun setupMemoriesListener() {
        memoriesListener?.remove()

        if (currentUidsToLoad.isEmpty()) {
            _isRefreshing.value = false
            return
        }

        _isLoading.value = true

        memoriesListener = galleryRepository.listenToMemories(
            uids = currentUidsToLoad,
            isNewestFirst = isNewestFirst,
            limit = queryLimit,
            onDataCallback = { loadedList ->
                _isLoading.value = false
                _isRefreshing.value = false

                allLoadedMemories = loadedList
                applyFilterAndSort()
            },
            onError = { e ->
                _isLoading.value = false
                _isRefreshing.value = false
                e.stackTraceToString()
            })
    }

    /**
     * Поиск
     */
    fun setSearchQuery(query: String) {
        currentSearchQuery = query
        applyFilterAndSort()
    }


    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        memoriesListener?.remove()
        userListener?.remove()
    }
}