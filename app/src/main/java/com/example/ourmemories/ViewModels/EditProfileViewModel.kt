package com.example.ourmemories.ViewModels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.EditProfileRepository
import com.example.ourmemories.Utils.ImageHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel для редактирования профиля [com.example.ourmemories.Fragments.EditProfileFragment].
 *
 * Функции:
 * - Загрузка текущих данных профиля.
 * - Сжатие и загрузка нового аватара.
 * - Обновление имени и даты рождения в Firestore и Auth.
 */
class EditProfileViewModel(
    application: Application,
    private val repository: EditProfileRepository,
    private val imageHandler: ImageHandler
) : AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    private val _currentName = MutableLiveData<String>()
    val currentName: LiveData<String> = _currentName

    private val _currentBirthDate = MutableLiveData<String>()
    val currentBirthDate: LiveData<String> = _currentBirthDate

    private val _currentPhotoUrl = MutableLiveData<String?>()
    val currentPhotoUrl: LiveData<String?> = _currentPhotoUrl

    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        loadCurrentData()
    }

    /**
     * Загрузка текущих данных профиля.
     */
    private fun loadCurrentData() {
        _isLoading.value = true
        repository.loadUser(
            currentName = _currentName,
            currentPhotoUrl = _currentPhotoUrl,
            currentBirthDate = _currentBirthDate,
            onSuccess = { _isLoading.value = false },
            onFailure = { errorMsg ->
                _isLoading.value = false
                _toastMessage.value = errorMsg
            })
    }

    /**
     * Установка выбранного изображения (из галереи).
     */
    fun selectImage(uri: Uri) {
        _selectedImageUri.value = uri
    }

    /**
     * Основной метод сохранения изменений.
     */
    fun saveChanges(name: String, date: String) {
        val uid = repository.getCurrentUserUid()

        if (uid == null) {
            _toastMessage.value = "Ошибка"
            return
        }

        if (name.isEmpty()) {
            _toastMessage.value = getStringSafe(R.string.error_empty_title)
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                var photoUrl = _currentPhotoUrl.value
                val newImageUri = _selectedImageUri.value

                if (newImageUri != null) {
                    val compressedData = withContext(Dispatchers.IO) {
                        imageHandler.compressImage(newImageUri)
                    }
                    photoUrl = repository.uploadAvatar(uid, compressedData)
                }
                repository.updateAuthProfile(name, photoUrl)

                val updateMap = hashMapOf<String, Any>(
                    "name" to name,
                    "birthDate" to date
                )
                if (photoUrl != null) {
                    updateMap["photoUrl"] = photoUrl
                }

                repository.saveUserToFirestore(uid, updateMap)

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _toastMessage.value = getStringSafe(R.string.saved)
                    _saveSuccess.value = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    val error = e.localizedMessage ?: "Unknown error"
                    _toastMessage.value = getStringSafe(R.string.error_generic, error)
                }
            }
        }
    }

    private fun getStringSafe(resId: Int, vararg formatArgs: Any): String {
        return try {
            if (formatArgs.isNotEmpty()) context.getString(
                resId, *formatArgs
            ) else context.getString(resId)
        } catch (e: Exception) {
            Log.e("EditProfileViewModel", "getStringSafe: $e")
            ""
        }
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}