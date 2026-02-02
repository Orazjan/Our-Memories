package com.example.ourmemories.Repositories

import android.net.Uri
import com.example.ourmemories.Models.User
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfileRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getUserStream(): Flow<User?> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(null); return@callbackFlow }

        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val user = snapshot?.toObject(User::class.java)?.copy(uid = uid)
            trySend(user)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getMemoriesCount(uids: List<String>): Int {
        if (uids.isEmpty()) return 0
        val snapshot = db.collection("memories").whereIn("uploaderUid", uids).get().await()
        return snapshot.size()
    }

    suspend fun getWishesCount(uids: List<String>): Int {
        if (uids.isEmpty()) return 0
        val snapshot = db.collection("wishes").whereIn("createdBy", uids).get().await()
        return snapshot.size()
    }

    suspend fun deleteAccount() {
        val user = auth.currentUser ?: throw Exception("No user")
        val uid = user.uid
        db.collection("users").document(uid).delete().await()
        user.delete().await()
    }

    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: throw Exception("No user")
        val email = user.email ?: throw Exception("No email")
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    suspend fun updateAvatar(uri: Uri): String {
        val user = auth.currentUser ?: throw Exception("No user")

        val timestamp = System.currentTimeMillis()
        val ref = storage.reference.child("avatars/${user.uid}_$timestamp.jpg")

        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        val updateProfile = UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(url)).build()
        user.updateProfile(updateProfile).await()


        db.collection("users").document(user.uid).update("photoUrl", url).await()

        return url
    }
}