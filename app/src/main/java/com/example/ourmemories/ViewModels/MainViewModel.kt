package com.example.ourmemories.ViewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.Models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

/**
 * ViewModel для главного экрана [MainFragment].
 *
 * Отвечает за:
 * - Загрузку данных текущего пользователя и партнера в реальном времени.
 * - Управление статусами и общей заметкой.
 * - Подключение и отключение партнера.
 * - Начисление ежедневных бонусов (очков дерева).
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

    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null
    private var currentPartnerUid: String? = null

    init {
        startListening()
    }

    /**
     * Запускает прослушивание документа текущего пользователя в Firestore.
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
     * Управляет подпиской на обновления данных партнера.
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
     * Проверяет и начисляет ежедневный бонус за вход.
     */
    private fun checkDailyBonus(user: User) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (user.lastDailyDate < today) {
            val dailyBonus = 10L
            db.collection("users").document(user.uid).update(
                mapOf(
                    "treePoints" to FieldValue.increment(dailyBonus),
                    "lastDailyDate" to today
                )
            ).addOnSuccessListener {
                _toastMessage.value = "Ежедневный бонус: +$dailyBonus очков! 🌳"
            }
        }
    }

    /**
     * Обновляет статус пользователя (эмодзи).
     * @param status Новый статус или null для удаления.
     */
    fun updateStatus(status: String?) {
        val uid = auth.currentUser?.uid ?: return
        val updates = if (status == null) 
            mapOf("status" to FieldValue.delete()) 
        else 
            mapOf("status" to status)
        
        db.collection("users").document(uid).update(updates)
            .addOnFailureListener { _toastMessage.value = "Ошибка обновления статуса" }
    }

    /**
     * Обновляет общую записку ("На холодильнике").
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
     * Сохраняет дату начала отношений.
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
     * Попытка подключиться к партнеру по коду.
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
                    }.addOnSuccessListener {
                        onSuccess()
                    }.addOnFailureListener { e ->
                        onFailure(e.message ?: "Ошибка")
                    }
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Ошибка сети")
            }
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
            _toastMessage.value = "Связь разорвана"
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
