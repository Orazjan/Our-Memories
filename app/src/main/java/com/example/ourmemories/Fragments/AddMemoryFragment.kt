package com.example.ourmemories.Fragments

import android.os.Bundle
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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Adapters.SelectedImagesAdapter
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.AddMemoryViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddMemoryFragment : Fragment(R.layout.add_memory_fragment) {

    private lateinit var viewModel: AddMemoryViewModel
    private lateinit var imagesAdapter: SelectedImagesAdapter

    // Photo Picker
    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[AddMemoryViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI(view: View) {
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
                val hasImages = (viewModel.selectedUris.value?.size ?: 0) > 0
                val hasTitle = etTitle.text.isNotEmpty()
                
                if (hasImages || hasTitle) {
                    showExitConfirmationDialog()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        // Настройка адаптера
        // Мы передаем пустой список при инициализации, он обновится через observe
        imagesAdapter = SelectedImagesAdapter(images = mutableListOf(), onRemoveClick = { position ->
            viewModel.removeImage(position)
        }, onImageClick = { position ->
            viewModel.setCover(position)
            Toast.makeText(context, "Обложка выбрана", Toast.LENGTH_SHORT).show()
        })

        rvSelectedImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedImages.adapter = imagesAdapter

        // Кнопки
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
            val currentTimestamp = viewModel.eventDate.value ?: System.currentTimeMillis()
            showWheelDatePicker(currentTimestamp)
        }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDescription.text.toString().trim()
            viewModel.saveMemory(title, desc)
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        val rvSelectedImages = view.findViewById<RecyclerView>(R.id.rvSelectedImages)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay)

        // Список фото
        viewModel.selectedUris.observe(viewLifecycleOwner) { uris ->
            // Обновляем список в адаптере
            // В идеале адаптер должен использовать DiffUtil или submitList, но для простоты обновим коллекцию напрямую
            imagesAdapter.updateList(uris) 
            rvSelectedImages.visibility = if (uris.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Дата события
        viewModel.eventDate.observe(viewLifecycleOwner) { timestamp ->
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            etDate.setText(sdf.format(Date(timestamp)))
        }

        // Состояние загрузки
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Сообщения
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        // Успешное сохранение -> закрываем фрагмент
        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                parentFragmentManager.popBackStack()
            }
        }
    }

    /**
     * Отображение диалога подтверждения выхода.
     */
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext()).setTitle("Отменить создание?")
            .setMessage("Все несохраненные данные будут потеряны.")
            .setPositiveButton("Выйти") { _, _ ->
                parentFragmentManager.popBackStack()
            }.setNegativeButton("Остаться", null).show()
    }

    /**
     * Отображение диалога выбора даты.
     */
    private fun showWheelDatePicker(initialTimestamp: Long) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = initialTimestamp
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

            viewModel.setEventDate(selectedCal.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }
}
