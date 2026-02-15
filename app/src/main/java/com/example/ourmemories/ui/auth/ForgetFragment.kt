package com.example.ourmemories.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.databinding.ForgetFragmentBinding
import com.example.ourmemories.ui.entering.EnterActivity

class ForgotPasswordFragment : Fragment() {
    private var _binding: ForgetFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ForgotPasswordViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ForgetFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ForgotPasswordViewModel::class.java]

        binding.btnBackToLogin.setOnClickListener {
            (requireActivity() as EnterActivity).showLogin()
        }

        binding.btnSendReset.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            viewModel.resetPassword(email)
        }

        observeViewModel()
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel() {
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        viewModel.resetSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                viewModel.onResetSuccessHandled()
                (requireActivity() as EnterActivity).showLogin()
            }
        }
    }
}
