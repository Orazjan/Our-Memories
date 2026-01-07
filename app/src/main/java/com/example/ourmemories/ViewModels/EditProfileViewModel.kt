package com.example.ourmemories.ViewModels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * ViewModel для редактирования профиля [EditProfileFragment].
 *
 * Функции:
 * - Загрузка текущих данных профиля.
 * - Сжатие и загрузка нового аватара.
 * - Обновление имени и даты рождения в Firestore и Auth.
 */
class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val context = application.applicationContext

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

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        loadCurrentData()
    }

    /**
     * Загрузка текущих данных профиля.
     */
    private fun loadCurrentData() {
        val user = auth.currentUser ?: return

        _currentName.value = user.displayName ?: ""
        _currentPhotoUrl.value = user.photoUrl?.toString()

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val birthDate = doc.getString("birthDate") ?: ""
                    _currentBirthDate.value = birthDate
                    
                    val nameInDb = doc.getString("name")
                    if (_currentName.value.isNullOrEmpty() && !nameInDb.isNullOrEmpty()) {
                        _currentName.value = nameInDb!!
                    }
                }
            }
            .addOnFailureListener {
                _toastMessage.value = "Ошибка загрузки данных: ${it.message}"
            }
    }

    /**
     * Установка выбранного изображения.
     */
    fun selectImage(uri: Uri) {
        _selectedImageUri.value = uri
    }

    /**
     * Основной метод сохранения.
     */
    fun saveChanges(name: String, date: String) {
        val user = auth.currentUser ?: return
        
        if (name.isEmpty()) {
            _toastMessage.value = "Имя не может быть пустым"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                var photoUrl = user.photoUrl?.toString()
                val newImageUri = _selectedImageUri.value

                if (newImageUri != null) {
                    val compressedData = compressImage(newImageUri)
                    val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                    storageRef.putBytes(compressedData).await()
                    photoUrl = storageRef.downloadUrl.await().toString()
                }

                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                if (photoUrl != null) {
                    updates.setPhotoUri(Uri.parse(photoUrl))
                }
                user.updateProfile(updates.build()).await()

                val updateMap = hashMapOf<String, Any>(
                    "name" to name,
                    "birthDate" to date
                )
                if (photoUrl != null) updateMap["photoUrl"] = photoUrl

                db.collection("users").document(user.uid).update(updateMap).await()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _toastMessage.value = "Профиль обновлен!"
                    _saveSuccess.value = true
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _toastMessage.value = "Ошибка: ${e.message}"
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
