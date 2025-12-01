package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class LoginFragment : Fragment(R.layout.login_fragment) {

    val auth = FirebaseAuth.getInstance()
    val TAG = "LoginFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogin = view.findViewById<View>(R.id.btn_login)
        val tvGoToRegister = view.findViewById<View>(R.id.tv_sign_up)
        val tvForgotPassword = view.findViewById<View>(R.id.btn_forgot_password)
        val loginTextView = view.findViewById<TextView>(R.id.loginTextView)
        val passwordTextView = view.findViewById<TextView>(R.id.passwordTextView)

        btnLogin.isEnabled = false

        fun validateInputs() {
            val email = loginTextView.text.toString().trim()
            val password = passwordTextView.text.toString().trim()

            val isEmailValid = email.isNotEmpty() && email.contains("@")

            val isPasswordValid = password.length >= 6

            btnLogin.isEnabled = isEmailValid && isPasswordValid
        }

        loginTextView.doAfterTextChanged { text ->
            loginTextView.error = null

            val input = text.toString()
            if (input.isNotEmpty() && !input.contains("@")) {
                loginTextView.error = "Некорректный Email"
            }

            validateInputs()
        }

        passwordTextView.doAfterTextChanged { text ->
            passwordTextView.error = null

            val passwordRegex = Regex("^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$")
            val isPasswordValid = passwordRegex.matches(passwordTextView.text.toString())

            if (!isPasswordValid) {
                passwordTextView.error =
                    "Пароль должен содержать 8+ символов, буквы и цифры"
            }

            validateInputs()
        }


        btnLogin.setOnClickListener {
            btnLogin.isEnabled = false
            loginingUser(loginTextView, passwordTextView)
        }

        tvGoToRegister.setOnClickListener {
            (activity as? EnterActivity)?.showRegistration()
        }

        tvForgotPassword.setOnClickListener {
            (activity as? EnterActivity)?.showForgotPassword()
        }

    }

    fun loginingUser(
        loginTextView: TextView,
        passwordTextView: TextView
    ) {

        val email = loginTextView.text.toString().trim()
        val password = passwordTextView.text.toString().trim()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                view?.findViewById<View>(R.id.btn_login)?.isEnabled = true

                if (task.isSuccessful) {
                    Log.d(TAG, "Вход выполнен успешно")

                    Toast.makeText(
                        requireContext(),
                        "Вход выполнен успешно",
                        Toast.LENGTH_LONG
                    ).show()
                    (requireActivity() as? EnterActivity)?.onAuthSuccess()
                } else {
                    val exception = task.exception

                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Неверный Email или пароль"

                        else -> {
                            exception?.message
                        }
                    }

                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Ошибка входа: $errorMessage")
                }
            }
    }
}

