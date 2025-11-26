package com.example.ourmemories.LogAndReg

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R

class EnterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter)

        if (savedInstanceState == null) {
            loadFragment(LoginFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun showRegistration() {
        loadFragment(RegFragment())
    }

    fun showForgotPassword() {
        loadFragment(ForgotPasswordFragment())
    }

    fun showLogin() {
        loadFragment(LoginFragment())
    }

    fun onAuthSuccess() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}