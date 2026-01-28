package com.example.ourmemories.Fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Utils.AutoStartPermissionHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var prefs: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupUI(view)
    }

    private fun setupUI(view: View) {
        val currentLang = Locale.getDefault().displayLanguage.replaceFirstChar { it.uppercase() }
        val language = getString(R.string.language)
        setupMenuCard(
            view.findViewById(R.id.cardLanguage),
            ("$language: ($currentLang)"),
            android.R.drawable.ic_menu_sort_by_size,
            "#E0F7FA"
        )
        view.findViewById<View>(R.id.cardLanguage).setOnClickListener { showLanguageDialog() }

        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val isSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDarkNow =
            currentNightMode == AppCompatDelegate.MODE_NIGHT_YES || (currentNightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && isSystemDark)
        val themeTitle =
            if (isDarkNow) getString(R.string.theme_light) else getString(R.string.theme_dark)
        val themeColor = if (isDarkNow) "#FFF3E0" else "#FFF8E1"

        setupMenuCard(
            view.findViewById(R.id.cardTheme),
            themeTitle,
            android.R.drawable.ic_menu_view,
            themeColor
        )
        view.findViewById<View>(R.id.cardTheme).setOnClickListener { toggleTheme() }

        setupMenuCard(
            view.findViewById(R.id.cardAutoStart),
            getString(R.string.menu_auto_start),
            android.R.drawable.ic_dialog_alert,
            "#F3E5F5"
        )
        view.findViewById<View>(R.id.cardAutoStart).setOnClickListener { autoStart() }
    }

    private fun showLanguageDialog() {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_language_picker)

        dialog.findViewById<View>(R.id.btnLangRu)?.setOnClickListener {
            setLocale("ru")
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnLangEn)?.setOnClickListener {
            setLocale("en")
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnLangKy)?.setOnClickListener {
            setLocale("ky")
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnLangTk)?.setOnClickListener {
            setLocale("tk")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        requireContext().resources.updateConfiguration(
            config, requireContext().resources.displayMetrics
        )

        prefs.edit().putString("language_code", languageCode).apply()

        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun toggleTheme() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val uiMode =
                    requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (uiMode == Configuration.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            }
        }
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun autoStart() {
        val intent = AutoStartPermissionHelper.getAutoStartPermissionIntent(requireContext())
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    context, getString(R.string.autostart_not_supported), Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(context, getString(R.string.autostart_not_supported), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setupMenuCard(card: View, title: String, iconRes: Int, colorHex: String) {
        card.findViewById<TextView>(R.id.tvTitle).text = title
        card.findViewById<ImageView>(R.id.ivIcon).setImageResource(iconRes)
        try {
            val rootLayout = (card as CardView).getChildAt(0) as android.view.ViewGroup
            val iconCard = rootLayout.getChildAt(0) as CardView
            iconCard.setCardBackgroundColor(colorHex.toColorInt())
        } catch (e: Exception) {
        }
    }
}