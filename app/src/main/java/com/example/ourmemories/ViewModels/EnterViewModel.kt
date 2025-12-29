package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * ViewModel для стартовой активности [EnterActivity].
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

    fun checkUser(isFirstRun: Boolean) {
        viewModelScope.launch {
            delay(1000) // Имитация загрузки для сплэша

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
                withTimeout(3000L) {
                    try { user.reload().await() } catch (e: Exception) { }

                    val doc = db.collection("users").document(user.uid).get().await()

                    if (doc.exists()) {
                        _navigationState.value = NavigationState.NavigateToMain
                    } else {
                        _isChecking.value = false
                        _navigationState.value = NavigationState.NavigateToSetupProfile
                    }
                }
            } catch (e: Exception) {
                // Если таймаут или ошибка - пускаем в главное меню (оффлайн режим или повторная попытка там)
                _navigationState.value = NavigationState.NavigateToMain
            }
        }
    }

    fun onAuthSuccess() {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                try {
                    val doc = db.collection("users").document(user.uid).get().await()
                    if (doc.exists()) {
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
