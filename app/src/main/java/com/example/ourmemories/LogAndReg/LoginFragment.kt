package com.example.ourmemories.LogAndReg

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
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

    private var lastClickTime: Long = 0

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

            if (isEmailValid) etEmail.error = null
            if (isPasswordValid) etPassword.error = null
        }

        fun attemptLogin() {
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return
            lastClickTime = SystemClock.elapsedRealtime()

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Некорректная почта"
                etEmail.requestFocus()
                return
            }

            if (password.length < 6) {
                etPassword.error = "Пароль слишком короткий"
                return
            }

            hideKeyboard(view)
            viewModel.login(email, password)
        }

        etEmail.doAfterTextChanged { validateInputs() }
        etPassword.doAfterTextChanged { validateInputs() }

        btnLogin.setOnClickListener {
            attemptLogin()
        }

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                if (btnLogin.isEnabled) {
                    attemptLogin()
                }
                true
            } else {
                false
            }
        }

        tvGoToRegister.setOnClickListener {
            (activity as? EnterActivity)?.showRegistration()
        }

        tvForgotPassword.setOnClickListener {
            (activity as? EnterActivity)?.showForgotPassword()
        }

        observeViewModel(progressBar, btnLogin, etEmail, etPassword)
    }

    private fun observeViewModel(progressBar: ProgressBar, btn: Button, et1: EditText, et2: EditText) {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            if (isLoading) {
                btn.text = ""
                btn.isEnabled = false
                btn.alpha = 0.5f
            } else {
                btn.text = getString(R.string.login)
                btn.visibility = View.VISIBLE
                val emailValid = Patterns.EMAIL_ADDRESS.matcher(et1.text.toString().trim()).matches()
                val passValid = et2.text.toString().trim().length >= 6
                btn.isEnabled = emailValid && passValid
                btn.alpha = if (btn.isEnabled) 1.0f else 0.5f
            }

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

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}