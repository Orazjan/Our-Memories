package com.example.ourmemories.data.repositories

import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class EditProfileRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    /**
     * Получает UID текущего авторизованного пользователя.
     */
    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Загружает данные пользователя из Firestore и обновляет LiveData.
     */
    fun loadUser(
        currentName: MutableLiveData<String>,
        currentPhotoUrl: MutableLiveData<String?>,
        currentBirthDate: MutableLiveData<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure("User not found")
            return
        }

        currentName.value = user.displayName ?: ""
        currentPhotoUrl.value = user.photoUrl?.toString()

        db.collection(Constants.COL_USERS).document(user.uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val birthDate = doc.getString("birthDate") ?: ""
                currentBirthDate.value = birthDate

                val nameInDb = doc.getString("name")
                if (!nameInDb.isNullOrEmpty()) {
                    currentName.value = nameInDb!!
                }

                val photoInDb = doc.getString("photoUrl")
                if (!photoInDb.isNullOrEmpty()) {
                    currentPhotoUrl.value = photoInDb
                }
            }
            onSuccess()
        }.addOnFailureListener {
            onFailure(it.message ?: "Error loading user data")
        }
    }

    /**
     * Обновляет данные пользователя в Firestore.
     */
    suspend fun uploadAvatar(uid: String, imageData: ByteArray): String {
        val timestamp = System.currentTimeMillis()
        val fileName = "${Constants.STORAGE_AVATARS}/${uid}_${timestamp}.jpg"

        val storageRef = storage.reference.child(fileName)
        storageRef.putBytes(imageData).await()
        return storageRef.downloadUrl.await().toString()
    }

    /**
     * Обновляет данные профиля пользователя в Firebase Authentication.
     */
    suspend fun updateAuthProfile(name: String, photoUrl: String?) {
        val user = auth.currentUser ?: return
        val updatesBuilder = UserProfileChangeRequest.Builder().setDisplayName(name)
        if (photoUrl != null) {
            updatesBuilder.setPhotoUri(photoUrl.toUri())
        }
        user.updateProfile(updatesBuilder.build()).await()
    }

    /**
     * Обновляет данные пользователя в Firestore.
     */
    suspend fun saveUserToFirestore(uid: String, data: Map<String, Any>) {
        db.collection(Constants.COL_USERS).document(uid).set(data, SetOptions.merge()).await()
    }
}