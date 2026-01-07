package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
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
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.ProfileViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Фрагмент профиля пользователя.
 * Отображает личную информацию, статистику пары и меню настроек.
 */
class ProfileFragment : Fragment(R.layout.profile_fragment) {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var prefs: SharedPreferences

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    // Для пасхалки
    private var clickCount = 0
    private val resetClickRunnable = Runnable { clickCount = 0 }
    private val RESET_CLICK_COUNT_DELAY = 500L
    private val handler = Handler(Looper.getMainLooper())

    // Код партнера (кэшируем для копирования)
    private var myPartnerCode: String? = null

    // Лаунчер для выбора нового фото из галереи
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            updateProfilePhoto(uri)
        }
    }

    /**
     * Инициализация фрагмента при создании View.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)

        // Применяем тему при создании
        applySavedTheme()

        setupUI(view)
        loadUserData()
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI(view: View) {
        val tvStatMemories = view.findViewById<TextView>(R.id.tvStatMemories)
        val tvStatWishes = view.findViewById<TextView>(R.id.tvStatWishes)
        val tvStatDays = view.findViewById<TextView>(R.id.tvStatDays)

        // === НАСТРОЙКА КАРТОЧЕК МЕНЮ ===
        setupMenuCard(
            view.findViewById(R.id.cardEditProfile),
            "Редактировать профиль",
            android.R.drawable.ic_menu_edit,
            "#BDE0FE"
        )
        setupMenuCard(
            view.findViewById(R.id.cardShareCode),
            "Поделиться кодом",
            android.R.drawable.ic_menu_share,
            "#FAD1E6"
        )

        // Тема
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDarkNow =
            currentNightMode == AppCompatDelegate.MODE_NIGHT_YES || (currentNightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && isSystemDark)
        val themeTitle = if (isDarkNow) "Светлая тема" else "Тёмная тема"

        setupMenuCard(
            view.findViewById(R.id.cardTheme),
            themeTitle,
            android.R.drawable.ic_menu_view,
            "#EEEEEE"
        )

        setupMenuCard(
            view.findViewById(R.id.cardContact),
            "Написать разработчику",
            android.R.drawable.ic_dialog_email,
            "#C8E6C9"
        )
        setupMenuCard(
            view.findViewById(R.id.cardPrivacy),
            "Политика конфиденциальности",
            android.R.drawable.ic_menu_info_details,
            "#EEEEEE"
        )
        setupMenuCard(
            view.findViewById(R.id.cardLogout),
            "Выйти из аккаунта",
            android.R.drawable.ic_lock_power_off,
            "#F3F4F6"
        )

        val cardDelete = view.findViewById<View>(R.id.cardDeleteAccount)
        setupMenuCard(cardDelete, "Удалить аккаунт", android.R.drawable.ic_delete, "#FFCDD2")
        cardDelete?.findViewById<TextView>(R.id.tvTitle)?.setTextColor(Color.RED)

        // === КЛИКИ ===
        view.findViewById<View>(R.id.cardEditProfile)?.setOnClickListener { openEditProfile() }
        view.findViewById<View>(R.id.userPhoto)?.setOnClickListener { pickImage.launch("image/*") }
        view.findViewById<View>(R.id.cardTheme)?.setOnClickListener { toggleTheme() }
        view.findViewById<View>(R.id.cardLogout)
            ?.setOnClickListener { viewModel.logout(); restartApp() }
        view.findViewById<View>(R.id.cardDeleteAccount)
            ?.setOnClickListener { showDeleteAccountDialog() }

        // Копирование кода
        view.findViewById<View>(R.id.passwordForPartner)?.setOnClickListener {
            myPartnerCode?.let { code -> copyToClipboard(code) }
        }

        // Поделиться кодом
        view.findViewById<View>(R.id.cardShareCode)?.setOnClickListener {
            if (myPartnerCode != null && myPartnerCode != "Код не создан") {
                shareCode(myPartnerCode!!)
            } else {
                Toast.makeText(context, "Код загружается...", Toast.LENGTH_SHORT).show()
            }
        }

        val userName = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)
        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                userName?.text = user.name
                if (userPhoto != null) {
                    GlideHelper.loadAvatar(userPhoto, user.photoUrl, "Profile")
                }

                myPartnerCode = user.partnerCode
                tvPartnerCode?.text = user.partnerCode ?: "---"

                if (tvStatDays != null) {
                    if (user.relationshipDate > 0) {
                        val diff = System.currentTimeMillis() - user.relationshipDate
                        val days = TimeUnit.MILLISECONDS.toDays(diff)
                        tvStatDays.text = days.toString()
                    } else {
                        tvStatDays.text = "0"
                    }
                }
            }
        }

        viewModel.memoriesCount.observe(viewLifecycleOwner) {
            tvStatMemories?.text = it.toString()
        }
        viewModel.wishesCount.observe(viewLifecycleOwner) {
            tvStatWishes?.text = it.toString()
        }

        view.findViewById<View>(R.id.cardContact)?.setOnClickListener { sendEmail() }
        view.findViewById<View>(R.id.cardPrivacy)?.setOnClickListener { openPrivacyPolicy() }

        setupVersionInfo(view.findViewById(R.id.textVersion))
    }

    /**
     * Загрузка данных пользователя из Firestore.
     */
    private fun loadUserData() {
        val user = auth.currentUser ?: return
        val username = view?.findViewById<TextView>(R.id.userName)
        val userPhoto = view?.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view?.findViewById<TextView>(R.id.passwordForPartner)

        username?.text = user.displayName ?: "Пользователь"
        if (userPhoto != null) {
            GlideHelper.loadAvatar(userPhoto, user.photoUrl?.toString(), "ProfileAvatar")
        }

        if (tvPartnerCode != null) {
            loadPartnerCode(user.uid, tvPartnerCode)
        }
    }

    /**
     * Открытие экрана редактирования профиля.
     */
    private fun openEditProfile() {
        (activity as? MainActivity)?.replaceFragment(EditProfileFragment())
    }

    /**
     * Применяет сохраненную тему из SharedPreferences.
     */
    private fun applySavedTheme() {
        val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
        }
    }

    /**
     * Переключение темы приложения.
     */
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

    /**
     * Установка карточки меню.
     */
    private fun setupMenuCard(card: View?, title: String, iconRes: Int, colorHex: String) {
        if (card == null) return
        card.findViewById<TextView>(R.id.tvTitle)?.text = title
        card.findViewById<ImageView>(R.id.ivIcon)?.setImageResource(iconRes)
        try {
            val rootLayout = (card as CardView).getChildAt(0) as? android.view.ViewGroup
            val iconCard =
                rootLayout?.findViewById(R.id.iconCard) ?: rootLayout?.getChildAt(0) as? CardView

            iconCard?.setCardBackgroundColor(colorHex.toColorInt())
        } catch (e: Exception) {
            // Игнорируем ошибки верстки, чтобы не крашилось
        }
    }

    /**
     * Загрузка кода партнера из Firestore.
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
     * Поделиться кодом партнера.
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
     * Копирование кода в буфер обмена.
     */
    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Partner Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
    }

    /**
     * Обновление фото профиля пользователя.
     */
    private fun updateProfilePhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        Toast.makeText(context, "Загрузка...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await()

                val updates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUrl).build()
                user.updateProfile(updates).await()
                db.collection("users").document(user.uid).update("photoUrl", downloadUrl.toString())
                    .await()

                loadUserData() // Обновляем UI
                Toast.makeText(context, "Фото обновлено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Открытие диалога удаления аккаунта.
     */
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить аккаунт?")
            .setMessage("Это действие нельзя отменить. Все ваши данные будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> deleteAccount() }
            .setNegativeButton("Отмена", null).show()
    }

    /**
     * Удаление аккаунта пользователя.
     */
    private fun deleteAccount() {
        val user = auth.currentUser ?: return

        // 1. Сначала пробуем удалить
        user.delete().addOnSuccessListener {
            db.collection("users").document(user.uid).delete()
            Toast.makeText(context, "Аккаунт удален", Toast.LENGTH_SHORT).show()
            restartApp()
        }.addOnFailureListener { e ->
            if (e is FirebaseAuthRecentLoginRequiredException) {
                showReauthDialog(user)
            } else {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Открытие диалога для повторной авторизации.
     */
    private fun showReauthDialog(user: com.google.firebase.auth.FirebaseUser) {
        val input = EditText(context).apply {
            hint = "Введите текущий пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(requireContext()).setTitle("Подтвердите удаление")
            .setMessage("В целях безопасности подтвердите пароль.").setView(input)
            .setPositiveButton("Подтвердить") { _, _ ->
                val pass = input.text.toString()
                if (pass.isNotEmpty() && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, pass)
                    // 3. Переавторизация
                    user.reauthenticate(credential).addOnSuccessListener {
                        // 4. Повторная попытка удаления после успешного входа
                        deleteAccount()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Неверный пароль", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Отправка письма на почту разработчика.
     */
    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@ourmemories.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Отзыв о приложении")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Нет приложения почты", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Открытие политики конфиденциальности.
     */
    private fun openPrivacyPolicy() {
        val browserIntent = Intent(
            Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/ourmemories-privacy")
        )
        try {
            startActivity(browserIntent)
        } catch (e: Exception) {
        }
    }

    /**
     * Перезапуск приложения после изменения темы.
     */
    private fun restartApp() {
        val intent = Intent(requireActivity(), EnterActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    /**
     * Пасхалка.
     */
    @SuppressLint("SetTextI18n")
    private fun setupVersionInfo(tv: TextView?) {
        if (tv == null) return

        var versionName: String? = "1.0.0"
        try {
            val pInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionName = pInfo.versionName
        } catch (e: Exception) {
        }

        tv.text = "V $versionName"

        tv.setOnClickListener {
            clickCount++

            // Сбрасываем предыдущий таймер сброса
            handler.removeCallbacks(resetClickRunnable)

            if (clickCount == 3) {
                (activity as? MainActivity)?.replaceFragment(VersionInfoFragment())
                clickCount = 0
            } else {
                // Запускаем таймер сброса заново
                handler.postDelayed(resetClickRunnable, RESET_CLICK_COUNT_DELAY)
            }
        }
    }
}