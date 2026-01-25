package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.VersionInfoViewModel

class VersionInfoFragment : Fragment(R.layout.version_info_fragment) {

    private lateinit var viewModel: VersionInfoViewModel
    private lateinit var infoText: TextView
    private lateinit var versionSpinner: AutoCompleteTextView
    private lateinit var tvAppVersion: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[VersionInfoViewModel::class.java]

        initUI(view)
        observeViewModel()
        
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupSpinnerListener()
    }

    /**
     * Инициализация пользовательского интерфейса.
     */
    private fun initUI(view: View) {
        infoText = view.findViewById(R.id.InfoText)
        versionSpinner = view.findViewById(R.id.mySpinner)
        tvAppVersion = view.findViewById(R.id.tvAppVersion)
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel() {
        viewModel.versionNames.observe(viewLifecycleOwner) { names ->
            val adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, names
            )
            versionSpinner.setAdapter(adapter)

            if (names.isNotEmpty() && versionSpinner.text.isEmpty()) {
                versionSpinner.setText(names[0], false)
            }
        }

        viewModel.selectedDescription.observe(viewLifecycleOwner) { text ->
            infoText.text = text
        }

        viewModel.currentAppVersion.observe(viewLifecycleOwner) { versionString ->
            tvAppVersion.text = versionString
        }
    }

    /**
     * Слушатель выбора версии из спиннера.
     */
    private fun setupSpinnerListener() {
        versionSpinner.setOnItemClickListener { parent, _, position, _ ->
            val selectedVersion = parent.getItemAtPosition(position) as String
            viewModel.selectVersion(selectedVersion)
        }

        versionSpinner.setOnClickListener {
            versionSpinner.showDropDown()
        }
    }
}
