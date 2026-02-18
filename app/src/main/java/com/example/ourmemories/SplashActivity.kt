package com.example.ourmemories

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.ui.entering.EnterActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    val firebaseAppCheck = FirebaseAppCheck.getInstance()


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        FirebaseApp.initializeApp(this)

        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)

        tvVersion.text = "Версия ${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            delay(1000)

            val intent = Intent(this@SplashActivity, EnterActivity::class.java)
            startActivity(intent)

            finish()
        }
    }
}