/**
 * Atanyazov Oraz
 * Copyright (c) 2025.
 */
package com.example.ourmemories

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.ourmemories.Fragments.CalendarFragment
import com.example.ourmemories.Fragments.GalleryFragment
import com.example.ourmemories.Fragments.MainFragment
import com.example.ourmemories.Fragments.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView // Важный импорт

/**
 * Основное активити приложения.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> MainFragment()
                R.id.nav_gallery -> GalleryFragment()
                R.id.nav_calendar -> CalendarFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }

            switchFragment(selectedFragment)

            true
        }

        if (savedInstanceState == null) {
            switchFragment(MainFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    /**
     * Переключатель фрагментов
     * @param fragment
     */
    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment)
            .commitNow()
    }

}