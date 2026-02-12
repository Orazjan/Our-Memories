package com.example.ourmemories.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.data.repositories.AuthRepository
import com.example.ourmemories.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseFirestore.getInstance()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    /**
     * Регистрация пользователя.
     */
    fun register(email: String, pass: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener {
                    createFirestoreUser(it.user)
                        _authSuccess.value = true
                    _isLoading.value = false
                }.addOnFailureListener {
                    _toastMessage.value = "Ошибка: ${it.localizedMessage}"
                    _isLoading.value = false
                    }

            } catch (e: Exception) {
                _toastMessage.value = "Ошибка: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun createFirestoreUser(firebaseUser: FirebaseUser?) {
        if (firebaseUser == null) return
        val userMap = hashMapOf(
            "uid" to firebaseUser.uid, "email" to firebaseUser.email, "name" to "User"
        )
        storage.collection(Constants.COL_USERS).document(firebaseUser.uid).set(userMap)
    }

    fun handleGoogleLogin(idToken: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.signInWithGoogle(idToken)
                _authSuccess.value = true
            } catch (e: Exception) {
                _toastMessage.value = "Ошибка Google: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}