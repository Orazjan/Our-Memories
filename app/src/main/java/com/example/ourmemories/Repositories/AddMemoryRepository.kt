package com.example.ourmemories.Repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AddMemoryRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    open fun getCurrentUserUid(): String? = auth.currentUser?.uid

    open suspend fun uploadImageBytes(data: ByteArray, uid: String): String {
        val fileName = UUID.randomUUID().toString()
        val ref = storage.reference.child("memories/$uid/$fileName.jpg")
        ref.putBytes(data).await()
        return ref.downloadUrl.await().toString()
    }

    open suspend fun addMemory(memoryMap: Map<String, Any>) {
        db.collection("memories").add(memoryMap).await()
    }

    open fun incrementUserPoints(uid: String, points: Long) {
        db.collection("users").document(uid).update("treePoints", FieldValue.increment(points))
    }
}