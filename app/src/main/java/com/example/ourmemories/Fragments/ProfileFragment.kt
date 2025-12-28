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
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.firestore

class ProfileFragment : Fragment(R.layout.profile_fragment) {

    private var clickCount = 0
    private val RESET_CLICK_COUNT_DELAY = 500L

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

    private lateinit var prefs: SharedPreferences

    // Код партнера для шеринга
    private var myPartnerCode: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        applySavedTheme()


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
        val isSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val isDarkNow =
            currentNightMode == AppCompatDelegate.MODE_NIGHT_YES || (currentNightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && isSystemDark)

        val themeTitle = if (isDarkNow) "Светлая тема" else "Тёмная тема"


        /**
         * Настройка меню
         */

        setupMenuCard(cardEdit, "Редактировать профиль", android.R.drawable.ic_menu_edit, "#BDE0FE")
        setupMenuCard(cardShare, "Поделиться кодом", android.R.drawable.ic_menu_share, "#FAD1E6")
        setupMenuCard(cardTheme, themeTitle, android.R.drawable.ic_menu_view, "#EEEEEE")
        setupMenuCard(
            cardContact,
            "Написать разработчику",
            android.R.drawable.ic_dialog_email,
            "#C8E6C9"
        )
        setupMenuCard(
            cardPrivacy,
            "Политика конфиденциальности",
            android.R.drawable.ic_menu_info_details,
            "#EEEEEE"
        )
        setupMenuCard(
            cardLogout,
            "Выйти из аккаунта",
            android.R.drawable.ic_lock_power_off,
            "#F3F4F6"
        )

        // Красная кнопка удаления
        setupMenuCard(cardDelete, "Удалить аккаунт", android.R.drawable.ic_delete, "#FFCDD2")
        cardDelete.findViewById<TextView>(R.id.tvTitle).setTextColor(Color.RED)

        /**
         * Загружаем аватарку и имя пользователя
         */
        val user = auth.currentUser
        if (user != null) {
            username.text = user.displayName ?: "Пользователь"
            GlideHelper.loadAvatar(userPhoto, user.photoUrl?.toString(), "ProfileAvatar")
            loadPartnerCode(user.uid, tvPartnerCode)

            // Клик по аватарке или имени -> Открываем редактирование
            userPhoto.setOnClickListener { openEditProfile() }
            username.setOnClickListener { openEditProfile() }

            // Копирование кода по клику на текст
            tvPartnerCode.setOnClickListener {
                myPartnerCode?.let { code -> copyToClipboard(code) }
            }
        }

        /**
         * Редактировать профиль
         * Поделиться кодом
         * Смена темы
         */

        cardEdit.setOnClickListener {
            openEditProfile()
        }

        // Поделиться
        cardShare.setOnClickListener {
            if (myPartnerCode != null) {
                shareCode(myPartnerCode!!)
            } else {
                Toast.makeText(context, "Код загружается...", Toast.LENGTH_SHORT).show()
            }
        }

        // Смена темы
        cardTheme.setOnClickListener {
            toggleTheme()
        }

        // Написать разработчику
        cardContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@ourmemories.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Отзыв о приложении")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("ContactFragment", "Error sending email", e)
                Toast.makeText(context, "Нет почтового приложения", Toast.LENGTH_SHORT).show()
            }
        }

        // Политика
        cardPrivacy.setOnClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://sites.google.com/view/ourmemories-privacy") // Ваша ссылка
            )
            try {
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
            }
        }

        // Выход
        cardLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireActivity(), EnterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Удаление аккаунта
        cardDelete.setOnClickListener {
            showDeleteAccountDialog()
        }

        // Пасхалка с версией
        val packageManager = requireContext().packageManager
        val packageName = requireContext().packageName

        val versionName = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName
        }

        textVersion.text = "V $versionName"
        textVersion.setOnClickListener {
            clickCount++
            if (clickCount == 3) {
                val versionFragment = VersionInfoFragment()
                (activity as? MainActivity)?.replaceFragment(versionFragment)
                clickCount = 0
            } else {
                Handler(Looper.getMainLooper()).postDelayed(
                    { if (clickCount < 3) clickCount = 0 },
                    RESET_CLICK_COUNT_DELAY
                )
            }
        }
    }

    /**
     * Открытие экрана редактирования
     */
    private fun openEditProfile() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, EditProfileFragment()) // Новый фрагмент
            .addToBackStack(null)
            .commit()
    }

    /**
     * Копирование в буфер обмена
     */
    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Partner Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Код скопирован: $text", Toast.LENGTH_SHORT).show()
    }

    /**
     * Установка карточек для меню
     */
    private fun setupMenuCard(card: View, title: String, iconRes: Int, colorHex: String) {
        val tvTitle = card.findViewById<TextView>(R.id.tvTitle)
        val ivIcon = card.findViewById<ImageView>(R.id.ivIcon)
        try {
            val rootLayout = (card as CardView).getChildAt(0) as android.widget.LinearLayout
            val iconCard = rootLayout.getChildAt(0) as CardView
            iconCard.setCardBackgroundColor(colorHex.toColorInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tvTitle.text = title
        ivIcon.setImageResource(iconRes)
    }

    /**
     * Загрузка кода партнера
     */
    private fun loadPartnerCode(uid: String, textView: TextView) {
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val code = document.getString("partnerCode")
                myPartnerCode = code
                textView.text = code ?: "Код не создан"
            }
        }
    }

    /**
     * Поделиться кодом
     */
    private fun shareCode(code: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Мой код в OurMemories: $code")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Отправить код"))
    }

    /**
     * Диалог для удаления аккаунта
     */
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить аккаунт?")
            .setMessage("Это действие необратимо. Все ваши данные будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> deleteAccount() }
            .setNegativeButton("Отмена", null).show()
    }

    /**
     * Удаление аккаунта
     */
    private fun deleteAccount() {
        val user = auth.currentUser ?: return

        // Сначала пробуем удалить документы из базы
        db.collection("users").document(user.uid).delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Теперь удаляем самого юзера из Auth
                user.delete().addOnSuccessListener {
                    Toast.makeText(context, "Аккаунт удален", Toast.LENGTH_SHORT).show()
                    restartApp()
                }.addOnFailureListener { e ->
                    // Если поймали ошибку "Требуется свежий вход"
                    if (e is FirebaseAuthRecentLoginRequiredException) {
                        showReauthDialog(user)
                    } else {
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "Ошибка удаления данных", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applySavedTheme() {
        // Читаем из кэша (по умолчанию -1, что значит FOLLOW_SYSTEM)
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
        }
    }

    /**
     * Переключение темы
     */
    private fun toggleTheme() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()

        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> {
                // Если было "Системная", смотрим текущую конфигурацию
                val uiMode =
                    requireContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (uiMode == Configuration.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            }
        }

        // Сохраняем выбор в настройки
        prefs.edit().putInt("theme_mode", newMode).apply()

        // Применяем
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun showReauthDialog(user: com.google.firebase.auth.FirebaseUser) {
        val passwordInput = EditText(context).apply {
            hint = "Ваш текущий пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(requireContext()).setTitle("Подтвердите удаление")
            .setMessage("Для безопасности введите ваш пароль еще раз.").setView(passwordInput)
            .setPositiveButton("Подтвердить") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty() && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, password)

                    // Повторная авторизация
                    user.reauthenticate(credential).addOnSuccessListener {
                        // Теперь точно можно удалять
                        user.delete().addOnSuccessListener {
                            Toast.makeText(context, "Аккаунт удален", Toast.LENGTH_SHORT).show()
                            restartApp()
                        }
                    }.addOnFailureListener {
                        Toast.makeText(context, "Неверный пароль", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun restartApp() {
        val intent = Intent(requireActivity(), EnterActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
