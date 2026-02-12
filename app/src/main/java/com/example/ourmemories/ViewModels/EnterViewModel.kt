package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.Utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * ViewModel для стартовой активности [com.example.ourmemories.EnterActivity].
 *
 * Определяет, какой экран показать при запуске приложения:
 * - Сплэш-скрин (проверка авторизации).
 * - Онбординг (первый запуск).
 * - Вход (не авторизован).
 * - Настройка профиля (авторизован, но профиль не заполнен).
 * - Главный экран (всё ок).
 */
class EnterViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    sealed class NavigationState {
        object Idle : NavigationState()
        object NavigateToMain : NavigationState()
        object NavigateToSetupProfile : NavigationState()
        object NavigateToLogin : NavigationState()
        object NavigateToOnboarding : NavigationState()
    }

    private val _navigationState = MutableLiveData<NavigationState>(NavigationState.Idle)
    val navigationState: LiveData<NavigationState> = _navigationState

    private val _isChecking = MutableLiveData(true)
    val isChecking: LiveData<Boolean> = _isChecking

    /**
     * Проверка авторизации.
     */
    fun checkUser(isFirstRun: Boolean) {
        viewModelScope.launch {
            val user = auth.currentUser

            if (user == null) {
                _isChecking.value = false
                if (isFirstRun) {
                    _navigationState.value = NavigationState.NavigateToOnboarding
                } else {
                    _navigationState.value = NavigationState.NavigateToLogin
                }
                return@launch
            }

            try {
                withTimeout(200) {

                    val doc = db.collection(Constants.COL_USERS).document(user.uid).get().await()
                    val birthDate = doc.getString("birthDate")

                    if (doc.exists() && !birthDate.isNullOrEmpty()) {
                        _navigationState.value = NavigationState.NavigateToMain
                    } else {
                        _isChecking.value = false
                        _navigationState.value = NavigationState.NavigateToSetupProfile
                    }
                }
            } catch (e: Exception) {
                _navigationState.value = NavigationState.NavigateToMain
            }
        }
    }

    /**
     * При успешной авторизации.
     */
    fun onAuthSuccess() {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                try {
                    val doc = db.collection(Constants.COL_USERS).document(user.uid).get().await()
                    val birthDate = doc.getString("birthDate")

                    if (doc.exists() && !birthDate.isNullOrEmpty()) {
                        _navigationState.value = NavigationState.NavigateToMain
                    } else {
                        _navigationState.value = NavigationState.NavigateToSetupProfile
                    }
                } catch (e: Exception) {
                    _navigationState.value = NavigationState.NavigateToSetupProfile
                }
            }
        }
    }
}
