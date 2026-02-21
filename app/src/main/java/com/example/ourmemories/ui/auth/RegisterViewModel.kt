package com.example.ourmemories.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.data.repositories.AuthRepository
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val repository = AuthRepository()

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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("REG_TEST", "ViewModel: Вызов repository.register")

                repository.register(email, pass)

                Log.d("REG_TEST", "ViewModel: Успех, профиль создан")
                _authSuccess.value = true

            } catch (e: Exception) {
                Log.e("REG_TEST", "ViewModel Error: ${e.message}")
                _toastMessage.value = "Ошибка: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun handleGoogleLogin(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("REG_TEST", "Google регистрация: старт")
                repository.signInWithGoogle(idToken)

                _authSuccess.value = true
                Log.d("REG_TEST", "Google регистрация: успех")
            } catch (e: Exception) {
                Log.e("REG_TEST", "Google регистрация ошибка: ${e.message}")
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