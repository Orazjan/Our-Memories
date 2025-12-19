package com.example.ourmemories.Fragments

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Adapters.SelectedImagesAdapter
import com.example.ourmemories.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddMemoryFragment : Fragment(R.layout.add_memory_fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val TAG = "AddMemoryFragment"

    // Список выбранных URI
    private val selectedUris = mutableListOf<Uri>()
    private lateinit var imagesAdapter: SelectedImagesAdapter

    // URI выбранной обложки (по умолчанию будет первой)
    private var coverUri: Uri? = null

    private var selectedDateTimestamp: Long = System.currentTimeMillis()

    // Photo Picker (Мульти-выбор)
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
            if (uris.isNotEmpty()) {
                val isFirstLoad = selectedUris.isEmpty()
                selectedUris.addAll(uris)

                // Если обложка еще не выбрана, ставим первую из списка
                if (coverUri == null && selectedUris.isNotEmpty()) {
                    coverUri = selectedUris[0]
                }

                // Пытаемся достать дату из первого фото
                if (isFirstLoad) {
                    tryExtractDateFromImage(uris[0])
                }

                updateImagesList()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        val cardAddPhoto = view.findViewById<View>(R.id.cardAddPhoto)
        val rvSelectedImages = view.findViewById<RecyclerView>(R.id.rvSelectedImages)
        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)
        val btnSave = view.findViewById<Button>(R.id.btnSaveMemory)

        // Защита от случайного выхода
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedUris.isNotEmpty() || etTitle.text.isNotEmpty()) {
                    showExitConfirmationDialog()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        // Настройка адаптера
        imagesAdapter = SelectedImagesAdapter(images = selectedUris, onRemoveClick = { position ->
            if (position in selectedUris.indices) {
                val removedUri = selectedUris[position]

                // Обновляем логику обложки (визуально)
                imagesAdapter.adjustCoverPositionAfterRemoval(position)

                // Удаляем из данных
                selectedUris.removeAt(position)
                imagesAdapter.notifyItemRemoved(position)
                imagesAdapter.notifyItemRangeChanged(position, selectedUris.size)

                // Если удалили текущую обложку, назначаем новую
                if (removedUri == coverUri) {
                    coverUri = selectedUris.firstOrNull()
                }

                updateImagesList()
            }
        }, onImageClick = { position ->
            // Пользователь кликнул на фото -> делаем его обложкой
            if (position in selectedUris.indices) {
                coverUri = selectedUris[position]
            }
        })

        rvSelectedImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedImages.adapter = imagesAdapter

        updateDateLabel(etDate)

        // Обработчики
        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        cardAddPhoto.setOnClickListener {
            try {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось открыть галерею", Toast.LENGTH_SHORT).show()
            }
        }

        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDescription.text.toString().trim()

            if (selectedUris.isEmpty()) {
                Toast.makeText(context, "Выберите хотя бы одно фото", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (title.isEmpty()) {
                Toast.makeText(context, "Введите название события", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            uploadMemories(title, desc)
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext()).setTitle("Отменить создание?")
            .setMessage("Все несохраненные данные будут потеряны.")
            .setPositiveButton("Выйти") { _, _ ->
                parentFragmentManager.popBackStack()
            }.setNegativeButton("Остаться", null).show()
    }

    private fun updateImagesList() {
        val rv = view?.findViewById<RecyclerView>(R.id.rvSelectedImages)
        if (selectedUris.isNotEmpty()) {
            rv?.visibility = View.VISIBLE
            // Если адаптер был пуст или view пересоздана
            if (rv?.adapter == null) rv?.adapter = imagesAdapter
        } else {
            rv?.visibility = View.GONE
        }
    }

    private fun uploadMemories(title: String, desc: String) {
        val user = auth.currentUser ?: return
        val loadingOverlay = view?.findViewById<View>(R.id.loadingOverlay)
        loadingOverlay?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // 1. Загрузка всех фото параллельно
                val uploadJobs = selectedUris.map { uri ->
                    async {
                        val url = uploadSingleImage(uri, user.uid)
                        Pair(uri, url)
                    }
                }

                val uploadedPairs = uploadJobs.awaitAll()

                // Получаем просто список ссылок
                val allUrls = uploadedPairs.map { it.second }

                // Находим URL для обложки (сопоставляем по URI)
                val coverPair = uploadedPairs.find { it.first == coverUri }
                val finalCoverUrl = coverPair?.second ?: allUrls.firstOrNull() ?: ""

                // 2. Сохранение АЛЬБОМА (одна запись) в БД
                if (allUrls.isNotEmpty()) {
                    val memoryMap = hashMapOf(
                        "uploaderUid" to user.uid,
                        "title" to title,
                        "description" to desc,
                        "timestamp" to selectedDateTimestamp,
                        "createdAt" to System.currentTimeMillis(),
                        "images" to allUrls,
                        "imageUrl" to finalCoverUrl
                    )

                    db.collection("memories").add(memoryMap).await()

                    // === НАЧИСЛЕНИЕ ОЧКОВ ЗА ФОТО ===
                    // 5 очков за каждую фотографию
                    val pointsToAdd = selectedUris.size * 5L
                    db.collection("users").document(user.uid)
                        .update("treePoints", FieldValue.increment(pointsToAdd))
                }

                withContext(Dispatchers.Main) {
                    loadingOverlay?.visibility = View.GONE
                    Toast.makeText(
                        context,
                        "Альбом сохранен! +${selectedUris.size * 5} очков",
                        Toast.LENGTH_SHORT
                    ).show()
                    parentFragmentManager.popBackStack()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingOverlay?.visibility = View.GONE
                    val msg =
                        if (e.message?.contains("Unable to resolve host") == true) "Нет интернета" else e.localizedMessage
                    Toast.makeText(context, "Ошибка: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadSingleImage(uri: Uri, uid: String): String {
        return withContext(Dispatchers.IO) {
            val compressedData = compressImage(uri)
            val fileName = UUID.randomUUID().toString()
            val ref = storage.reference.child("memories/$uid/$fileName.jpg")
            ref.putBytes(compressedData).await()
            ref.downloadUrl.await().toString()
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        val context = requireContext()
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val scaledBitmap = scaleBitmap(bitmap, 1280)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return outputStream.toByteArray()
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                newWidth = maxDimension
                newHeight = (newWidth * originalHeight) / originalWidth
            } else {
                newHeight = maxDimension
                newWidth = (newHeight * originalWidth) / originalHeight
            }
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // === EXIF Чтение даты ===
    private fun tryExtractDateFromImage(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val dateString = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)

                if (dateString != null) {
                    val format =
                        if (dateString.contains("-")) "yyyy-MM-dd HH:mm:ss" else "yyyy:MM:dd HH:mm:ss"
                    val sdf = SimpleDateFormat(format, Locale.US)
                    val date = sdf.parse(dateString)
                    if (date != null) {
                        selectedDateTimestamp = date.time
                        view?.findViewById<EditText>(R.id.etDate)?.let { updateDateLabel(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения даты: ${e.message}")
        }
    }

    // === КАСТОМНЫЙ DATE PICKER ===
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
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.minValue = 1980
        npYear.maxValue = currentYear
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

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        updateDaysInMonth()

        btnConfirm.setOnClickListener {
            val selectedCal = Calendar.getInstance()
            selectedCal.set(Calendar.YEAR, npYear.value)
            selectedCal.set(Calendar.MONTH, npMonth.value)
            selectedCal.set(Calendar.DAY_OF_MONTH, npDay.value)

            selectedDateTimestamp = selectedCal.timeInMillis
            updateDateLabel(editText)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateDateLabel(editText: EditText) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        editText.setText(sdf.format(Date(selectedDateTimestamp)))
    }
}