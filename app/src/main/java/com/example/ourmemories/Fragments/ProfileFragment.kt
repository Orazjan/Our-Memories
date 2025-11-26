package com.example.ourmemories.Fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R

/**
 * Фрагмент профиля.
 */
class ProfileFragment : Fragment() {
    private var clickCount = 0
    private val RESET_CLICK_COUNT_DELAY = 500L // Время на сброс кликов (в миллисекундах)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Убрана лишняя переменная view и точка с запятой
        return inflater.inflate(R.layout.profile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Убедитесь, что в вашем XML файле (profile_fragment.xml) есть TextView с id "textVersion"
        val version: TextView = view.findViewById(R.id.textVersion)
        val versionOfApp = "V 0.0.1"
        version.text = versionOfApp

        version.setOnClickListener {
            clickCount++

            if (clickCount == 3) {
                val versionFragment = VersionInfoFragment()
                // Теперь этот метод существует и ошибки Unresolved reference не будет
                (activity as? MainActivity)?.replaceFragment(versionFragment)
                clickCount = 0
            } else {
                // Запускаем таймер сброса, если кликов меньше 3
                Handler(Looper.getMainLooper()).postDelayed({
                    if (clickCount < 3) {
                        clickCount = 0
                    }
                }, RESET_CLICK_COUNT_DELAY)
            }
        }
    }
}

