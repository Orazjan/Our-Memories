package com.example.ourmemories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.LogAndReg.ForgotPasswordFragment
import com.example.ourmemories.LogAndReg.LoginFragment
import com.example.ourmemories.LogAndReg.OnboardingFragment
import com.example.ourmemories.LogAndReg.OnboardingStep2Fragment
import com.example.ourmemories.LogAndReg.RegFragment
import com.example.ourmemories.LogAndReg.SetupProfileFragment
import com.example.ourmemories.Utils.Constants
import com.example.ourmemories.Utils.LocaleHelper
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
        LocaleHelper.onAttach(this)

        viewModel = ViewModelProvider(this)[EnterViewModel::class.java]

        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val themePrefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = themePrefs.getInt(Constants.KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        hideSystemUI()

        splashScreen.setKeepOnScreenCondition {
            viewModel.isChecking.value == true
        }

        setContentView(R.layout.activity_enter)

        setupFirebaseSettings()
        
        if (savedInstanceState == null) {
            val isFirstRun = prefs.getBoolean("isFirstRun", true)
            viewModel.checkUser(isFirstRun)
        }

        observeViewModel()
    }

    /**
     * Настройка Firebase Firestore.
     */
    private fun setupFirebaseSettings() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()
            Firebase.firestore.firestoreSettings = settings
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Наблюдение за состоянием навигации.
     */
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

    /**
     * Скрытие системного UI (status bar и navigation bar).
     */
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Переход в основное приложение.
     */
    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Отображение экрана онбординга.
     */
    fun showOnboarding() {
        loadFragment(OnboardingFragment())
    }

    /**
     * Отображение второго экрана онбординга.
     */
    fun showOnboardingStep2() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, OnboardingStep2Fragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    /**
     * Закрытие онбординга и перехода к регистрации
     */
    fun finishOnboarding() {
        showRegistration()
    }

    /**
     * Отображение экрана регистрации.
     */
    fun showRegistration() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, RegFragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    /**
     * Отображение экрана входа.
     */
    fun showLogin() {
        if (!supportFragmentManager.isStateSaved) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, LoginFragment())
            .commitAllowingStateLoss()
    }

    /**
     * Отображение экрана профиля.
     */
    fun showProfileSetup() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SetupProfileFragment())
            .commitAllowingStateLoss() 
    }

    /**
     * Отображение экрана профиля.
     */
    fun showForgotPassword() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ForgotPasswordFragment()).addToBackStack(null)
            .commitAllowingStateLoss()
    }

    /**
     * Переход в основное приложение после успешной авторизации.
     */
    fun onAuthSuccess() {
        prefs.edit().putBoolean("isFirstRun", false).apply()
        viewModel.onAuthSuccess()
    }

    /**
     * Загрузка фрагмента.
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }
}
