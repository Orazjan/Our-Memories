package com.example.ourmemories.ui.versioninfo

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.databinding.VersionInfoFragmentBinding

class VersionInfoFragment : Fragment() {

    private var _binding: VersionInfoFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: VersionInfoViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = VersionInfoFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[VersionInfoViewModel::class.java]

        setupSpinnerListener()
        observeViewModel()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel() {
        viewModel.versionNames.observe(viewLifecycleOwner) { names ->
            val adapter = ArrayAdapter(
                requireContext(), R.layout.simple_dropdown_item_1line, names
            )
            binding.mySpinner.setAdapter(adapter)

            if (names.isNotEmpty() && binding.mySpinner.text.isEmpty()) {
                binding.mySpinner.setText(names[0], false)
            }
        }

        viewModel.selectedDescription.observe(viewLifecycleOwner) { text ->
            binding.InfoText.text = text
        }

        viewModel.currentAppVersion.observe(viewLifecycleOwner) { versionString ->
            binding.tvAppVersion.text = versionString
        }
    }

    /**
     * Слушатель выбора версии из спиннера.
     */
    private fun setupSpinnerListener() {
        binding.mySpinner.setOnItemClickListener { parent, _, position, _ ->
            val selectedVersion = parent.getItemAtPosition(position) as String
            viewModel.selectVersion(selectedVersion)
        }

        binding.mySpinner.setOnClickListener {
            binding.mySpinner.showDropDown()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}