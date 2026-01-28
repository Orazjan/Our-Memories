package com.example.ourmemories.ViewModels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.example.ourmemories.Models.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ViewModel для главного экрана [com.example.ourmemories.Fragments.MainFragment].
 */
class MainViewModel(
    application: Application, private val repository: UserRepository
) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var localBonusProcessedDate: Long = 0

    private val context = application.applicationContext

    private val TAG = "MainViewModel"

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isWidgetLoading = MutableLiveData(false)
    val isWidgetLoading: LiveData<Boolean> = _isWidgetLoading

    private val _partnerUser = MutableLiveData<User?>()
    val partnerUser: LiveData<User?> = _partnerUser

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage
    
    /**
     * Список доступных эмодзи-статусов.
     */
    val availableStatuses = listOf("😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "🎉", "💪", "🎧", "🚗", "📚")

    /**
     * Реактивный расчет количества дней вместе.
     */
    val daysTogether: LiveData<Long> = currentUser.map { user ->
        calculateDays(user?.relationshipDate ?: 0L)
    }

    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null
    private var currentPartnerUid: String? = null

    init {
        startListening()
    }

    fun getStatuses(): List<String> {
        return availableStatuses
    }

    /**
     * Подписка на изменения данных пользователя.
     */
    fun startListening() {
        val myUid = auth.currentUser?.uid ?: return
        myListener?.remove()
        myListener = repository.listenToUser(myUid) { user ->
            _currentUser.value = user
            if (user != null) {
                checkDailyBonus(user)
                handlePartnerListener(user.partnerUid)
            }
        }
    }

    /**
     * Подписка на изменения данных партнера.
     */
    private fun handlePartnerListener(partnerUid: String?) {
        if (partnerUid == currentPartnerUid && partnerListener != null) return
        currentPartnerUid = partnerUid
        partnerListener?.remove()

        if (partnerUid != null) {
            partnerListener = repository.listenToUser(partnerUid) { partner ->
                _partnerUser.value = partner
            }
        } else {
            _partnerUser.value = null
        }
    }

    /**
     * Проверка ежедневного бонуса.
     */
    fun checkDailyBonus(user: User) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (localBonusProcessedDate == today) return

        if (user.lastDailyDate < today) {
            localBonusProcessedDate = today
            val dailyBonus = 10L

            repository.updateTreePoints(user.uid, dailyBonus, today)

            _toastMessage.value = "Ежедневный бонус: +$dailyBonus очков! 🌳"
        } else {
            localBonusProcessedDate = today
        }
    }

    /**
     * Отправка приветствия партнеру.
     */
    fun sendHello(partnerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myName =
            _currentUser.value?.name ?: context.getString(R.string.your_partner_default_name)

        val actionData = hashMapOf(
            "type" to "hello",
            "fromUid" to myUid,
            "fromName" to myName,
            "toUid" to partnerUid,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("actions").add(actionData).addOnSuccessListener {
            _toastMessage.value = context.getString(R.string.hello_sent)
        }.addOnFailureListener {
            _toastMessage.value = context.getString(R.string.error_send)
        }
    }

    /**
     * Обновление статуса пользователя.
     */
    fun updateStatus(status: String?) {
        val uid = auth.currentUser?.uid ?: return
        repository.updateStatus(uid, status) {
            _toastMessage.value = context.getString(R.string.error_status_update)
        }
    }

    /**
     * Обновление статуса виджета.
     */
    fun updateWidgetStatus(hasWidget: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        repository.updateWidgetStatus(uid, hasWidget)
    }

    /**
     * Отправка фото партнеру.
     */
    fun sendWidgetPhoto(uri: Uri) {
        val partnerUid = _currentUser.value?.partnerUid

        if (partnerUid == null) {
            _toastMessage.value = context.getString(R.string.no_partner_for_widget)
            return
        }

        val partner = _partnerUser.value

        if (partner == null) {
            _toastMessage.value = context.getString(R.string.partner_data_loading)
            return
        }

        if (partner.hasWidget == false) {
            _toastMessage.value = context.getString(R.string.partner_no_widget_hint)
        }

        _isWidgetLoading.value = true

        val storageRef = storage.reference.child("widget_photos/$partnerUid.jpg")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { url ->
                db.collection("users").document(partnerUid)
                    .update("widgetImageUrl", url.toString()).addOnSuccessListener {
                        _isWidgetLoading.value = false
                        _toastMessage.value = context.getString(R.string.widget_photo_sent)
                    }.addOnFailureListener {
                        _isWidgetLoading.value = false
                        _toastMessage.value = context.getString(R.string.error_db, it.message)
                    }
            }
        }.addOnFailureListener {
            _isWidgetLoading.value = false
            _toastMessage.value = context.getString(R.string.error_upload, it.message)
        }
    }

    /**
     * Обновление записки пользователя.
     */
    fun updateSharedNote(text: String) {
        val uid = auth.currentUser?.uid ?: return
        val partnerUid = _currentUser.value?.partnerUid

        repository.updateSharedNote(
            uid,
            partnerUid,
            text,
            onSuccess = { _toastMessage.value = context.getString(R.string.note_updated) },
            onFailure = { _toastMessage.value = context.getString(R.string.error_db, partnerUid) })
    }

    /**
     * Сохранение даты начала отношений.
     */
    fun saveRelationshipDate(timestamp: Long) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mapOf("relationshipDate" to timestamp)
        db.collection("users").document(uid).update(updates)
        if (currentPartnerUid != null) {
            db.collection("users").document(currentPartnerUid!!).update(updates)
        }
    }

    /**
     * Вычисляет количество дней с даты начала отношений.
     */
    fun calculateDays(timestamp: Long): Long {
        if (timestamp == 0L) return 0
        val diff = System.currentTimeMillis() - timestamp
        return TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
    }

    /**
     * Обновление последнего времени активности пользователя.
     */
    fun updateLastActive() {
        val uid = auth.currentUser?.uid ?: return
        repository.updateLastActive(uid)
    }

    /**
     * Подключение к партнеру.
     */
    fun connectPartner(code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val myUid = auth.currentUser?.uid ?: return
        if (currentPartnerUid != null) {
            onFailure(context.getString(R.string.error_cannot_connect))
            return
        }
        db.collection("users").whereEqualTo("partnerCode", code).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onFailure(context.getString(R.string.error_code_not_found))
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id
                    if (partnerUid == myUid || !partnerDoc.getString("partnerUid").isNullOrEmpty()) {
                        onFailure(context.getString(R.string.error_cannot_connect))
                        return@addOnSuccessListener
                    }
                    val myRef = db.collection("users").document(myUid)
                    val partnerRef = db.collection("users").document(partnerUid)
                    db.runBatch { batch ->
                        batch.update(myRef, "partnerUid", partnerUid)
                        batch.update(partnerRef, "partnerUid", myUid)
                    }.addOnSuccessListener { onSuccess() }.addOnFailureListener {
                        onFailure(
                            context.getString(
                                R.string.error_generic, it.message
                            )
                        )
                    }
                }
            }.addOnFailureListener { onFailure(context.getString(R.string.error_network)) }

    }

    /**
     * Разрыв связи с партнером.
     */
    fun disconnectPartner(partnerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myRef = db.collection("users").document(myUid)
        val partnerRef = db.collection("users").document(partnerUid)
        db.runBatch { batch ->
            batch.update(myRef, "partnerUid", null)
            batch.update(partnerRef, "partnerUid", null)
        }.addOnSuccessListener {
            _toastMessage.value = context.getString(R.string.disconnected)
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        myListener?.remove()
        partnerListener?.remove()
    }
}
