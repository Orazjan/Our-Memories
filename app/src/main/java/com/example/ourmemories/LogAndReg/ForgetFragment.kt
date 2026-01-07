package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.ForgotPasswordViewModel

class ForgotPasswordFragment : Fragment(R.layout.forget_fragment) {

    private lateinit var viewModel: ForgotPasswordViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ForgotPasswordViewModel::class.java]

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val btnBackToLogin = view.findViewById<TextView>(R.id.btnBackToLogin)
        val btnSendReset = view.findViewById<Button>(R.id.btnSendReset)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)

        btnBack.setOnClickListener {
            (requireActivity() as EnterActivity).showLogin()
        }

        btnBackToLogin.setOnClickListener {
            (requireActivity() as EnterActivity).showLogin()
        }

        btnSendReset.setOnClickListener {
            val email = etEmail.text.toString().trim()
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
