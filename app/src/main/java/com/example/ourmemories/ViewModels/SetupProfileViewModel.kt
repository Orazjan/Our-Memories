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
import kotlin.random.Random

class SetupProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
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
        val user = auth.currentUser
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
                // Загрузка изображения
                val compressedData = compressImage(uri)
                val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                storageRef.putBytes(compressedData).await()
                val photoUrl = storageRef.downloadUrl.await().toString()

                // Проверка аутентификации
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .setPhotoUri(Uri.parse(photoUrl))
                    .build()
                user.updateProfile(profileUpdates).await()

                // Сохранение в Firestore
                val partnerCode = generatePartnerCode()
                val userData = hashMapOf(
                    "name" to name,
                    "birthDate" to date,
                    "uid" to user.uid,
                    "email" to user.email,
                    "photoUrl" to photoUrl,
                    "partnerCode" to partnerCode,
                    "partnerUid" to null
                )
                db.collection("users").document(user.uid).set(userData).await()

                _setupSuccess.value = true
                _toastMessage.value = "Профиль готов!"

            } catch (e: Exception) {
                _toastMessage.value = "Ошибка: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Сжатие изобрпжения
     */
    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val maxDimension = 800
        val scale = Math.min(
            maxDimension.toDouble() / bitmap.width,
            maxDimension.toDouble() / bitmap.height
        )

        val scaledBitmap = if (scale < 1.0) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }

    /**
     * TODO Нужно сделать проверку чтобы код одного пользователя не совподал с кодом другого
     */
    private fun generatePartnerCode(): String {
        return Random.nextInt(10000000, 99999999).toString()
    }

    fun onToastShown() {
        _toastMessage.value = null
    }
}
