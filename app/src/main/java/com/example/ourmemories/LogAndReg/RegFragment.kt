package com.example.ourmemories.LogAndReg

import android.content.Context
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.RegisterViewModel

class RegFragment : Fragment(R.layout.register_fragment) {

    private lateinit var viewModel: RegisterViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[RegisterViewModel::class.java]

        val btnRegister = view.findViewById<Button>(R.id.btn_register)
        val tvGoToLogin = view.findViewById<View>(R.id.tv_login)
        val etEmail = view.findViewById<EditText>(R.id.loginTextView)
        val etPassword = view.findViewById<EditText>(R.id.passwordTextView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        // Изначально кнопка неактивна
        btnRegister.isEnabled = false
        btnRegister.alpha = 0.5f

        fun validateInputs() {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 6

            btnRegister.isEnabled = isEmailValid && isPasswordValid
            btnRegister.alpha = if (btnRegister.isEnabled) 1.0f else 0.5f
        }

        etEmail.doAfterTextChanged { validateInputs() }
        etPassword.doAfterTextChanged { validateInputs() }

        btnRegister.setOnClickListener {
            hideKeyboard(view)
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            viewModel.register(email, password)
        }

        tvGoToLogin.setOnClickListener {
            (requireActivity() as? EnterActivity)?.showLogin()
        }

        observeViewModel(progressBar, btnRegister, etEmail, etPassword)
    }

    private fun observeViewModel(progressBar: ProgressBar, btn: Button, et1: EditText, et2: EditText) {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btn.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
            et1.isEnabled = !isLoading
            et2.isEnabled = !isLoading
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.onToastShown()
            }
        }

        viewModel.authSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                (requireActivity() as? EnterActivity)?.onAuthSuccess()
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
