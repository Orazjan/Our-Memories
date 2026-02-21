package com.example.ourmemories.data.repositories

import com.example.ourmemories.utils.Constants
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
     * Регистрация: создает аккаунт в Auth и сразу записывает профиль в Firestore.
     */
    suspend fun register(email: String, pass: String) {
        val authResult = auth.createUserWithEmailAndPassword(email, pass).await()

        val user = authResult.user ?: throw Exception("Не удалось получить данные пользователя")

        val userMap = hashMapOf(
            "uid" to user.uid, "email" to user.email, "name" to "User"
        )

        db.collection(Constants.COL_USERS).document(user.uid).set(userMap).await()
    }

    /**
     * Вход через Google и сохранение/обновление пользователя в Firestore.
     */
    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()

        val user = authResult.user ?: throw Exception("Ошибка: пользователь Google равен null")

        val userMap = hashMapOf(
            "uid" to user.uid, "email" to user.email, "name" to (user.displayName ?: "User")
        )

        db.collection(Constants.COL_USERS).document(user.uid).set(userMap, SetOptions.merge())
            .await()
    }
}