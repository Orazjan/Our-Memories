package com.example.ourmemories.Fragments

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
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
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

class EditProfileFragment : Fragment(R.layout.edit_profile_fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private var selectedImageUri: Uri? = null

    // UI Elements
    private lateinit var ivAvatar: ImageView
    private lateinit var etName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var loadingOverlay: View

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivAvatar.setImageURI(uri) // Показываем локально сразу
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivAvatar = view.findViewById(R.id.ivAvatar)
        etName = view.findViewById(R.id.etName)
        etBirthDate = view.findViewById(R.id.etBirthDate)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        // 1. Загружаем текущие данные
        loadCurrentData()

        // 2. Обработчики
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        cardAvatar.setOnClickListener { pickImage.launch("image/*") }

        etBirthDate.setOnClickListener { showWheelDatePicker(etBirthDate) }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etBirthDate.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(context, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveChanges(name, date)
        }
    }

    private fun loadCurrentData() {
        val user = auth.currentUser ?: return

        // Фото и имя из Auth (быстро)
        etName.setText(user.displayName)
        GlideHelper.loadAvatar(ivAvatar, user.photoUrl?.toString(), "EditProfile")

        // Дата из Firestore (чуть дольше)
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val birthDate = doc.getString("birthDate")
                    // Если имени нет в Auth, берем из базы
                    if (etName.text.isEmpty()) etName.setText(doc.getString("name"))
                    etBirthDate.setText(birthDate)
                }
            }
    }

    private fun saveChanges(name: String, date: String) {
        val user = auth.currentUser ?: return
        loadingOverlay.visibility = View.VISIBLE // Блокируем экран

        lifecycleScope.launch {
            try {
                var photoUrl = user.photoUrl?.toString()

                // Если выбрали новое фото -> Грузим
                if (selectedImageUri != null) {
                    val compressedData = compressImage(selectedImageUri!!)
                    val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                    storageRef.putBytes(compressedData).await()
                    photoUrl = storageRef.downloadUrl.await().toString()
                }

                // Обновляем Auth (Имя + Фото)
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                if (photoUrl != null) {
                    updates.setPhotoUri(Uri.parse(photoUrl))
                }
                user.updateProfile(updates.build()).await()

                // Обновляем Firestore
                val updateMap = hashMapOf<String, Any>(
                    "name" to name,
                    "birthDate" to date
                )
                if (photoUrl != null) updateMap["photoUrl"] = photoUrl

                db.collection("users").document(user.uid).update(updateMap).await()

                // Успех
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(context, "Профиль обновлен!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack() // Возвращаемся назад
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        }
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }

    private fun showWheelDatePicker(editText: EditText) {
        val dialog = BottomSheetDialog(
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
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
        npYear.value = 2000 // Дефолт
        npYear.wrapSelectorWheel = false

        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        // Обновляем дни в зависимости от месяца/года (упрощенно)
        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value)
            cal.set(Calendar.MONTH, npMonth.value)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }

        btnConfirm.setOnClickListener {
            val selectedDate =
                String.format("%02d.%02d.%d", npDay.value, npMonth.value + 1, npYear.value)
            editText.setText(selectedDate)
            dialog.dismiss()
        }
        dialog.show()
    }
}