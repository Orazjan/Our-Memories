package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.ourmemories.Factory.EditProfileViewModelFactory
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.EditProfileRepository
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.Utils.ImageHandler
import com.example.ourmemories.ViewModels.EditProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class EditProfileFragment : Fragment(R.layout.edit_profile_fragment) {

    private lateinit var ivAvatar: ImageView
    private lateinit var etName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var loadingOverlay: View

    private val viewModel: EditProfileViewModel by viewModels {
        val application = requireActivity().application
        val repository = EditProfileRepository()
        val imageHandler = ImageHandler(requireContext())

        EditProfileViewModelFactory(application, repository, imageHandler)
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri)
        } else {
            viewModel._toastMessage.value = "Пользователь отменил выбор фото"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        observeViewModel(view)
    }

    private fun setupUI(view: View) {
        ivAvatar = view.findViewById(R.id.ivAvatar)
        etName = view.findViewById(R.id.etName)
        etBirthDate = view.findViewById(R.id.etBirthDate)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        val btnBack = view.findViewById<View>(R.id.btnBack)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val cardAvatar = view.findViewById<View>(R.id.cardAvatar)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        cardAvatar.setOnClickListener {
            try {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel._toastMessage.value = getString(R.string.error_gallery)
            }
        }

        etBirthDate.setOnClickListener { showWheelDatePicker(etBirthDate) }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val date = etBirthDate.text.toString().trim()
            viewModel.saveChanges(name, date)
        }
    }

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
                GlideHelper.loadAvatar(ivAvatar, url, "EditProfile_Old")
            }
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                GlideHelper.loadAvatar(ivAvatar, uri)
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

    @SuppressLint("DefaultLocale")
    private fun showWheelDatePicker(editText: EditText) {
        val dialog = BottomSheetDialog(
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay) ?: return
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth) ?: return
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear) ?: return
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate) ?: return

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