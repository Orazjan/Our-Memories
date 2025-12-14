package com.example.ourmemories

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class EnterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var prefs: SharedPreferences

    // Флаг, чтобы держать Splash Screen
    private var isChecking = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)


        splashScreen.setKeepOnScreenCondition { isChecking }
        hideSystemUI()


        setContentView(R.layout.activity_enter)

        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()
            Firebase.firestore.firestoreSettings = settings
        } catch (e: Exception) {
        }

        auth = Firebase.auth
        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        lifecycleScope.launch {
            delay(1000)
            checkUserAndNavigate()
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

    private suspend fun checkUserAndNavigate() {
        val user = auth.currentUser

        Log.d("EnterActivity", "Проверка пользователя: ${user?.uid}")

        if (user == null) {
            Log.d("EnterActivity", "Пользователь не найден -> Экран входа/онбординга")
            showLoginOrOnboarding()
            isChecking = false
            return
        }

        try {
            withTimeout(2500L) {
                try {
                    user.reload().await()
                } catch (e: Exception) {
                }

                val db = Firebase.firestore
                val doc = db.collection("users").document(user.uid).get().await()

                if (doc.exists()) {
                    Log.d("EnterActivity", "Профиль найден -> Главный экран")
                    navigateToMainApp()
                } else {
                    Log.d("EnterActivity", "Профиль не заполнен -> SetupProfile")
                    isChecking = false
                    showProfileSetup()
                }
            }
        } catch (e: Exception) {
            Log.e("EnterActivity", "Ошибка или таймаут: ${e.message}")
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
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }

    fun showOnboardingStep2() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, OnboardingStep2Fragment()).addToBackStack(null)
            .commit()
    }

    fun finishOnboarding() {
        showRegistration()
    }

    fun showRegistration() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, RegFragment()).addToBackStack(null).commit()
    }

    fun showLogin() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, LoginFragment()).commit()
    }

    fun showProfileSetup() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, SetupProfileFragment()).commit()
    }

    fun showForgotPassword() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, ForgotPasswordFragment()).addToBackStack(null)
            .commit()
    }

    fun onAuthSuccess() {
        prefs.edit().putBoolean("isFirstRun", false).apply()

        lifecycleScope.launch {
            val user = auth.currentUser
            if (user != null) {
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