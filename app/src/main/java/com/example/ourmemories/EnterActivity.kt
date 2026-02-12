package com.example.ourmemories

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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

    private lateinit var prefs: SharedPreferences
    private val viewModel: EnterViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        LocaleHelper.onAttach(this)
        setupFirebaseSettings()

        prefs = getSharedPreferences(Constants.APP_PREFS, MODE_PRIVATE)
        val themePrefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val savedTheme = themePrefs.getInt(Constants.KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        hideSystemUI()
        splashScreen.setKeepOnScreenCondition {
            viewModel.isChecking.value == true
        }

        setContentView(R.layout.activity_enter)

        if (savedInstanceState == null) {
            val isFirstRun = prefs.getBoolean(Constants.FIRST_RUN, true)
            viewModel.checkUser(isFirstRun)
        }

        observeViewModel()
    }

    /**
     * Наблюдение за состоянием навигации.
     */
    private fun observeViewModel() {
        viewModel.navigationState.observe(this) { state ->
            when (state) {
                is EnterViewModel.NavigationState.NavigateToMain -> navigateToMainApp()
                is EnterViewModel.NavigationState.NavigateToSetupProfile -> showProfileSetup()
                is EnterViewModel.NavigationState.NavigateToLogin -> loadFragment(LoginFragment())
                is EnterViewModel.NavigationState.NavigateToOnboarding -> showOnboarding()
                is EnterViewModel.NavigationState.Idle -> {}
            }
        }
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
     * Загрузка фрагмента.
     */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
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
            .replace(R.id.fragment_container, RegFragment()).addToBackStack(null).commit()
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
        prefs.edit { putBoolean(Constants.FIRST_RUN, false) }
        viewModel.onAuthSuccess()
    }
    /**
     * Переход в основное приложение.
     */
    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
