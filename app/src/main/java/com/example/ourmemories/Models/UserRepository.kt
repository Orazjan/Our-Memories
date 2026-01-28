package com.example.ourmemories.Models

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Репозиторий для управления данными пользователя.
 * Изолирует работу с Firebase Firestore от ViewModel.
 */
class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Получает UID текущего авторизованного пользователя.
     */
    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Подписывается на обновления документа пользователя в реальном времени.
     */
    fun listenToUser(uid: String, onDataCallback: (User?) -> Unit): ListenerRegistration {
        return db.collection("users").document(uid).addSnapshotListener { document, e ->
            if (e != null || document == null || !document.exists()) {
                onDataCallback(null)
                return@addSnapshotListener
            }
            val user = document.toObject(User::class.java)?.copy(uid = uid)
            onDataCallback(user)
        }
    }

    /**
     * Обновляет статус наличия виджета (для отображения партнеру).
     */
    fun updateWidgetStatus(uid: String, hasWidget: Boolean) {
        db.collection("users").document(uid).update("hasWidget", hasWidget)
    }

    /**
     * Обновляет время последней активности ("Был в сети").
     */
    fun updateLastActive(uid: String) {
        db.collection("users").document(uid).update("lastActive", System.currentTimeMillis())
    }

    /**
     * Начисляет очки дереву любви и обновляет дату последнего бонуса.
     */
    fun updateTreePoints(uid: String, pointsToAdd: Long, lastDailyDate: Long) {
        db.collection("users").document(uid).update(
            mapOf(
                "treePoints" to FieldValue.increment(pointsToAdd), "lastDailyDate" to lastDailyDate
            )
        )
    }

    /**
     * Обновляет эмодзи-статус.
     */
    fun updateStatus(uid: String, status: String?, onFailure: () -> Unit) {
        val updates = if (status == null) mapOf("status" to FieldValue.delete())
        else mapOf("status" to status)

        db.collection("users").document(uid).update(updates).addOnFailureListener { onFailure() }
    }

    /**
     * Обновляет общую записку ("На холодильнике") сразу у обоих партнеров.
     */
    fun updateSharedNote(
        uid: String, partnerUid: String?, text: String, onSuccess: () -> Unit, onFailure: () -> Unit
    ) {
        val batch = db.batch()
        val updates = hashMapOf<String, Any>("sharedNote" to text)

        batch.update(db.collection("users").document(uid), updates)

        if (partnerUid != null) {
            batch.update(db.collection("users").document(partnerUid), updates)
        }

        batch.commit().addOnSuccessListener { onSuccess() }.addOnFailureListener { onFailure() }
    }
}