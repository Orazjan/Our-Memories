package com.example.ourmemories.LogAndReg

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.SetupProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class SetupProfileFragment : Fragment(R.layout.setup_profile_fragment) {

    private lateinit var viewModel: SetupProfileViewModel

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.setSelectedImage(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[SetupProfileViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Инициализация пользовательского интерфейса.
     */
    private fun setupUI(view: View) {
        val etName = view.findViewById<EditText>(R.id.etName)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        // Анимация появления
        cardAvatar.scaleX = 0f
        cardAvatar.scaleY = 0f
        cardAvatar.animate().scaleX(1f).scaleY(1f).setDuration(500)
            .setInterpolator(OvershootInterpolator()).start()
        cardAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }

        etDate.setOnClickListener {
            showWheelDatePicker(etDate)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etDate.text.toString().trim()
            
            // Валидация в UI для визуальных эффектов (тряска)
            var hasError = false
            if (viewModel.selectedImageUri.value == null) {
                shakeView(cardAvatar)
                hasError = true
            }
            if (name.isEmpty()) {
                shakeView(etName)
                etName.error = "Введите имя"
                hasError = true
            }
            if (date.isEmpty()) {
                shakeView(etDate)
                hasError = true
            }

            if (!hasError) {
                viewModel.saveProfile(name, date)
            }
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)
        val avatarView = view.findViewById<ImageView>(R.id.ivAvatar)
        val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay)

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                loadingOverlay.visibility = View.VISIBLE
                btnSave.isEnabled = false
                btnSave.text = "Сохранение..."
            } else {
                loadingOverlay.visibility = View.GONE
                btnSave.isEnabled = true
                btnSave.text = "Готово"
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        viewModel.setupSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                (requireActivity() as? EnterActivity)?.onAuthSuccess()
            }
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                avatarView.setPadding(0, 0, 0, 0)
                Glide.with(this).load(uri).centerCrop().into(avatarView)
            }
        }
    }

    /**
     * Тряска для поля ввода.
     */
    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(
            view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f
        ).apply {
            duration = 500
            start()
        }
    }

    /**
     * Открытие диалога выбора даты.
     */
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
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
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
}
