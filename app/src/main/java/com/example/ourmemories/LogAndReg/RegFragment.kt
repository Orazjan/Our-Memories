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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

class RegFragment : Fragment(R.layout.register_fragment) {

    private val TAG = "RegistrationFragment"
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

            // Проверка формата Email
            val isEmailValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
            // Пароль минимум 6 символов
            val isPasswordValid = password.length >= 6

            btnRegister.isEnabled = isEmailValid && isPasswordValid
            btnRegister.alpha = if (btnRegister.isEnabled) 1.0f else 0.5f
        }

        etEmail.doAfterTextChanged {
            etEmail.error = null
            validateInputs()
        }

        etPassword.doAfterTextChanged {
            etPassword.error = null
            if (it != null && it.length < 6) {
                // etPassword.error = "Минимум 6 символов" // Можно включить, если нужно
            }
            validateInputs()
        }

        btnRegister.setOnClickListener {
            // Скрываем клавиатуру
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            // Блокируем UI и показываем загрузку
            btnRegister.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE
            etEmail.isEnabled = false
            etPassword.isEnabled = false

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        // Отправляем письмо подтверждения (опционально, но хорошая практика)
                        user?.sendEmailVerification()?.addOnCompleteListener { _ ->
                            Toast.makeText(
                                requireContext(),
                                "Аккаунт создан!",
                                Toast.LENGTH_LONG
                            ).show()

                            // Переходим к настройке профиля (или в приложение)
                            // onAuthSuccess сам решит, куда направить (в SetupProfile, если профиля нет)
                            (requireActivity() as? EnterActivity)?.onAuthSuccess()
                        }
                    } else {
                        // Ошибка -> Возвращаем UI
                        btnRegister.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        etEmail.isEnabled = true
                        etPassword.isEnabled = true

                        val exception = task.exception
                        val errorMessage = when (exception) {
                            is FirebaseAuthWeakPasswordException -> "Пароль слишком простой"
                            is FirebaseAuthUserCollisionException -> "Этот Email уже занят"
                            is FirebaseNetworkException -> "Нет интернета. Проверьте соединение"
                            else -> "Ошибка: ${exception?.localizedMessage}"
                        }
                        Log.e(TAG, "Ошибка регистрации", exception)
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToLogin.setOnClickListener {
            (requireActivity() as? EnterActivity)?.showLogin()
        }
    }
}