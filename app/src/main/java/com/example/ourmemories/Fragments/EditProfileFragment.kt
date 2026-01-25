package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.EditProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class EditProfileFragment : Fragment(R.layout.edit_profile_fragment) {

    private lateinit var viewModel: EditProfileViewModel

    private lateinit var ivAvatar: ImageView
    private lateinit var etName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var loadingOverlay: View

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[EditProfileViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI(view: View) {
        ivAvatar = view.findViewById(R.id.ivAvatar)
        etName = view.findViewById(R.id.etName)
        etBirthDate = view.findViewById(R.id.etBirthDate)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        cardAvatar.setOnClickListener { pickImage.launch("image/*") }

        etBirthDate.setOnClickListener { showWheelDatePicker(etBirthDate) }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etBirthDate.text.toString().trim()
            viewModel.saveChanges(name, date)
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        viewModel.currentName.observe(viewLifecycleOwner) { name ->
            if (etName.text.isEmpty() && name.isNotEmpty()) {
                etName.setText(name)
            }
        }
        
        viewModel.currentBirthDate.observe(viewLifecycleOwner) { date ->
            if (etBirthDate.text.isEmpty() && date.isNotEmpty()) {
                etBirthDate.setText(date)
            }
        }

        viewModel.currentPhotoUrl.observe(viewLifecycleOwner) { url ->
            if (viewModel.selectedImageUri.value == null) {
                GlideHelper.loadAvatar(ivAvatar, url, "EditProfile")
            }
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                ivAvatar.setImageURI(uri)
            }
        }


        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }


        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                parentFragmentManager.popBackStack()
            }
        }


        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Отображение диалога выбора даты.
     */
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

        btnConfirm.setOnClickListener {
            val selectedDate =
                String.format("%02d.%02d.%d", npDay.value, npMonth.value + 1, npYear.value)
            editText.setText(selectedDate)
            dialog.dismiss()
        }
        dialog.show()
    }
}
