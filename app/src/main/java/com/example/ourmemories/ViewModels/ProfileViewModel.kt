package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.ourmemories.Models.User
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * ViewModel для экрана профиля [ProfileFragment].
 *
 * Отвечает за:
 * - Отображение данных профиля (имя, фото, код партнера).
 * - Выход из аккаунта (Logout).
 * - Удаление аккаунта (включая обработку повторной авторизации при необходимости).
 */
class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _actionState = MutableLiveData<ActionState>()
    val actionState: LiveData<ActionState> = _actionState

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var userListener: ListenerRegistration? = null

    /**
     * Состояния для событий профиля.
     */
    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        object NavigateToLogin : ActionState()
        class ReAuthNeeded(val email: String) : ActionState()
    }

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _user.value = null
            return
        }

        userListener = db.collection("users").document(uid).addSnapshotListener { doc, e ->
            if (e != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val userObj = doc.toObject(User::class.java)?.copy(uid = uid)
                val authUser = auth.currentUser
                val finalUser = userObj?.copy(
                    name = if (userObj.name.isEmpty()) authUser?.displayName ?: "Пользователь" else userObj.name,
                    photoUrl = if (userObj.photoUrl == null) authUser?.photoUrl?.toString() else userObj.photoUrl
                )
                _user.value = finalUser
            }
        }
    }

    fun logout() {
        auth.signOut()
        _actionState.value = ActionState.NavigateToLogin
    }

    /**
     * Попытка удалить аккаунт пользователя.
     * Сначала удаляет данные из Firestore, затем сам аккаунт Auth.
     */
    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _actionState.value = ActionState.Loading

        db.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        _actionState.value = ActionState.NavigateToLogin
                        _toastMessage.value = "Аккаунт удален"
                    }
                    .addOnFailureListener { e ->
                        if (e is FirebaseAuthRecentLoginRequiredException) {
                            _actionState.value = ActionState.ReAuthNeeded(user.email ?: "")
                        } else {
                            _actionState.value = ActionState.Idle
                            _toastMessage.value = "Ошибка: ${e.message}"
                        }
                    }
            }
            .addOnFailureListener { e ->
                _actionState.value = ActionState.Idle
                _toastMessage.value = "Ошибка удаления данных: ${e.message}"
            }
    }

    /**
     * Повторная авторизация (если токен устарел) и последующее удаление аккаунта.
     */
    fun reauthenticateAndDelete(password: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        _actionState.value = ActionState.Loading
        val credential = EmailAuthProvider.getCredential(email, password)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        _actionState.value = ActionState.NavigateToLogin
                        _toastMessage.value = "Аккаунт удален"
                    }
                    .addOnFailureListener { e ->
                        _actionState.value = ActionState.Idle
                        _toastMessage.value = "Ошибка удаления: ${e.message}"
                    }
            }
            .addOnFailureListener { e ->
                _actionState.value = ActionState.Idle
                _toastMessage.value = "Неверный пароль"
            }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
    }
}
