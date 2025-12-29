package com.example.ourmemories

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.ourmemories.Fragments.GalleryFragment
import com.example.ourmemories.Fragments.MainFragment
import com.example.ourmemories.Fragments.ProfileFragment
import com.example.ourmemories.Fragments.WishlistFragment
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

    // Лаунчер для запроса разрешений (Android 13+)
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

        // Скрываем меню, если открыт вложенный фрагмент (детализация)
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

        // Выбор стартового фрагмента
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    /**
     * Логика обработки системной кнопки "Назад".
     */
    private fun setupBackPressHandler() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. Если есть фрагменты в стеке (детали) -> назад
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                // 2. Если не на главной вкладке -> переход на главную
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    bottomNav.selectedItemId = R.id.nav_home
                    return
                }
                // 3. Если на главной -> выход по двойному нажатию
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
     * Необходим для корректной работы пуш-уведомлений.
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
