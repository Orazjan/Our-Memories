package com.example.ourmemories.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.R
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val context = application.applicationContext
    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _resetSuccess = MutableLiveData<Boolean>()
    val resetSuccess: LiveData<Boolean> = _resetSuccess

    /**
     * Запрос на сброс пароля.
     */
    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _toastMessage.value = context.getString(R.string.enter_your_email)
            return
        }

        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                _toastMessage.value = context.getString(R.string.reset_email_sent, email)
                _resetSuccess.value = true
            } else {
                val errorMsg = task.exception?.message ?: "Unknown error"
                _toastMessage.value = context.getString(R.string.error_generic, errorMsg)
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
