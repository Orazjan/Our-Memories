package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class RegFragment : Fragment(R.layout.register_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ищем кнопки (ID должны совпадать с вашим register_fragment.xml)
        val btnRegister = view.findViewById<View>(R.id.btn_register) // Кнопка "Зарегистрироваться"
        val tvGoToLogin = view.findViewById<View>(R.id.tv_login) // Текст "Уже есть аккаунт? Войти"

        // Логика кнопки Регистрация
        btnRegister.setOnClickListener {
            // Тут логика сохранения данных...

            // ОТЧЁТ: "Шеф, мы зарегистрировались, пускай в приложение"
            (requireActivity() as EnterActivity).onAuthSuccess()
        }

        // Логика ссылки на Вход
        tvGoToLogin.setOnClickListener {
            // ОТЧЁТ: "Шеф, верни нас на экран входа"
            (requireActivity() as EnterActivity).showLogin()
        }
    }
}