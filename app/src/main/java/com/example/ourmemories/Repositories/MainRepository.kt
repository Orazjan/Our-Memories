package com.example.ourmemories.Repositories

import android.net.Uri
import com.example.ourmemories.Models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage

/**
 * Репозиторий для управления данными пользователя.
 * Изолирует работу с Firebase Firestore от ViewModel.
 */
class MainRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

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
    fun saveRelationshipDate(uid: String, partnerUid: String?, timestamp: Long) {
        val updates = mapOf("relationshipDate" to timestamp)
        db.collection("users").document(uid).update(updates)
        if (partnerUid != null) db.collection("users").document(partnerUid).update(updates)
    }

    fun sendHello(
        myUid: String,
        myName: String,
        partnerUid: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val actionData = hashMapOf(
            "type" to "hello",
            "fromUid" to myUid,
            "fromName" to myName,
            "toUid" to partnerUid,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("actions").add(actionData).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure() }
    }

    fun uploadWidgetPhoto(
        partnerUid: String, uri: Uri, onSuccess: () -> Unit, onFailure: (String) -> Unit
    ) {
        val storageRef = storage.reference.child("widget_photos/$partnerUid.jpg")
        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { url ->
                db.collection("users").document(partnerUid).update("widgetImageUrl", url.toString())
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(it.message ?: "") }
            }
        }.addOnFailureListener { onFailure(it.message ?: "") }
    }

    fun connectPartner(
        myUid: String, code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit
    ) {
        db.collection("users").whereEqualTo("partnerCode", code).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    onFailure("CODE_NOT_FOUND")
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id
                    if (partnerUid == myUid) {
                        onFailure("CANT_ADD_SELF"); return@addOnSuccessListener
                    }
                    if (!partnerDoc.getString("partnerUid").isNullOrEmpty()) {
                        onFailure("USER_BUSY"); return@addOnSuccessListener
                    }

                    val batch = db.batch()
                    batch.update(db.collection("users").document(myUid), "partnerUid", partnerUid)
                    batch.update(db.collection("users").document(partnerUid), "partnerUid", myUid)
                    batch.commit().addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onFailure(it.message ?: "") }
                }
            }.addOnFailureListener { onFailure(it.message ?: "") }
    }

    fun disconnectPartner(myUid: String, partnerUid: String, onSuccess: () -> Unit) {
        val batch = db.batch()
        batch.update(db.collection("users").document(myUid), "partnerUid", null)
        batch.update(db.collection("users").document(partnerUid), "partnerUid", null)
        batch.commit().addOnSuccessListener { onSuccess() }
    }
}