package com.example.ourmemories.Repositories

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class SetupProfileRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    fun getCurrentUser() = auth.currentUser

    /**
     * Загружает аватар и возвращает URL.
     */
    suspend fun uploadAvatar(uid: String, data: ByteArray): String {
        val timestamp = System.currentTimeMillis()
        val ref = storage.reference.child("avatars/${uid}_$timestamp.jpg")
        ref.putBytes(data).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Обновляет профиль авторизации (имя и фото).
     */
    suspend fun updateAuthProfile(user: FirebaseUser, name: String, photoUrl: String) {
        val updates =
            UserProfileChangeRequest.Builder().setDisplayName(name).setPhotoUri(Uri.parse(photoUrl))
                .build()
        user.updateProfile(updates).await()
    }

    /**
     * Проверяет, свободен ли код партнера.
     * Возвращает true, если код уникален (свободен).
     */
    suspend fun isCodeUnique(code: String): Boolean {
        val snapshot = db.collection("partner_codes").document(code).get().await()
        return !snapshot.exists()
    }

    /**
     * Сохраняет данные пользователя и резервирует код партнера (Batch write).
     * Всё выполняется атомарно: либо всё сохранится, либо ничего.
     */
    suspend fun saveUserProfile(
        uid: String, userData: Map<String, Any?>, code: String, codeData: Map<String, Any?>
    ) {
        val batch = db.batch()

        val userRef = db.collection("users").document(uid)
        val codeRef = db.collection("partner_codes").document(code)

        batch.set(userRef, userData)
        batch.set(codeRef, codeData)

        batch.commit().await()
    }
}