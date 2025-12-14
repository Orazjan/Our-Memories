package com.example.ourmemories.Fragments

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// Ссылаемся на созданный XML (fab_fragment)
class AddMemoryFragment : Fragment(R.layout.fab_fragment) {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = FirebaseAuth.getInstance()

    private var selectedImageUri: Uri? = null
    private var uploadTask: Deferred<String?>? = null
    private var selectedDateTimestamp: Long = System.currentTimeMillis()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val ivImage = view?.findViewById<ImageView>(R.id.ivSelectedImage)
            val placeholder = view?.findViewById<LinearLayout>(R.id.layoutPlaceholder)

            ivImage?.setImageURI(uri)
            ivImage?.scaleType = ImageView.ScaleType.CENTER_CROP
            placeholder?.visibility = View.GONE

            // Определяем дату из фото
            tryExtractDateFromPhoto(uri)

            // Начинаем загрузку фоном сразу после выбора
            uploadTask = lifecycleScope.async {
                uploadImageToStorage(uri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        val cardImage = view.findViewById<View>(R.id.cardImageUpload)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etDesc = view.findViewById<EditText>(R.id.etDescription)
        val btnSave = view.findViewById<Button>(R.id.btnSaveMemory)

        // Ставим текущую дату
        updateDateText(etDate, selectedDateTimestamp)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        cardImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDesc.text.toString().trim()

            if (title.isEmpty()) {
                etTitle.error = "Введите название"
                return@setOnClickListener
            }
            if (selectedImageUri == null) {
                Toast.makeText(context, "Выберите фото", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                btnSave.isEnabled = false
                btnSave.text = "Сохранение..."

                // Блокируем экран, чтобы пользователь не нажал назад
                val loadingOverlay =
                    view.findViewById<View>(R.id.layoutPlaceholder)

                try {
                    saveMemory(title, description)
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSave.isEnabled = true
                    btnSave.text = "Сохранить воспоминание"
                }
            }
        }
    }

    private fun tryExtractDateFromPhoto(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val exifInterface = ExifInterface(inputStream)
                    val dateTimeString =
                        exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                    if (dateTimeString != null) {
                        val exifFormat =
                            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                        val date = exifFormat.parse(dateTimeString)
                        if (date != null) {
                            selectedDateTimestamp = date.time
                            val etDate = view?.findViewById<EditText>(R.id.etDate)
                            if (etDate != null) {
                                updateDateText(etDate, selectedDateTimestamp)
                                Toast.makeText(context, "Дата взята из фото", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun saveMemory(title: String, description: String) {
        val user = auth.currentUser ?: throw Exception("Вы не авторизованы")

        // Ждем завершения загрузки фото (если оно еще грузится)
        val photoUrl = uploadTask?.await() ?: throw Exception("Не удалось загрузить фото")
        val memoryId = UUID.randomUUID().toString()

        // Объединяем Заголовок и Описание, так как в модели нет Title
        val combinedDescription = "$title\n\n$description"

        val memoryMap = hashMapOf(
            "id" to memoryId,
            "description" to combinedDescription,
            "imageUrl" to photoUrl,
            "timestamp" to selectedDateTimestamp,
            "createdAt" to System.currentTimeMillis(),
            "uploaderUid" to user.uid
        )

        db.collection("memories").document(memoryId).set(memoryMap).await()

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Воспоминание сохранено!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private suspend fun uploadImageToStorage(uri: Uri): String? {
        val user = auth.currentUser ?: return null
        return try {
            val compressedData = compressImage(uri)
            val fileName = UUID.randomUUID().toString()
            val storageRef = storage.reference.child("memories/${user.uid}/$fileName.jpg")

            storageRef.putBytes(compressedData).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
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
        calendar.timeInMillis = selectedDateTimestamp

        npYear.minValue = 1990
        npYear.maxValue = Calendar.getInstance().get(Calendar.YEAR)
        npYear.value = calendar.get(Calendar.YEAR)
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
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        updateDaysInMonth()

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }

        btnConfirm.setOnClickListener {
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(Calendar.YEAR, npYear.value)
            selectedCalendar.set(Calendar.MONTH, npMonth.value)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, npDay.value)

            selectedDateTimestamp = selectedCalendar.timeInMillis
            updateDateText(editText, selectedDateTimestamp)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateDateText(editText: EditText, timestamp: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)
        editText.setText(String.format("%02d.%02d.%d", day, month, year))
    }
}