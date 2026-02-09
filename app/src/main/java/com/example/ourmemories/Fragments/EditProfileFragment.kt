package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import com.example.ourmemories.databinding.EditProfileFragmentBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class EditProfileFragment : Fragment() {

    private var _binding: EditProfileFragmentBinding? = null
    private val binding get() = _binding!!


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EditProfileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.cardAvatar.setOnClickListener {
            try {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel._toastMessage.value = getString(R.string.error_gallery)
            }
        }

        binding.etBirthDate.setOnClickListener { showWheelDatePicker(binding.etBirthDate) }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val date = binding.etBirthDate.text.toString().trim()
            viewModel.saveChanges(name, date)
        }
    }

    private fun observeViewModel() {
        viewModel.currentName.observe(viewLifecycleOwner) { name ->
            if (binding.etName.text.isEmpty() && name.isNotEmpty()) {
                binding.etName.setText(name)
            }
        }

        viewModel.currentBirthDate.observe(viewLifecycleOwner) { date ->
            if (binding.etBirthDate.text.isEmpty() && date.isNotEmpty()) {
                binding.etBirthDate.setText(date)
            }
        }

        viewModel.currentPhotoUrl.observe(viewLifecycleOwner) { url ->
            if (viewModel.selectedImageUri.value == null) {
                GlideHelper.loadAvatar(binding.ivAvatar, url, "EditProfile_Old")
            }
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                GlideHelper.loadAvatar(binding.ivAvatar, uri)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
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