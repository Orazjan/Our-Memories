package com.example.ourmemories.Fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.MainActivity
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.ProfileViewModel

class ProfileFragment : Fragment(R.layout.profile_fragment) {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var prefs: SharedPreferences

    private var clickCount = 0
    private val RESET_CLICK_COUNT_DELAY = 500L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        applySavedTheme()

        setupUI(view)
        observeViewModel(view)
    }

    private fun setupUI(view: View) {
        // Инициализация Views
        val username = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)
        val textVersion = view.findViewById<TextView>(R.id.textVersion)

        // Кнопки меню
        val cardEdit = view.findViewById<View>(R.id.cardEditProfile)
        val cardShare = view.findViewById<View>(R.id.cardShareCode)
        val cardTheme = view.findViewById<View>(R.id.cardTheme)
        val cardContact = view.findViewById<View>(R.id.cardContact)
        val cardPrivacy = view.findViewById<View>(R.id.cardPrivacy)
        val cardLogout = view.findViewById<View>(R.id.cardLogout)
        val cardDelete = view.findViewById<View>(R.id.cardDeleteAccount)

        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val isDarkNow = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES || (currentNightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && isSystemDark)
        val themeTitle = if (isDarkNow) "Светлая тема" else "Тёмная тема"

        setupMenuCard(cardEdit, "Редактировать профиль", android.R.drawable.ic_menu_edit, "#BDE0FE")
        setupMenuCard(cardShare, "Поделиться кодом", android.R.drawable.ic_menu_share, "#FAD1E6")
        setupMenuCard(cardTheme, themeTitle, android.R.drawable.ic_menu_view, "#EEEEEE")
        setupMenuCard(cardContact, "Написать разработчику", android.R.drawable.ic_dialog_email, "#C8E6C9")
        setupMenuCard(cardPrivacy, "Политика конфиденциальности", android.R.drawable.ic_menu_info_details, "#EEEEEE")
        setupMenuCard(cardLogout, "Выйти из аккаунта", android.R.drawable.ic_lock_power_off, "#F3F4F6")
        setupMenuCard(cardDelete, "Удалить аккаунт", android.R.drawable.ic_delete, "#FFCDD2")
        cardDelete.findViewById<TextView>(R.id.tvTitle).setTextColor(Color.RED)

        // Обработчики кликов
        cardEdit.setOnClickListener { openEditProfile() }
        userPhoto.setOnClickListener { openEditProfile() }
        username.setOnClickListener { openEditProfile() }
        
        tvPartnerCode.setOnClickListener {
            val code = tvPartnerCode.text.toString()
            if (code.isNotEmpty() && code != "Код не создан") copyToClipboard(code)
        }

        cardShare.setOnClickListener {
            val code = viewModel.user.value?.partnerCode
            if (!code.isNullOrEmpty()) shareCode(code) else Toast.makeText(context, "Код загружается...", Toast.LENGTH_SHORT).show()
        }

        cardTheme.setOnClickListener { toggleTheme() }

        cardContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@ourmemories.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Отзыв о приложении")
            }
            try { startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "Нет почтового приложения", Toast.LENGTH_SHORT).show() }
        }

        cardPrivacy.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/ourmemories-privacy")))
            } catch (e: Exception) { Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
        }

        cardLogout.setOnClickListener { viewModel.logout() }
        
        cardDelete.setOnClickListener { showDeleteAccountDialog() }

        // Версия и пасхалка
        setupVersionInfo(textVersion)
    }

    private fun observeViewModel(view: View) {
        val username = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                username.text = user.name
                GlideHelper.loadAvatar(userPhoto, user.photoUrl, "ProfileAvatar")
                tvPartnerCode.text = user.partnerCode ?: "Код не создан"
            }
        }

        viewModel.actionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileViewModel.ActionState.NavigateToLogin -> restartApp()
                is ProfileViewModel.ActionState.ReAuthNeeded -> showReauthDialog(state.email)
                is ProfileViewModel.ActionState.Loading -> {
                    // Можно показать прогресс, если нужно
                }
                else -> {}
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    // === Вспомогательные методы ===

    private fun openEditProfile() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .replace(R.id.fragment_container, EditProfileFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Partner Code", text))
        Toast.makeText(context, "Код скопирован: $text", Toast.LENGTH_SHORT).show()
    }

    private fun shareCode(code: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Мой код в OurMemories: $code")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Отправить код"))
    }

    private fun setupMenuCard(card: View, title: String, iconRes: Int, colorHex: String) {
        val tvTitle = card.findViewById<TextView>(R.id.tvTitle)
        val ivIcon = card.findViewById<ImageView>(R.id.ivIcon)
        try {
            val rootLayout = (card as CardView).getChildAt(0) as android.widget.LinearLayout
            val iconCard = rootLayout.getChildAt(0) as CardView
            iconCard.setCardBackgroundColor(colorHex.toColorInt())
        } catch (e: Exception) { e.printStackTrace() }
        tvTitle.text = title
        ivIcon.setImageResource(iconRes)
    }

    // === Управление темой ===

    private fun applySavedTheme() {
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
        }
    }

    private fun toggleTheme() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                val uiMode = requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (uiMode == Configuration.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            }
        }
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    // === Диалоги и Удаление ===

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить аккаунт?")
            .setMessage("Это действие необратимо. Все ваши данные будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> viewModel.deleteAccount() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun showReauthDialog(email: String) {
        val passwordInput = EditText(context).apply {
            hint = "Ваш текущий пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Подтвердите удаление")
            .setMessage("Для безопасности введите ваш пароль еще раз.")
            .setView(passwordInput)
            .setPositiveButton("Подтвердить") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    viewModel.reauthenticateAndDelete(password)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(requireActivity(), EnterActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun setupVersionInfo(textVersion: TextView) {
        val packageManager = requireContext().packageManager
        val packageName = requireContext().packageName
        val versionName = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (e: Exception) { "1.0" }

        textVersion.text = "V $versionName"
        textVersion.setOnClickListener {
            clickCount++
            if (clickCount == 3) {
                val versionFragment = VersionInfoFragment()
                (activity as? MainActivity)?.replaceFragment(versionFragment)
                clickCount = 0
            } else {
                Handler(Looper.getMainLooper()).postDelayed({ if (clickCount < 3) clickCount = 0 }, RESET_CLICK_COUNT_DELAY)
            }
        }
    }
}
