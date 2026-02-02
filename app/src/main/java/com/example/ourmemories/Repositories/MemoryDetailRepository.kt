package com.example.ourmemories.Repositories

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MemoryDetailRepository {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()


    fun getMemoryDetail(
        memoryId: String, onSuccess: (
            title: String, desc: String, time: Long, cover: String, images: List<String>
        ) -> Unit, onFailure: (String) -> Unit
    ) {
        db.collection("memories").document(memoryId).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val title = document.getString("title") ?: ""
                val description = document.getString("description") ?: ""
                val timestamp = document.getLong("timestamp") ?: 0L
                val imageUrl = document.getString("imageUrl") ?: ""

                val images = try {
                    document.get("images") as? List<String> ?: emptyList()
                } catch (e: Exception) {
                    e.stackTrace
                    emptyList<String>()
                }

                val finalImages = if (images.isEmpty() && imageUrl.isNotEmpty()) {
                    listOf(imageUrl)
                } else {
                    images
                }

                onSuccess(title, description, timestamp, imageUrl, finalImages)
            } else {
                onFailure("Document not found")
            }
        }.addOnFailureListener {
            onFailure(it.message ?: "Error loading memory")
        }
    }


    fun deletePhoto(memoryId: String, url: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        db.collection("memories").document(memoryId).update("images", FieldValue.arrayRemove(url))
            .addOnSuccessListener {
                try {
                    storage.getReferenceFromUrl(url).delete()
                } catch (e: Exception) {
                    e.stackTrace
                    Log.e("Repo", "Error deleting file: $url", e)
                }
                onSuccess()
            }.addOnFailureListener {
                onFailure()
            }
    }

    fun updateCoverUrl(memoryId: String, newUrl: String) {
        db.collection("memories").document(memoryId).update("imageUrl", newUrl)
    }

    fun deleteAlbum(memoryId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("memories").document(memoryId).delete().addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun cleanupStorage(urls: List<String>) {
        urls.forEach { url ->
            try {
                storage.getReferenceFromUrl(url).delete()
            } catch (e: Exception) {
                Log.e("MemoryDetailViewModel", "Не удалось удалить фото: $url", e)
            }
        }
    }

    fun saveChanges(
        newTitle: String,
        newDesc: String,
        newTimestamp: Long,
        memoryId: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        db.collection("memories").document(memoryId).update(
            mapOf("title" to newTitle, "description" to newDesc, "timestamp" to newTimestamp)
        ).addOnSuccessListener { onSuccess() }.addOnFailureListener { onFailure() }
    }

    fun setCover(url: String, memoryId: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        db.collection("memories").document(memoryId).update("imageUrl", url)
            .addOnSuccessListener { onSuccess() }.addOnFailureListener { onFailure() }
    }
}