package com.example.ourmemories

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.ourmemories.Fragments.GalleryFragment
import com.example.ourmemories.Fragments.MainFragment
import com.example.ourmemories.Fragments.ProfileFragment
import com.example.ourmemories.Fragments.WishlistFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Основное активити приложения.
 */
class MainActivity : AppCompatActivity() {

    private val MAIN_TAG = "main_fragment"
    private val GALLERY_TAG = "gallery_fragment"
    private val WISHLIST_TAG = "wishlist_fragment"
    private val PROFILE_TAG = "profile_fragment"

    // Переменная для хранения времени последнего нажатия "Назад"
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Автоматическое скрытие меню при открытии деталей
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

        // === ОБРАБОТКА КНОПКИ НАЗАД ===
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Если есть открытые фрагменты в стеке (детали, настройки) -> закрываем их
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }

                // Если мы НЕ на главной вкладке -> переходим на Главную
                if (bottomNav.selectedItemId != R.id.nav_home) {
                    bottomNav.selectedItemId = R.id.nav_home
                    return
                }

                //  Если мы на Главной -> двойное нажатие для выхода
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish() // Закрываем приложение
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

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

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