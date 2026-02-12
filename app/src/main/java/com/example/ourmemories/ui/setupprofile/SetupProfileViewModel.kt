package com.example.ourmemories.ui.setupprofile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.data.repositories.SetupProfileRepository
import com.example.ourmemories.utils.ImageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class SetupProfileViewModel(
    application: Application,
    private val repository: SetupProfileRepository,
    private val imageHandler: ImageHandler
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _setupSuccess = MutableLiveData<Boolean>()
    val setupSuccess: LiveData<Boolean> = _setupSuccess

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    fun setSelectedImage(uri: Uri) {
        _selectedImageUri.value = uri
    }

    /**
     * Сохранение профиля пользователя.
     */
    fun saveProfile(name: String, date: String) {
        val user = repository.getCurrentUser()
        if (user == null) {
            _toastMessage.value = "Ошибка авторизации"
            return
        }

        val uri = _selectedImageUri.value
        if (uri == null) {
            _toastMessage.value = "Выберите фото профиля"
            return
        }
        if (name.isEmpty()) {
            _toastMessage.value = "Введите имя"
            return
        }
        if (date.isEmpty()) {
            _toastMessage.value = "Выберите дату рождения"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val compressedData = withContext(Dispatchers.IO) {
                    imageHandler.compressImage(uri)
                }
                val photoUrl = repository.uploadAvatar(user.uid, compressedData)

                repository.updateAuthProfile(user, name, photoUrl)

                var partnerCode = ""
                var isUnique = false
                var attempts = 0

                while (!isUnique && attempts < 5) {
                    partnerCode = generatePartnerCode()
                    isUnique = repository.isCodeUnique(partnerCode)
                    attempts++
                }

                if (!isUnique) {
                    throw Exception("Не удалось создать уникальный код. Попробуйте снова.")
                }

                val userData = hashMapOf(
                    "name" to name,
                    "birthDate" to date,
                    "uid" to user.uid,
                    "email" to user.email,
                    "photoUrl" to photoUrl,
                    "partnerCode" to partnerCode,
                    "partnerUid" to null,
                    "status" to null,
                    "sharedNote" to "",
                    "treePoints" to 0
                )

                val codeData = hashMapOf(
                    "uid" to user.uid
                )

                repository.saveUserProfile(user.uid, userData, partnerCode, codeData)

                _setupSuccess.value = true
                _toastMessage.value = "Профиль готов!"

            } catch (e: Exception) {
                _toastMessage.value = "Ошибка: ${e.localizedMessage}"
                Log.e("SetupProfileViewModel", "Error saving profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Сжатие изобрпжения
     */


    private fun generatePartnerCode(): String {
        return Random.Default.nextInt(10000000, 99999999).toString()
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}