package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _resetSuccess = MutableLiveData<Boolean>()
    val resetSuccess: LiveData<Boolean> = _resetSuccess

    /**
     * Запрос на сброс пароля.
     */
    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _toastMessage.value = "Введите email"
            return
        }

        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _toastMessage.value = "Проверьте почту $email"
                _resetSuccess.value = true
            } else {
                _toastMessage.value = "Ошибка: ${task.exception?.message}"
            }
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onResetSuccessHandled() {
        _resetSuccess.value = false
    }
}
