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
 * Основное активити приложения.
 * Управляет навигацией и правами доступа.
 */
class MainActivity : AppCompatActivity() {

    private val MAIN_TAG = "main_fragment"
    private val GALLERY_TAG = "gallery_fragment"
    private val WISHLIST_TAG = "wishlist_fragment"
    private val PROFILE_TAG = "profile_fragment"

    private var backPressedTime: Long = 0

    // Запрос разрешения на уведомления (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(
                this, "Необходимо разрешение для уведомлений", Toast.LENGTH_SHORT
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Скрываем системные панели для полного погружения
        hideSystemUI()

        // 1. Запрашиваем права на уведомления
        askNotificationPermission()

        // 2. Сохраняем токен для пушей
        updateFcmToken()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Автоматически скрываем меню, если открыт детальный фрагмент (в стеке > 0)
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                bottomNav.visibility = View.GONE
            } else {
                bottomNav.visibility = View.VISIBLE
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            val selectedTag = when (item.itemId) {
                R.id.nav_home -> MAIN_TAG
                R.id.nav_gallery -> GALLERY_TAG
                R.id.nav_wishlist -> WISHLIST_TAG
                R.id.nav_profile -> PROFILE_TAG
                else -> return@setOnItemSelectedListener false
            }
            switchFragment(selectedTag)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        // Обработка кнопки Назад
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Если есть фрагменты в стеке (например, детали фото) -> закрываем их
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                // Если мы не на главной -> идем на главную
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    bottomNav.selectedItemId = R.id.nav_home
                    return
                }
                // Если мы на главной -> двойной клик для выхода
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Нажмите еще раз для выхода",
                        Toast.LENGTH_SHORT
                    ).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateFcmToken() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "Token: $token")

            // Сохраняем токен в Firestore
            Firebase.firestore.collection("users").document(user.uid).update("fcmToken", token)
                .addOnFailureListener { e -> Log.e("FCM", "Error saving token", e) }
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

    /**
     * Публичный метод для открытия фрагментов из других мест
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

    private fun switchFragment(tag: String) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        val currentActiveFragment = fragmentManager.primaryNavigationFragment
        if (currentActiveFragment != null) {
            transaction.hide(currentActiveFragment)
        }

        var fragment = fragmentManager.findFragmentByTag(tag)

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