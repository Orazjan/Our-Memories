/**
 * Atanyazov Oraz
 * Copyright (c) 2025.
 */
package com.example.ourmemories

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.ourmemories.Fragments.CalendarFragment
import com.example.ourmemories.Fragments.GalleryFragment
import com.example.ourmemories.Fragments.MainFragment
import com.example.ourmemories.Fragments.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Основное активити приложения.
 */
class MainActivity : AppCompatActivity() {

    private val MAIN_TAG = "main_fragment"
    private val GALLERY_TAG = "gallery_fragment"
    private val CALENDAR_TAG = "calendar_fragment"
    private val PROFILE_TAG = "profile_fragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedTag = when (item.itemId) {
                R.id.nav_home -> MAIN_TAG
                R.id.nav_gallery -> GALLERY_TAG
                R.id.nav_calendar -> CALENDAR_TAG
                R.id.nav_profile -> PROFILE_TAG
                else -> return@setOnItemSelectedListener false
            }

            switchFragment(selectedTag)
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
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
     * ПУБЛИЧНЫЙ метод для открытия второстепенных фрагментов
     * (например, VersionInfo или Настройки).
     * Добавляет транзакцию в BackStack, чтобы работала кнопка "Назад".
     */
    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Переключатель основных табов (show/hide).
     */
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
                CALENDAR_TAG -> CalendarFragment()
                PROFILE_TAG -> ProfileFragment()
                else -> MainFragment()
            }

            transaction.add(R.id.fragment_container, fragment, tag)
        } else {
            transaction.show(fragment)
        }

        transaction.setPrimaryNavigationFragment(fragment)
        transaction.setReorderingAllowed(true)
        transaction.commit()
    }
}