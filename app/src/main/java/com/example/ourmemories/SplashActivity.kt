package com.example.ourmemories

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tvVersion = findViewById<TextView>(R.id.tvVersion)

        tvVersion.text = "Версия ${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            delay(2000)

            val intent = Intent(this@SplashActivity, EnterActivity::class.java) // Или MainActivity
            startActivity(intent)

            finish()
        }
    }
}