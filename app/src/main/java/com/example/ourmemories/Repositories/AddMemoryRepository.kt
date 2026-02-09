package com.example.ourmemories.Repositories

import com.example.ourmemories.Utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

open class AddMemoryRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    open fun getCurrentUserUid(): String? = auth.currentUser?.uid

    open suspend fun uploadImageBytes(data: ByteArray, uid: String): String {
        val fileName = UUID.randomUUID().toString()
        val ref = storage.reference.child("${Constants.COL_MEMORIES}/$uid/$fileName.jpg")
        ref.putBytes(data).await()
        return ref.downloadUrl.await().toString()
    }

    open suspend fun addMemory(memoryMap: Map<String, Any>) {
        db.collection(Constants.COL_MEMORIES).add(memoryMap).await()
    }

    open fun incrementUserPoints(uid: String, points: Long) {
        db.collection(Constants.COL_USERS).document(uid)
            .update("treePoints", FieldValue.increment(points))
    }
}