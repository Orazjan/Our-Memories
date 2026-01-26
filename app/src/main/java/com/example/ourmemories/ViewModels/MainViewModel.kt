package com.example.ourmemories.ViewModels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.ourmemories.Models.TreeInfo
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ViewModel для главного экрана [com.example.ourmemories.Fragments.MainFragment].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()
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
     * Реактивный расчет состояния дерева на основе очков текущего пользователя.
     */
    val treeInfo: LiveData<TreeInfo?> = currentUser.map { user ->
        user?.let { getTreeInfo(it.treePoints) }
    }

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
        myListener = db.collection("users").document(myUid).addSnapshotListener { document, e ->
            if (e != null) {
                Log.e(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            if (document != null && document.exists()) {
                val user = document.toObject(User::class.java)?.copy(uid = myUid)
                _currentUser.value = user
                if (user != null) {
                    checkDailyBonus(user)
                    handlePartnerListener(user.partnerUid)
                }
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
            partnerListener = db.collection("users").document(partnerUid).addSnapshotListener { doc, e ->
                if (e != null) return@addSnapshotListener
                if (doc != null && doc.exists()) {
                    val partner = doc.toObject(User::class.java)?.copy(uid = partnerUid)
                    _partnerUser.value = partner
                }
            }
        } else {
            _partnerUser.value = null
        }
    }

    /**
     * Проверка ежедневного бонуса.
     */
    private fun checkDailyBonus(user: User) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (user.lastDailyDate < today) {
            val dailyBonus = 10L
            db.collection("users").document(user.uid).update(
                mapOf("treePoints" to FieldValue.increment(dailyBonus), "lastDailyDate" to today)
            ).addOnSuccessListener {
                _toastMessage.value = context.getString(R.string.daily_bonus_toast, dailyBonus)
            }
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
        val updates =
            if (status == null) mapOf("status" to FieldValue.delete()) else mapOf("status" to status)
        db.collection("users").document(uid).update(updates)
        _toastMessage.value = context.getString(R.string.error_status_update)
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

        if (!partner.hasWidget) {
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
        val updates = hashMapOf<String, Any>("sharedNote" to text)
        val batch = db.batch()
        batch.update(db.collection("users").document(uid), updates)
        if (currentPartnerUid != null) {
            batch.update(db.collection("users").document(currentPartnerUid!!), updates)
        }
        batch.commit()
            .addOnSuccessListener { _toastMessage.value = context.getString(R.string.note_updated) }
            .addOnFailureListener { _toastMessage.value = context.getString(R.string.error_save) }
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
     * Логика определения уровня дерева.
     */
    fun getTreeInfo(points: Long): TreeInfo {
        val (nameResId, iconRes, maxPoints) = when {
            points >= 1000 -> Triple(
                R.string.tree_stage_eternal, R.drawable.ic_tree_stage_10, 2000L
            )

            points >= 800 -> Triple(R.string.tree_stage_magic, R.drawable.ic_tree_stage_9, 1000L)
            points >= 650 -> Triple(R.string.tree_stage_abundant, R.drawable.ic_tree_stage_8, 800L)
            points >= 500 -> Triple(R.string.tree_stage_love, R.drawable.ic_tree_stage_7, 650L)
            points >= 350 -> Triple(R.string.tree_stage_blooming, R.drawable.ic_tree_stage_6, 500L)
            points >= 200 -> Triple(R.string.tree_stage_mature, R.drawable.ic_tree_stage_5, 350L)
            points >= 150 -> Triple(R.string.tree_stage_strong, R.drawable.ic_tree_stage_4, 200L)
            points >= 100 -> Triple(R.string.tree_stage_young, R.drawable.ic_tree_stage_3, 150L)
            points >= 50 -> Triple(R.string.tree_stage_sapling, R.drawable.ic_tree_stage_2, 50L)
            else -> Triple(R.string.tree_stage_sprout, R.drawable.ic_tree_stage_1, 20L)
        }

        return TreeInfo(nameResId, iconRes, points, maxPoints)
    }

    /**
     * Получение знака зодиака по дате рождения.
     */
    fun getZodiacSign(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        try {
            val parts = dateString.split(".")
            val day = parts[0].toInt()
            val month = parts[1].toInt()

            return when (month) {
                1 -> if (day < 20) "♑" else "♒"
                2 -> if (day < 19) "♒" else "♓"
                3 -> if (day < 21) "♓" else "♈"
                4 -> if (day < 20) "♈" else "♉"
                5 -> if (day < 21) "♉" else "♊"
                6 -> if (day < 21) "♊" else "♋"
                7 -> if (day < 23) "♋" else "♌"
                8 -> if (day < 23) "♌" else "♍"
                9 -> if (day < 23) "♍" else "♎"
                10 -> if (day < 23) "♎" else "♏"
                11 -> if (day < 22) "♏" else "♐"
                12 -> if (day < 22) "♐" else "♑"
                else -> ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * Обновление последнего времени активности пользователя.
     */
    fun updateLastActive() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("lastActive", System.currentTimeMillis())
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
