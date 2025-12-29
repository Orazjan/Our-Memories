package com.example.ourmemories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.Fragments.ProfileFragment
import com.example.ourmemories.LogAndReg.ForgotPasswordFragment
import com.example.ourmemories.LogAndReg.LoginFragment
import com.example.ourmemories.LogAndReg.OnboardingFragment
import com.example.ourmemories.LogAndReg.OnboardingStep2Fragment
import com.example.ourmemories.LogAndReg.RegFragment
import com.example.ourmemories.LogAndReg.SetupProfileFragment
import com.example.ourmemories.ViewModels.EnterViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore

class EnterActivity : AppCompatActivity() {

    private lateinit var viewModel: EnterViewModel
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[EnterViewModel::class.java]

        // Применяем настройки темы
        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val themePrefs = getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        val savedTheme = themePrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        hideSystemUI()

        // Управление видимостью Splash Screen
        splashScreen.setKeepOnScreenCondition {
            viewModel.isChecking.value == true
        }

        setContentView(R.layout.activity_enter)

        setupFirebaseSettings()
        
        // Запуск проверки пользователя (только при первом создании Activity)
        if (savedInstanceState == null) {
            val isFirstRun = prefs.getBoolean("isFirstRun", true)
            viewModel.checkUser(isFirstRun)
        }

        observeViewModel()
    }

    private fun setupFirebaseSettings() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()
            Firebase.firestore.firestoreSettings = settings
        } catch (e: Exception) {
            // Игнорируем, если настройки уже применены
        }
    }

    private fun observeViewModel() {
        viewModel.navigationState.observe(this) { state ->
            when (state) {
                is EnterViewModel.NavigationState.NavigateToMain -> navigateToMainApp()
                is EnterViewModel.NavigationState.NavigateToLogin -> showLogin()
                is EnterViewModel.NavigationState.NavigateToOnboarding -> showOnboarding()
                is EnterViewModel.NavigationState.NavigateToSetupProfile -> showProfileSetup()
                else -> {}
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    // === Публичные методы для навигации из фрагментов ===

    fun showOnboarding() {
        loadFragment(OnboardingFragment())
    }

    fun showOnboardingStep2() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, OnboardingStep2Fragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    fun finishOnboarding() {
        showRegistration()
    }

    fun showRegistration() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, RegFragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    fun showLogin() {
        if (!supportFragmentManager.isStateSaved) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    fun showProfileSetup() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SetupProfileFragment())
            .commitAllowingStateLoss() 
    }

    fun showForgotPassword() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ForgotPasswordFragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    // Вызывается из LoginFragment/RegFragment при успехе
    fun onAuthSuccess() {
        prefs.edit().putBoolean("isFirstRun", false).apply()
        viewModel.onAuthSuccess()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }
}
