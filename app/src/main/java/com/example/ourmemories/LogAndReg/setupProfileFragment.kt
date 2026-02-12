package com.example.ourmemories.LogAndReg

import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.Factory.SetupProfileFactory
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.SetupProfileRepository
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.Utils.ImageHandler
import com.example.ourmemories.ViewModels.SetupProfileViewModel
import com.example.ourmemories.databinding.SetupProfileFragmentBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class SetupProfileFragment : Fragment() {
    private var _binding: SetupProfileFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SetupProfileViewModel by viewModels() {
        val application = requireActivity().application
        val repository = SetupProfileRepository()
        val imageHandler = ImageHandler(requireContext())
        SetupProfileFactory(application, repository, imageHandler)
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                viewModel.setSelectedImage(uri)
            } else {
                Log.d("ProfileFragment", "Пользователь отменил выбор фото")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SetupProfileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupUI()
        observeViewModel()
    }


    /**
     * Инициализация пользовательского интерфейса.
     */
    private fun setupUI() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && !user.displayName.isNullOrEmpty()) {
            binding.etName.setText(user.displayName)
        }
        binding.cardAvatar.scaleX = 0f
        binding.cardAvatar.scaleY = 0f
        binding.cardAvatar.animate().scaleX(1f).scaleY(1f).setDuration(500)
            .setInterpolator(OvershootInterpolator()).start()
        binding.cardAvatar.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.etDate.setOnClickListener {
            showWheelDatePicker(binding.etDate)
        }

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val date = binding.etDate.text.toString().trim()
            
            var hasError = false
            if (viewModel.selectedImageUri.value == null) {
                shakeView(binding.cardAvatar)
                hasError = true
            }
            if (name.isEmpty()) {
                shakeView(binding.etName)
                binding.etName.error = getString(R.string.your_name)
                hasError = true
            }
            if (date.isEmpty()) {
                shakeView(binding.etDate)
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
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.loadingOverlay.visibility = View.VISIBLE
                binding.btnSaveProfile.isEnabled = false
                binding.btnSaveProfile.text = getString(R.string.saving_data)
            } else {
                binding.loadingOverlay.visibility = View.GONE
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = getString(R.string.ready)
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
                GlideHelper.loadAvatar(binding.ivAvatar, uri)
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
