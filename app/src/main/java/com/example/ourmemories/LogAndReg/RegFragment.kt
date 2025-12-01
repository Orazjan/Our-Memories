package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button // Изменил импорт для кнопки
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class RegFragment : Fragment(R.layout.register_fragment) {
    val TAG = "RegistrationFragment"
    val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRegister = view.findViewById<Button>(R.id.btn_register) // Используем Button
        val tvGoToLogin = view.findViewById<View>(R.id.tv_login)
        val loginTextView = view.findViewById<TextView>(R.id.loginTextView)
        val passwordTextView = view.findViewById<TextView>(R.id.passwordTextView)

        btnRegister.isEnabled = false

        fun validateInputs() {
            val email = loginTextView.text.toString().trim()
            val password = passwordTextView.text.toString().trim()

            val isEmailValid = email.isNotEmpty() && email.contains("@")
            val isPasswordValid = password.length >= 6

            btnRegister.isEnabled = isEmailValid && isPasswordValid
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
            if (text != null && text.length < 6) {
                passwordTextView.error = "Пароль должен быть не менее 6 символов"
            }
            validateInputs()
        }

        btnRegister.setOnClickListener {
            btnRegister.isEnabled = false
            regUser(loginTextView, passwordTextView)
        }

        tvGoToLogin.setOnClickListener {
            (requireActivity() as? EnterActivity)?.showLogin()
        }
    }

    private fun regUser(
        loginTextView: TextView, passwordTextView: TextView
    ) {
        val email = loginTextView.text.toString().trim()
        val password = passwordTextView.text.toString().trim()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                        if (verifyTask.isSuccessful) {
                            Toast.makeText(
                                requireContext(),
                                "Аккаунт создан! Теперь заполните профиль.",
                                Toast.LENGTH_LONG
                            ).show()

                            (requireActivity() as? EnterActivity)?.showProfileSetup()
                        }
                    }
                } else {
                    view?.findViewById<View>(R.id.btn_register)?.isEnabled = true

                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthWeakPasswordException -> "Пароль слишком простой"
                        is FirebaseAuthUserCollisionException -> "Этот Email уже занят"
                        is FirebaseNetworkException -> "Нет интернета. Проверьте соединение"
                        else -> "Ошибка: ${exception?.message ?: "Неизвестная ошибка"}"
                    }
                    Log.e(TAG, "Ошибка регистрации: $errorMessage")
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }
}