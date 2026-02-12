package com.example.ourmemories

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.ourmemories.ui.gallery.GalleryFragment
import com.example.ourmemories.ui.main.MainFragment
import com.example.ourmemories.ui.profile.ProfileFragment
import com.example.ourmemories.ui.wishlists.WishlistFragment
import com.example.ourmemories.utils.AutoStartPermissionHelper
import com.example.ourmemories.utils.Constants
import com.example.ourmemories.utils.LocaleHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Главное Activity приложения.
 *
 * Выступает в роли хоста для фрагментов и управляет:
 * 1. Нижней навигацией [BottomNavigationView].
 * 2. Разрешениями на уведомления (POST_NOTIFICATIONS).
 * 3. Актуализацией FCM токена для Push-уведомлений.
 * 4. Обработкой нажатия кнопки "Назад".
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val MAIN_TAG = "main_fragment"
        const val GALLERY_TAG = "gallery_fragment"
        const val WISHLIST_TAG = "wishlist_fragment"
        const val PROFILE_TAG = "profile_fragment"
        const val BACK_PRESS_INTERVAL = 2000L
    }

    private var backPressedTime: Long = 0

    /**
     * Лаунчер для запроса разрешений.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Уведомления отключены", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        LocaleHelper.onAttach(this)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedThemeMode = prefs.getInt(Constants.KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)


        if (AppCompatDelegate.getDefaultNightMode() != savedThemeMode) {
            AppCompatDelegate.setDefaultNightMode(savedThemeMode)
        }

        checkAutoStartPermission(prefs)
        hideSystemUI()
        checkNotificationPermission()
        updateFcmToken()

        setupNavigation()
        setupBackPressHandler()
    }

    /**
     * Настройка нижней навигации и слушателей переключения фрагментов.
     */
    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        supportFragmentManager.addOnBackStackChangedListener {
            bottomNav.visibility = if (supportFragmentManager.backStackEntryCount > 0) View.GONE else View.VISIBLE
        }

        bottomNav.setOnItemSelectedListener { item ->
            val tag = when (item.itemId) {
                R.id.nav_home -> MAIN_TAG
                R.id.nav_gallery -> GALLERY_TAG
                R.id.nav_wishlist -> WISHLIST_TAG
                R.id.nav_profile -> PROFILE_TAG
                else -> return@setOnItemSelectedListener false
            }
            switchFragment(tag)
            true
        }

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    /**
     * Проверка разрешения на автозапуск приложения.
     */
    private fun checkAutoStartPermission(prefs: SharedPreferences) {
        val isAsked = prefs.getBoolean(Constants.KEY_AUTOSTART_ASKED, false)

        if (!isAsked) {
            val intent = AutoStartPermissionHelper.getAutoStartPermissionIntent(this)
            if (intent != null) {
                showAutoStartDialog(intent, prefs)
            }
        }
    }

    /**
     * Показ диалога для автозапуска приложения.
     */
    private fun showAutoStartDialog(intent: Intent, prefs: SharedPreferences) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_autostart_permission, null)
        val btnSettings = dialogView.findViewById<Button>(R.id.btnSettings)
        val btnLater = dialogView.findViewById<TextView>(R.id.btnLater)
        val btnDontAsk = dialogView.findViewById<TextView>(R.id.btnDontAsk)

        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)


        btnSettings.setOnClickListener {
            startActivity(intent)
            prefs.edit().putBoolean(Constants.KEY_AUTOSTART_ASKED, true).apply()
            dialog.dismiss()
        }
        btnLater.setOnClickListener {
            dialog.dismiss()
        }
        btnDontAsk.setOnClickListener {
            prefs.edit().putBoolean(Constants.KEY_AUTOSTART_ASKED, true).apply()
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Логика обработки системной кнопки "Назад".
     */
    private fun setupBackPressHandler() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    bottomNav.selectedItemId = R.id.nav_home
                    return
                }
                if (System.currentTimeMillis() - backPressedTime < BACK_PRESS_INTERVAL) {
                    finish()
                } else {
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /**
     * Запрос разрешения на уведомления для Android 13 (Tiramisu) и выше.
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Обновляет FCM токен текущего пользователя в Firestore.
     */
    private fun updateFcmToken() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            
            val token = task.result
            Firebase.firestore.collection("users").document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener { e -> 
                    Log.e("MainActivity", "Token update failed: ${e.localizedMessage}") 
                }
        }
    }

    /**
     * Скрывает системные панели (status bar, navigation bar) для иммерсивного режима.
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
     * Публичный метод для замены фрагментов с анимацией Fade.
     * Используется для открытия детальных экранов.
     */
    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    /**
     * Переключает основные вкладки приложения, сохраняя их состояние (hide/show).
     */
    private fun switchFragment(tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        val currentFragment = supportFragmentManager.primaryNavigationFragment

        if (currentFragment != null) {
            transaction.hide(currentFragment)
        }

        var fragment = supportFragmentManager.findFragmentByTag(tag)

        if (fragment == null) {
            fragment = when (tag) {
                MAIN_TAG -> MainFragment()
                GALLERY_TAG -> GalleryFragment()
                WISHLIST_TAG -> WishlistFragment()
                PROFILE_TAG -> ProfileFragment()
                else -> MainFragment()
            }
            transaction.add(R.id.fragment_container, fragment, tag)
        } else {
            transaction.show(fragment)
        }

        transaction.setPrimaryNavigationFragment(fragment)
        transaction.setReorderingAllowed(true)
        transaction.commitAllowingStateLoss()
    }
}
