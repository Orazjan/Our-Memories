package com.example.ourmemories.ViewModels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.Models.User
import com.example.ourmemories.Repositories.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана профиля [com.example.ourmemories.Fragments.ProfileFragment].
 *
 * Отвечает за:
 * - Загрузку данных пользователя и статистики (количество фото, желаний).
 * - Выход и удаление аккаунта.
 */
class ProfileViewModel(
    application: Application, private val repository: ProfileRepository
) : AndroidViewModel(application) {
    sealed class ActionState {
        object Idle : ActionState()
        object Loading : ActionState()
        object NavigateToLogin : ActionState()
        class ReAuthNeeded(val email: String) : ActionState()
    }

    private val _actionState = MutableLiveData<ActionState>(ActionState.Idle)
    val actionState: LiveData<ActionState> = _actionState

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _memoriesCount = MutableLiveData(0)
    val memoriesCount: LiveData<Int> = _memoriesCount

    private val _wishesCount = MutableLiveData(0)
    val wishesCount: LiveData<Int> = _wishesCount

    val user: LiveData<User?> = repository.getUserStream().asLiveData()

    init {
        viewModelScope.launch {
            repository.getUserStream().collectLatest { currentUser ->
                if (currentUser != null) {
                    loadStatistics(currentUser)
                }
            }
        }
    }

    private fun loadStatistics(user: User) {
        viewModelScope.launch {
            try {
                val uids = mutableListOf(user.uid)
                if (!user.partnerUid.isNullOrEmpty()) {
                    uids.add(user.partnerUid)
                }

                val memCount = repository.getMemoriesCount(uids)
                val wishCount = repository.getWishesCount(uids)

                _memoriesCount.value = memCount
                _wishesCount.value = wishCount
            } catch (e: Exception) {
                e.stackTrace
            }
        }
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        _actionState.value = ActionState.NavigateToLogin
    }

    fun uploadAvatar(uri: Uri) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.updateAvatar(uri)
                _actionState.value = ActionState.Idle
                _toastMessage.value = "Фото обновлено"
            } catch (e: Exception) {
                _actionState.value = ActionState.Idle
                _toastMessage.value = "Ошибка: ${e.message}"
            }
        }
    }

    fun deleteAccount() {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.deleteAccount()
                _actionState.value = ActionState.NavigateToLogin
            } catch (e: Exception) {
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                    _actionState.value = ActionState.ReAuthNeeded(email)
                } else {
                    _actionState.value = ActionState.Idle
                    _toastMessage.value = e.message
                }
            }
        }
    }

    fun reauthenticateAndDelete(password: String) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.reauthenticate(password)
                repository.deleteAccount()
                _actionState.value = ActionState.NavigateToLogin
            } catch (e: Exception) {
                _actionState.value = ActionState.Idle
                _toastMessage.value = "Неверный пароль"
            }
        }
    }

    fun onToastShown() { _toastMessage.value = null }
}