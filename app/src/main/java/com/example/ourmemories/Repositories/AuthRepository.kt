package com.example.ourmemories.Repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /**
     * Обычный вход по почте и паролю
     */
    suspend fun login(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).await()
    }

    /**
     * Вход через Google и сохранение пользователя в Firestore
     */
    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()

        val user = authResult.user
        if (user != null) {
            val userMap = hashMapOf(
                "uid" to user.uid, "email" to user.email, "name" to (user.displayName ?: "User")
            )

            db.collection("users").document(user.uid).set(userMap, SetOptions.merge()).await()
        }
    }
}