package com.example.ourmemories.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Информация о состоянии "Дерева Любви".
 */
data class TreeInfo(
    val levelName: String, val iconRes: Int, val points: Long, val maxPoints: Int
)

/**
 * ViewModel для главного экрана [com.example.ourmemories.Fragments.MainFragment].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "MainViewModel"

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

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
                _toastMessage.value = "Ежедневный бонус: +$dailyBonus очков! 🌳"
            }
        }
    }

    /**
     * Отправка приветствия партнеру.
     */
    fun sendHello(uid: String) {
        val uid = auth.currentUser?.uid ?: return
    }

    /**
     * Обновление статуса пользователя.
     */
    fun updateStatus(status: String?) {
        val uid = auth.currentUser?.uid ?: return
        val updates =
            if (status == null) mapOf("status" to FieldValue.delete()) else mapOf("status" to status)
        db.collection("users").document(uid).update(updates)
            .addOnFailureListener { _toastMessage.value = "Ошибка обновления статуса" }
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
            .addOnSuccessListener { _toastMessage.value = "Записка обновлена!" }
            .addOnFailureListener { _toastMessage.value = "Ошибка сохранения" }
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
        val (levelName, iconRes, maxPoints) = when {
            points >= 1000 -> Triple("Древо Вечной Любви", R.drawable.ic_tree_stage_10, 2000)
            points >= 800 -> Triple("Волшебное Дерево", R.drawable.ic_tree_stage_9, 1000)
            points >= 650 -> Triple("Изобильное Дерево", R.drawable.ic_tree_stage_8, 800)
            points >= 500 -> Triple("Древо Любви", R.drawable.ic_tree_stage_7, 650)
            points >= 350 -> Triple("Цветущее Дерево", R.drawable.ic_tree_stage_6, 500)
            points >= 200 -> Triple("Взрослое Дерево", R.drawable.ic_tree_stage_5, 350)
            points >= 100 -> Triple("Крепкое Дерево", R.drawable.ic_tree_stage_4, 200)
            points >= 50 -> Triple("Молодое Дерево", R.drawable.ic_tree_stage_3, 100)
            points >= 20 -> Triple("Саженец", R.drawable.ic_tree_stage_2, 50)
            else -> Triple("Росток", R.drawable.ic_tree_stage_1, 20)
        }
        return TreeInfo(levelName, iconRes, points, maxPoints)
    }

    /**
     * Подключение к партнеру.
     */
    fun connectPartner(code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val myUid = auth.currentUser?.uid ?: return
        if (currentPartnerUid != null) {
            onFailure("У вас уже есть партнер!")
            return
        }
        db.collection("users").whereEqualTo("partnerCode", code).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onFailure("Код не найден")
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id
                    if (partnerUid == myUid || !partnerDoc.getString("partnerUid").isNullOrEmpty()) {
                        onFailure("Невозможно подключиться")
                        return@addOnSuccessListener
                    }
                    val myRef = db.collection("users").document(myUid)
                    val partnerRef = db.collection("users").document(partnerUid)
                    db.runBatch { batch ->
                        batch.update(myRef, "partnerUid", partnerUid)
                        batch.update(partnerRef, "partnerUid", myUid)
                    }.addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onFailure(it.message ?: "Ошибка") }
                }
            }.addOnFailureListener { onFailure(it.message ?: "Ошибка сети") }
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
        }.addOnSuccessListener { _toastMessage.value = "Связь разорвана" }
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
