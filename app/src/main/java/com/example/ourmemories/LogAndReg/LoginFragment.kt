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
import com.example.ourmemories.ViewModels.LoginViewModel

class LoginFragment : Fragment(R.layout.login_fragment) {

    private lateinit var viewModel: LoginViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val tvGoToRegister = view.findViewById<View>(R.id.tv_sign_up)
        val tvForgotPassword = view.findViewById<View>(R.id.btn_forgot_password)
        val etEmail = view.findViewById<EditText>(R.id.loginTextView)
        val etPassword = view.findViewById<EditText>(R.id.passwordTextView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.isEnabled = false
        btnLogin.alpha = 0.5f

        fun validateInputs() {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 6
            btnLogin.isEnabled = isEmailValid && isPasswordValid
            btnLogin.alpha = if (btnLogin.isEnabled) 1.0f else 0.5f
        }

        etEmail.doAfterTextChanged { validateInputs() }
        etPassword.doAfterTextChanged { validateInputs() }

        btnLogin.setOnClickListener {
            hideKeyboard(view)
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            viewModel.login(email, password)
        }

        tvGoToRegister.setOnClickListener {
            (activity as? EnterActivity)?.showRegistration()
        }

        tvForgotPassword.setOnClickListener {
            (activity as? EnterActivity)?.showForgotPassword()
        }

        observeViewModel(progressBar, btnLogin, etEmail, etPassword)
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
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

        viewModel.loginSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                (requireActivity() as? EnterActivity)?.onAuthSuccess()
            }
        }
    }

    /**
     * Прячем клавиатуру
     */
    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
