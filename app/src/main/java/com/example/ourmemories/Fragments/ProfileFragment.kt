package com.example.ourmemories.Fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore

/**
 * Фрагмент профиля.
 */
class ProfileFragment : Fragment(R.layout.profile_fragment) {
    private var clickCount = 0
    private val RESET_CLICK_COUNT_DELAY = 500L

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val version: TextView = view.findViewById(R.id.textVersion)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)

        val user = auth.currentUser

        if (user != null) {
            username.text = user.displayName ?: "Пользователь"

            user.reload().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val photoUrl = user.photoUrl
                    Log.d("ProfileFragment", "URL фото: $photoUrl")

                    if (photoUrl != null) {
                        // === ОПТИМИЗАЦИЯ ЗАГРУЗКИ ===
                        val requestOptions =
                            RequestOptions().timeout(30000) // 30 секунд на ожидание
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Кэшируем и оригинал, и результат
                                .placeholder(android.R.drawable.ic_menu_camera)
                                .error(android.R.drawable.stat_notify_error).circleCrop()

                        Glide.with(this).load(photoUrl).apply(requestOptions)
                            .thumbnail(0.1f) // Сначала показываем размытую версию
                            .into(userPhoto)
                    } else {
                        userPhoto.setImageResource(android.R.drawable.ic_menu_camera)
                    }
                }
            }

            // Загрузка кода партнера из Firestore
            db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val code = document.getString("partnerCode")
                        tvPartnerCode.text = code ?: "Код не создан"
                    } else {
                        tvPartnerCode.text = "Ошибка данных"
                    }
                }.addOnFailureListener { e ->
                    tvPartnerCode.text = "Ошибка сети"
                    Log.e("ProfileFragment", "Ошибка загрузки кода", e)
                }
        }

        val versionOfApp = "V 0.0.3"
        version.text = versionOfApp

        version.setOnClickListener {
            clickCount++

            if (clickCount == 3) {
                val versionFragment = VersionInfoFragment()
                (activity as? MainActivity)?.replaceFragment(versionFragment)
                clickCount = 0
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (clickCount < 3) {
                        clickCount = 0
                    }
                }, RESET_CLICK_COUNT_DELAY)
            }
        }
    }
}