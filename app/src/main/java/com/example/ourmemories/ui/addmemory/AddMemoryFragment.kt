package com.example.ourmemories.ui.addmemory

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ourmemories.adapters.SelectedImagesAdapter
import com.example.ourmemories.R
import com.example.ourmemories.data.repositories.AddMemoryRepository
import com.example.ourmemories.databinding.AddMemoryFragmentBinding
import com.example.ourmemories.utils.DatePickerHelper
import com.example.ourmemories.utils.ImageHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddMemoryFragment : Fragment() {
    private var _binding: AddMemoryFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AddMemoryViewModel
    private lateinit var imagesAdapter: SelectedImagesAdapter

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = AddMemoryFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = AddMemoryRepository()
        val imageHandler = ImageHandler(requireContext())
        val factory =
            AddMemoryViewModelFactory(requireActivity().application, repository, imageHandler)
        viewModel = ViewModelProvider(this, factory)[AddMemoryViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI() {

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val hasImages = (viewModel.selectedUris.value?.size ?: 0) > 0
                val hasTitle = binding.etTitle.text.isNotEmpty()

                if (hasImages || hasTitle) {
                    showExitConfirmationDialog()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()

                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        imagesAdapter =
            SelectedImagesAdapter(images = mutableListOf(), onRemoveClick = { position ->
                viewModel.removeImage(position)
            }, onImageClick = { position ->
                viewModel.setCover(position)
                Toast.makeText(context, getString(R.string.cover_selected), Toast.LENGTH_SHORT)
                    .show()
            })

        binding.rvSelectedImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSelectedImages.adapter = imagesAdapter

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.cardAddPhoto.setOnClickListener {
            try {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, getString(R.string.error_gallery), Toast.LENGTH_SHORT).show()
            }
        }

        binding.etDate.setOnClickListener {
            val currentTimestamp = viewModel.eventDate.value ?: System.currentTimeMillis()
            DatePickerHelper.showDatePicker(
                requireContext(), currentTimestamp
            ) { dateString, timestamp ->
                binding.etDate.setText(dateString)
            }
        }

        binding.btnSaveMemory.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val desc = binding.etDescription.text.toString().trim()
            viewModel.saveMemory(title, desc)
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {

        viewModel.selectedUris.observe(viewLifecycleOwner) { uris ->

            imagesAdapter.images = uris.toMutableList()
            imagesAdapter.notifyDataSetChanged()

            binding.rvSelectedImages.visibility = if (uris.isNotEmpty()) View.VISIBLE else View.GONE
        }


        viewModel.eventDate.observe(viewLifecycleOwner) { timestamp ->
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            binding.etDate.setText(sdf.format(Date(timestamp)))
        }

        viewModel.coverUri.observe(viewLifecycleOwner) { uri ->
            imagesAdapter.setCover(uri)
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

    /**
     * Отображение диалога подтверждения выхода.
     */
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.cancel_creation_title))
            .setMessage(getString(R.string.cancel_creation_message))
            .setPositiveButton(getString(R.string.exit)) { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .setNegativeButton(getString(R.string.stay), null)
            .show()
    }
}