package com.example.ourmemories.LogAndReg

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class SetupProfileFragment : Fragment(R.layout.setup_profile_fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var selectedImageUri: Uri? = null

    // Храним задачу загрузки, чтобы дождаться её завершения при сохранении
    private var uploadTask: Deferred<String?>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val avatarView = view?.findViewById<ImageView>(R.id.ivAvatar)
            avatarView?.setImageURI(uri)
            avatarView?.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarView?.setPadding(0, 0, 0, 0)

            // === ОПТИМИЗАЦИЯ: Начинаем загрузку сразу после выбора! ===
            // Пока пользователь вводит имя, фото уже летит на сервер
            uploadTask = lifecycleScope.async {
                uploadImageToStorage(uri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<EditText>(R.id.etName)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        cardAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etDate.text.toString().trim()

            // Проверка на заполнение (фото обязательно)
            if (name.isEmpty() || date.isEmpty() || selectedImageUri == null) {
                Toast.makeText(
                    context, "Пожалуйста, выберите фото и заполните все поля", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                btnSave.isEnabled = false
                btnSave.text = "Сохранение..." // Текст изменился, так как загрузка могла уже пройти

                try {
                    saveProfile(name, date)
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Готово"
                }
            }
        }
    }

    // Вспомогательная функция для загрузки в фоне
    private suspend fun uploadImageToStorage(uri: Uri): String? {
        val user = auth.currentUser ?: return null
        return try {
            val compressedData = compressImage(uri)
            // Увеличиваем таймаут для плохой сети (через конфигурацию Storage, если нужно)
            // но стандартные настройки обычно справляются, если файл маленький.
            val storageRef = storage.reference.child("avatars/${user.uid}.jpg")

            storageRef.putBytes(compressedData).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace() // Логируем ошибку, но не крашим приложение здесь
            null
        }
    }

    private fun showWheelDatePicker(editText: EditText) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        npYear.minValue = 1900
        npYear.maxValue = currentYear
        npYear.value = 2000
        npYear.wrapSelectorWheel = false

        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value)
            cal.set(Calendar.MONTH, npMonth.value)
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            npDay.maxValue = maxDay
        }

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }

        updateDaysInMonth()

        btnConfirm.setOnClickListener {
            val selectedDate = String.format(
                "%02d.%02d.%d", npDay.value, npMonth.value + 1, npYear.value
            )
            editText.setText(selectedDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generatePartnerCode(): String {
        return Random.nextInt(10000000, 99999999).toString()
    }

    private suspend fun saveProfile(name: String, date: String) {
        val user = auth.currentUser ?: throw Exception("Пользователь не найден")
        val uid = user.uid

        // Ждем завершения фоновой загрузки.
        // Если загрузка уже завершилась - результат вернется мгновенно.
        // Если была ошибка в фоне - uploadTask вернет null, и мы попробуем загрузить еще раз или сохраним без фото.
        var photoUrl = uploadTask?.await()

        // Если фоновая загрузка не удалась (например, начали грузить, а интернет пропал),
        // можно попробовать еще раз сейчас (синхронно), если очень нужно,
        // или просто продолжить без фото.
        if (photoUrl == null && selectedImageUri != null) {
            // Попытка 2 (на случай сбоя первой)
            photoUrl = uploadImageToStorage(selectedImageUri!!)
        }

        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name)
        if (photoUrl != null) {
            profileUpdates.photoUri = Uri.parse(photoUrl)
        }
        user.updateProfile(profileUpdates.build()).await()

        val partnerCode = generatePartnerCode()

        val userData = hashMapOf(
            "name" to name,
            "birthDate" to date,
            "uid" to uid,
            "email" to user.email,
            "photoUrl" to photoUrl,
            "partnerCode" to partnerCode,
            "partnerUid" to null
        )
        db.collection("users").document(uid).set(userData).await()

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Профиль готов!", Toast.LENGTH_SHORT).show()
            (requireActivity() as? EnterActivity)?.onAuthSuccess()
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                requireContext().contentResolver,
                uri
            )
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }
}