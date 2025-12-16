package com.example.ourmemories.LogAndReg

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginFragment : Fragment(R.layout.login_fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val TAG = "LoginFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val tvGoToRegister = view.findViewById<View>(R.id.tv_sign_up)
        val tvForgotPassword = view.findViewById<View>(R.id.btn_forgot_password)
        val etEmail = view.findViewById<EditText>(R.id.loginTextView)
        val etPassword = view.findViewById<EditText>(R.id.passwordTextView)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        // Изначально кнопка неактивна
        btnLogin.isEnabled = false
        btnLogin.alpha = 0.5f

        // Функция валидации
        fun validateInputs() {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Проверка формата Email
            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            // Проверка пароля (минимум 6 символов)
            val isPasswordValid = password.length >= 6

            btnLogin.isEnabled = isEmailValid && isPasswordValid
            btnLogin.alpha = if (btnLogin.isEnabled) 1.0f else 0.5f
        }

        etEmail.doAfterTextChanged {
            etEmail.error = null
            validateInputs()
        }

        etPassword.doAfterTextChanged {
            etPassword.error = null
            validateInputs()
        }

        btnLogin.setOnClickListener {
            // Скрываем клавиатуру
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            // Блокируем UI и показываем загрузку
            btnLogin.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE
            etEmail.isEnabled = false
            etPassword.isEnabled = false

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    // В любом случае восстанавливаем UI (если ошибка) или уходим (если успех)

                    if (task.isSuccessful) {
                        Log.d(TAG, "Вход выполнен успешно")
                        (requireActivity() as? EnterActivity)?.onAuthSuccess()
                    } else {
                        // Ошибка -> Возвращаем UI
                        btnLogin.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        etEmail.isEnabled = true
                        etPassword.isEnabled = true

                        val exception = task.exception
                        val errorMessage = when (exception) {
                            is FirebaseAuthInvalidUserException -> "Пользователь не найден"
                            is FirebaseAuthInvalidCredentialsException -> "Неверный Email или пароль"
                            is FirebaseNetworkException -> "Нет интернета. Проверьте соединение"
                            else -> "Ошибка входа: ${exception?.localizedMessage}"
                        }

                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Login Error", exception)
                    }
                }
        }

        tvGoToRegister.setOnClickListener {
            (activity as? EnterActivity)?.showRegistration()
        }

        tvForgotPassword.setOnClickListener {
            (activity as? EnterActivity)?.showForgotPassword()
        }
    }
}