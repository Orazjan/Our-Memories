package com.example.ourmemories

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.ourmemories.LogAndReg.ForgotPasswordFragment
import com.example.ourmemories.LogAndReg.LoginFragment
import com.example.ourmemories.LogAndReg.OnboardingFragment
import com.example.ourmemories.LogAndReg.OnboardingStep2Fragment
import com.example.ourmemories.LogAndReg.RegFragment
import com.example.ourmemories.LogAndReg.SetupProfileFragment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class EnterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter)

        auth = Firebase.auth
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        if (auth.currentUser != null && auth.currentUser!!.isEmailVerified) {
            navigateToMainApp()
            return
        }

        if (savedInstanceState == null) {
            val isFirstRun = prefs.getBoolean("isFirstRun", true)

            if (isFirstRun) {
                loadFragment(OnboardingFragment())
            } else {
                loadFragment(LoginFragment())
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun showOnboardingStep2() {
        supportFragmentManager.beginTransaction()
            // Анимация слайда справа налево (красивый эффект перелистывания)
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, OnboardingStep2Fragment())
            .addToBackStack(null)
            .commit()
    }

    /**
     * Вызывается из OnboardingFragment, когда пользователь нажал "Начать".
     * Запоминаем, что обучение пройдено, и переходим к регистрации.
     */
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
        supportFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, LoginFragment())
            .commit()
    }

    fun showProfileSetup() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SetupProfileFragment())
            .commit() // Без addToBackStack, чтобы нельзя было вернуться к регистрации
    }

    fun showForgotPassword() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ForgotPasswordFragment())
            .addToBackStack(null) // Чтобы кнопка "Назад" вернула на экран логина
            .commit()
    }

    /**
     * Вызывается при успешном входе или регистрации.
     * Запускает главное приложение и закрывает экран входа.
     */
    fun onAuthSuccess() {
        navigateToMainApp()
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}