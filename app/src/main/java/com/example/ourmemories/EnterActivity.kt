package com.example.ourmemories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.LogAndReg.ForgotPasswordFragment
import com.example.ourmemories.LogAndReg.LoginFragment
import com.example.ourmemories.LogAndReg.OnboardingFragment
import com.example.ourmemories.LogAndReg.OnboardingStep2Fragment
import com.example.ourmemories.LogAndReg.RegFragment
import com.example.ourmemories.LogAndReg.SetupProfileFragment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class EnterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    // Флаг, чтобы держать Splash Screen пока мы проверяем базу
    private var isChecking = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Держим заставку, пока isChecking == true
        splashScreen.setKeepOnScreenCondition { isChecking }

        setContentView(R.layout.activity_enter)

        auth = Firebase.auth
        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Запускаем проверку в фоне
        lifecycleScope.launch {
            checkUserAndNavigate()
        }
    }

    private suspend fun checkUserAndNavigate() {
        val user = auth.currentUser

        // Если пользователя нет (вышел или не входил) -> показываем Вход
        if (user == null) {
            isChecking = false
            showLoginOrOnboarding()
            return
        }

        try {
            // Даем на проверку базы 2.5 секунды
            withTimeout(2500L) {
                // Обновляем данные пользователя (необязательно, но полезно для токена)
                try {
                    user.reload().await()
                } catch (e: Exception) {
                }

                // Проверяем, создан ли профиль в базе данных
                val db = Firebase.firestore
                val doc = db.collection("users").document(user.uid).get().await()

                if (doc.exists()) {
                    // Профиль есть -> Идем в приложение
                    navigateToMainApp()
                } else {
                    // Профиля нет (зарегистрировался, но закрыл приложение) -> Идем заполнять
                    isChecking = false
                    showProfileSetup()
                }
            }
        } catch (e: Exception) {
            // === ЕСЛИ ВРЕМЯ ИСТЕКЛО ИЛИ НЕТ ИНТЕРНЕТА ===
            // Так как пользователь залогинен (user != null), мы доверяем ему
            // и пускаем в приложение в оффлайн-режиме.
            navigateToMainApp()
        }
    }

    private fun showLoginOrOnboarding() {
        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            loadFragment(OnboardingFragment())
        } else {
            loadFragment(LoginFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun showOnboardingStep2() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, OnboardingStep2Fragment())
            .addToBackStack(null)
            .commit()
    }

    fun finishOnboarding() {
        prefs.edit().putBoolean("isFirstRun", false).apply()
        showRegistration()
    }

    fun showRegistration() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, RegFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showLogin() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    fun showProfileSetup() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SetupProfileFragment())
            .commit()
    }

    fun showForgotPassword() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ForgotPasswordFragment())
            .addToBackStack(null)
            .commit()
    }

    fun onAuthSuccess() {
        // При успешном входе из Login/Reg
        lifecycleScope.launch {
            val user = auth.currentUser
            if (user != null) {
                // Проверяем базу без таймаута (пользователь уже видит интерфейс)
                val db = Firebase.firestore
                val doc = try {
                    db.collection("users").document(user.uid).get().await()
                } catch (e: Exception) {
                    null
                }

                if (doc != null && doc.exists()) {
                    navigateToMainApp()
                } else {
                    showProfileSetup()
                }
            }
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}