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
import android.widget.NumberPicker // Для "барабанов"
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.android.material.bottomsheet.BottomSheetDialog // Для всплывающего окна
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class SetupProfileFragment : Fragment(R.layout.setup_profile_fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var selectedImageUri: Uri? = null

    // Лаунчер для выбора картинки из галереи
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val avatarView = view?.findViewById<ImageView>(R.id.ivAvatar)
            avatarView?.setImageURI(uri)
            avatarView?.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarView?.setPadding(0, 0, 0, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<EditText>(R.id.etName)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        // Выбор фото
        cardAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        // Выбор даты (Новый метод с барабанами)
        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        // Сохранение
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etDate.text.toString().trim()

            if (name.isEmpty() || date.isEmpty()) {
                Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Запускаем процесс в фоне (корутина)
            lifecycleScope.launch {
                btnSave.isEnabled = false
                btnSave.text = "Загрузка..."

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

    // ФУНКЦИЯ ПОКАЗА "БАРАБАНОВ" (Wheel Picker) ===
    private fun showWheelDatePicker(editText: EditText) {
        val dialog = BottomSheetDialog(
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        // Инициализируем элементы диалога
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        // Настройка ГОДА (от 1900 до текущего)
        npYear.minValue = 1900
        npYear.maxValue = currentYear
        npYear.value = 2000 // Год по умолчанию
        npYear.wrapSelectorWheel = false

        // Настройка МЕСЯЦА (Имена месяцев: Янв, Фев...)
        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        // 3. Настройка ДНЯ
        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        // Обновляет кол-во дней (28/30/31) при смене месяца/года
        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value)
            cal.set(Calendar.MONTH, npMonth.value)
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            npDay.maxValue = maxDay
        }

        // Слушатели изменений
        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }

        updateDaysInMonth()

        // Кнопка "Выбрать"
        btnConfirm.setOnClickListener {
            // Форматируем дату в строку: 05.02.2000
            val selectedDate = String.format(
                "%02d.%02d.%d",
                npDay.value,
                npMonth.value + 1, // Месяцы начинаются с 0
                npYear.value
            )
            editText.setText(selectedDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    // === ГЛАВНАЯ ЛОГИКА СОХРАНЕНИЯ ===
    private suspend fun saveProfile(name: String, date: String) {
        val user = auth.currentUser ?: throw Exception("Пользователь не найден")
        val uid = user.uid
        var photoUrl: String? = null

        // Сжимаем и загружаем фото (если выбрано)
        if (selectedImageUri != null) {
            val compressedData = compressImage(selectedImageUri!!)
            val storageRef = storage.reference.child("avatars/$uid.jpg")

            // Ждем завершения загрузки
            storageRef.putBytes(compressedData).await()
            // Ждем получения ссылки
            photoUrl = storageRef.downloadUrl.await().toString()
        }

        // Обновляем Auth (Имя + Фото)
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
        if (photoUrl != null) {
            profileUpdates.setPhotoUri(Uri.parse(photoUrl))
        }
        user.updateProfile(profileUpdates.build()).await()

        // Сохраняем данные в Firestore
        val userData = hashMapOf(
            "name" to name,
            "birthDate" to date,
            "uid" to uid,
            "email" to user.email,
            "photoUrl" to photoUrl
        )
        db.collection("users").document(uid).set(userData).await()

        // Успех -> Переходим в приложение
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Профиль готов!", Toast.LENGTH_SHORT).show()
            (requireActivity() as? EnterActivity)?.onAuthSuccess()
        }
    }

    // === ФУНКЦИЯ СЖАТИЯ ФОТО ===
    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        }

        val outputStream = ByteArrayOutputStream()
        // Сжимаем в JPEG с качеством 60%
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }
}