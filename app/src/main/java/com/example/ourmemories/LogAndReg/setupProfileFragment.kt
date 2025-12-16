package com.example.ourmemories.LogAndReg

import android.animation.ObjectAnimator
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
import com.bumptech.glide.Glide
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

    // Храним процесс загрузки
    private var uploadTask: Deferred<String?>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val avatarView = view?.findViewById<ImageView>(R.id.ivAvatar)

            // Используем Glide для надежного отображения
            if (avatarView != null) {
                avatarView.setPadding(0, 0, 0, 0)
                Glide.with(this).load(uri).centerCrop().into(avatarView)
            }

            // СРАЗУ НАЧИНАЕМ СЖАТИЕ И ЗАГРУЗКУ
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

        // Анимация появления аватарки
        cardAvatar.alpha = 0f
        cardAvatar.translationY = 50f
        cardAvatar.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(200).start()

        cardAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etDate.text.toString().trim()

            // Анимация "тряски" при ошибке
            var hasError = false

            if (selectedImageUri == null) {
                shakeView(cardAvatar)
                Toast.makeText(context, "Выберите фото профиля", Toast.LENGTH_SHORT).show()
                hasError = true
            }

            if (name.isEmpty()) {
                shakeView(etName)
                etName.error = "Введите имя"
                hasError = true
            }

            if (date.isEmpty()) {
                shakeView(etDate)
                Toast.makeText(context, "Выберите дату рождения", Toast.LENGTH_SHORT).show()
                hasError = true
            }

            if (hasError) return@setOnClickListener

            lifecycleScope.launch {
                btnSave.isEnabled = false
                btnSave.text = "Сохранение..."

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

    // Функция тряски View (визуальный отклик на ошибку)
    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(
            view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f
        ).apply {
            duration = 500
            start()
        }
    }

    private suspend fun uploadImageToStorage(uri: Uri): String? {
        val user = auth.currentUser ?: return null
        return try {
            val compressedData = compressAndResizeImage(uri)
            val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
            storageRef.putBytes(compressedData).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun saveProfile(name: String, date: String) {
        val user = auth.currentUser ?: throw Exception("Пользователь не найден")
        val uid = user.uid

        // Ждем результат загрузки
        var photoUrl = uploadTask?.await()

        // Если фоновая загрузка сорвалась, пробуем еще раз
        if (photoUrl == null && selectedImageUri != null) {
            photoUrl = uploadImageToStorage(selectedImageUri!!)
        }

        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name)
        if (photoUrl != null) {
            profileUpdates.setPhotoUri(Uri.parse(photoUrl))
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

    private suspend fun compressAndResizeImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val context = requireContext()
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                context.contentResolver, uri
            )
        }

        val maxDimension = 800
        val scale = Math.min(
            maxDimension.toDouble() / originalBitmap.width,
            maxDimension.toDouble() / originalBitmap.height
        )

        val scaledBitmap = if (scale < 1.0) {
            Bitmap.createScaledBitmap(
                originalBitmap,
                (originalBitmap.width * scale).toInt(),
                (originalBitmap.height * scale).toInt(),
                true
            )
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
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
            val selectedDate =
                String.format("%02d.%02d.%d", npDay.value, npMonth.value + 1, npYear.value)
            editText.setText(selectedDate)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun generatePartnerCode(): String {
        return Random.nextInt(10000000, 99999999).toString()
    }
}