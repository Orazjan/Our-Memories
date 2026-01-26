package com.example.ourmemories.ViewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val context = application.applicationContext

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loginSuccess = MutableLiveData<Boolean>()
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    /**
     * Логин пользователя.
     */
    fun login(email: String, pass: String) {
        _isLoading.value = true

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    _loginSuccess.value = true
                } else {
                    val exception = task.exception
                    // Используем ресурсы для локализации сообщений об ошибках
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> context.getString(R.string.error_user_not_found)
                        is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.error_invalid_credentials)
                        is FirebaseNetworkException -> context.getString(R.string.error_no_internet)
                        else -> context.getString(
                            R.string.error_generic, exception?.localizedMessage
                        )
                    }
                    _toastMessage.value = errorMessage
                }
            }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
