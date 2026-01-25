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
 * - Загрузку данных пользователя и статистики (количество фото, желаний).
 * - Выход и удаление аккаунта.
 */
class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _memoriesCount = MutableLiveData(0)
    val memoriesCount: LiveData<Int> = _memoriesCount

    private val _wishesCount = MutableLiveData(0)
    val wishesCount: LiveData<Int> = _wishesCount

    private val _actionState = MutableLiveData<ActionState>()
    val actionState: LiveData<ActionState> = _actionState

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private var userListener: ListenerRegistration? = null

    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        object NavigateToLogin : ActionState()
        class ReAuthNeeded(val email: String) : ActionState()
    }

    init {
        loadUserProfile()
    }

    /**
     * Загружает профиль пользователя.
     */
    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return

        userListener = db.collection("users").document(uid).addSnapshotListener { doc, e ->
            if (e != null) return@addSnapshotListener
            if (doc != null && doc.exists()) {
                val userObj = doc.toObject(User::class.java)?.copy(uid = uid)
                _user.value = userObj

                loadStatistics(uid, userObj?.partnerUid)
            }
        }
    }

    /**
     * Загружает количество воспоминаний и желаний для пары.
     */
    private fun loadStatistics(myUid: String, partnerUid: String?) {
        val uids = mutableListOf(myUid)
        if (partnerUid != null) uids.add(partnerUid)

        db.collection("memories").whereIn("uploaderUid", uids).get()
            .addOnSuccessListener { _memoriesCount.value = it.size() }

        db.collection("wishes").whereIn("createdBy", uids).get()
            .addOnSuccessListener { _wishesCount.value = it.size() }
    }

    /**
     * Выход из аккаунта.
     */
    fun logout() {
        auth.signOut()
        _actionState.value = ActionState.NavigateToLogin
    }

    /**
     * Удаление аккаунта.
     */
    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _actionState.value = ActionState.Loading

        db.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener { _actionState.value = ActionState.NavigateToLogin }
                    .addOnFailureListener { e ->
                        if (e is FirebaseAuthRecentLoginRequiredException) {
                            _actionState.value = ActionState.ReAuthNeeded(user.email ?: "")
                        } else {
                            _actionState.value = ActionState.Idle
                            _toastMessage.value = e.message
                        }
                    }
            }
    }

    /**
     * TODO добавить
     * Переавторизация и удаление аккаунта.
     */
    fun reauthenticateAndDelete(password: String) {
        val user = auth.currentUser ?: return
        val credential = EmailAuthProvider.getCredential(user.email!!, password)
        _actionState.value = ActionState.Loading

        user.reauthenticate(credential).addOnSuccessListener {
            deleteAccount()
        }.addOnFailureListener {
            _actionState.value = ActionState.Idle
            _toastMessage.value = "Неверный пароль"
        }
    }

    fun onToastShown() { _toastMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
    }
}
