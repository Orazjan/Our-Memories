package com.example.ourmemories.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.Models.WishItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * ViewModel для списка желаний [WishlistFragment].
 *
 * Функции:
 * - Загрузка списка желаний (своих и партнера).
 * - Добавление нового желания.
 * - Переключение статуса выполнения (выполнено/не выполнено).
 * - Удаление желания.
 */
class WishlistViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "WishlistViewModel"

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
        val myUid = auth.currentUser?.uid
        if (myUid == null) {
            _isRefreshing.value = false
            return
        }

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
    }

    /**
     * Запускает слушатель желаний.
     */
    private fun setupWishesListener(uids: List<String>) {
        wishesListener?.remove()

        wishesListener = db.collection("wishes")
            .whereIn("createdBy", uids)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                _isRefreshing.value = false

                if (e != null) {
                    if (e.message?.contains("index") == true) {
                        _toastMessage.value = "Требуется индекс Firestore. Проверьте логи."
                    }
                    Log.e(TAG, "Error fetching wishes", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val loadedWishes = snapshots.map { doc ->
                        doc.toObject(WishItem::class.java).copy(id = doc.id)
                    }
                    val sortedWishes = loadedWishes.sortedBy { it.isCompleted }
                    _wishes.value = sortedWishes
                }
            }
    }

    /**
     * Добавление нового желания.
     */
    fun addWish(title: String, desc: String, category: String) {
        val user = auth.currentUser ?: return

        val wish = WishItem(
            title = title,
            description = desc,
            category = category,
            isCompleted = false,
            createdBy = user.uid,
            creatorPhotoUrl = user.photoUrl?.toString(),
            timestamp = System.currentTimeMillis()
        )

        db.collection("wishes").add(wish).addOnFailureListener {
            _toastMessage.value = "Ошибка добавления: ${it.message}"
        }
    }

    /**
     * Обновление статуса выполнения желания.
     */
    fun toggleWishStatus(item: WishItem, isCompleted: Boolean) {
        if (item.id.isNotEmpty()) {
            db.collection("wishes").document(item.id).update("isCompleted", isCompleted)
                .addOnFailureListener {
                    _toastMessage.value = "Ошибка обновления"
                    startListening() 
                }
        }
    }

    /**
     * Удаление желания.
     */
    fun deleteWish(item: WishItem) {
        if (item.id.isNotEmpty()) {
            db.collection("wishes").document(item.id).delete()
                .addOnSuccessListener {
                    _toastMessage.value = "Желание удалено"
                }
                .addOnFailureListener {
                    _toastMessage.value = "Ошибка удаления"
                }
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
