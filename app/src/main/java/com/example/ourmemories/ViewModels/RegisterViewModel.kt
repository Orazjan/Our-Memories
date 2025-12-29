package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class RegisterViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    fun register(email: String, pass: String) {
        _isLoading.value = true

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    // Отправляем письмо с подтверждением
                    user?.sendEmailVerification()?.addOnCompleteListener { 
                        _toastMessage.value = "Аккаунт создан! Проверьте почту."
                        _authSuccess.value = true
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthWeakPasswordException -> "Пароль слишком простой"
                        is FirebaseAuthUserCollisionException -> "Этот Email уже занят"
                        is FirebaseNetworkException -> "Нет интернета"
                        else -> "Ошибка: ${exception?.localizedMessage}"
                    }
                    _toastMessage.value = errorMessage
                }
            }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
