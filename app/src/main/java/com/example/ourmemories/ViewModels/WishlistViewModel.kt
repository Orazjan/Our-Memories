package com.example.ourmemories.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.Models.User
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.WishlistRepository
import com.google.firebase.firestore.ListenerRegistration

/**
 * ViewModel для списка желаний.
 */
class WishlistViewModel(
    application: Application, private val repository: WishlistRepository
) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    private val _wishes = MutableLiveData<List<WishItem>>()
    val wishes: LiveData<List<WishItem>> = _wishes

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var userListener: ListenerRegistration? = null
    private var wishesListener: ListenerRegistration? = null
    private var currentUids: List<String>? = null

    init {
        startListening()
    }

    /**
     * Запускает слушатель профиля пользователя для определения списка UID (свой + партнера),
     * затем запускает слушатель желаний.
     */
    fun startListening() {
        val myUid = repository.getCurrentUserUid()
        if (myUid == null) {
            _isRefreshing.value = false
            return
        }

        _isRefreshing.value = true

        userListener?.remove()
        userListener = repository.listenToUser(myUid) { user ->
            handleUserUpdate(user, myUid)
        }
    }

    private fun handleUserUpdate(user: User?, myUid: String) {
        if (user != null) {
            val partnerUid = user.partnerUid
            val uids = mutableListOf(myUid)
            if (!partnerUid.isNullOrEmpty()) {
                uids.add(partnerUid)
            }

            if (currentUids != uids) {
                currentUids = uids
                setupWishesListener(uids)
            } else {
                if (_wishes.value != null) _isRefreshing.value = false
            }
        } else {
            _isRefreshing.value = false
        }
    }

    /**
     * Запускает слушатель желаний через репозиторий.
     */
    private fun setupWishesListener(uids: List<String>) {
        wishesListener?.remove()

        wishesListener = repository.listenToWishes(uids = uids, onData = { loadedWishes ->
                _isRefreshing.value = false
            val sortedWishes = loadedWishes.sortedBy { it.isCompleted }
            _wishes.value = sortedWishes
        }, onError = { e ->
            _isRefreshing.value = false
            if (e.message?.contains("index") == true) {
                _toastMessage.value = "Требуется индекс Firestore."
                }
        })
    }

    /**
     * Добавление нового желания.
     */
    fun addWish(title: String, desc: String, category: String) {
        val user = repository.getCurrentUser() ?: return

        val wish = WishItem(
            title = title,
            description = desc,
            category = category,
            isCompleted = false,
            createdBy = user.uid,
            creatorPhotoUrl = user.photoUrl?.toString(),
            timestamp = System.currentTimeMillis()
        )

        repository.addWish(wish) { e ->
            _toastMessage.value = getStringSafe(R.string.error_generic, e.message ?: "")
        }
    }

    /**
     * Обновление статуса выполнения желания.
     */
    fun toggleWishStatus(item: WishItem, isCompleted: Boolean) {
        if (item.id.isNotEmpty()) {
            repository.updateWishStatus(item.id, isCompleted) {
                _toastMessage.value = getStringSafe(R.string.error_save)
                startListening()
            }
        }
    }

    /**
     * Удаление желания.
     */
    fun deleteWish(item: WishItem) {
        if (item.id.isNotEmpty()) {
            repository.deleteWish(item.id, onSuccess = {}, onFailure = {
                _toastMessage.value = getStringSafe(R.string.error_delete_photo)
            })
        }
    }

    private fun getStringSafe(resId: Int, vararg formatArgs: Any): String {
        return try {
            if (formatArgs.isNotEmpty()) context.getString(
                resId, *formatArgs
            ) else context.getString(resId)
        } catch (e: Exception) {
            ""
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        wishesListener?.remove()
    }
}