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

    private var clickCount = 0
    private val resetClickRunnable = Runnable { clickCount = 0 }
    private val RESET_CLICK_COUNT_DELAY = 500L
    private val handler = Handler(Looper.getMainLooper())

    private var myPartnerCode: String? = null

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

        setupMenuCard(
            view.findViewById(R.id.cardEditProfile), getString(R.string.menu_edit_profile),
            android.R.drawable.ic_menu_edit,
            "#BDE0FE"
        )
        setupMenuCard(
            view.findViewById(R.id.cardShareCode), getString(R.string.menu_share_code),
            android.R.drawable.ic_menu_share,
            "#FAD1E6"
        )

        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDarkNow =
            currentNightMode == AppCompatDelegate.MODE_NIGHT_YES || (currentNightMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && isSystemDark)
        val themeTitle =
            if (isDarkNow) getString(R.string.theme_light) else getString(R.string.theme_dark)

        setupMenuCard(
            view.findViewById(R.id.cardTheme),
            themeTitle,
            android.R.drawable.ic_menu_view,
            colorHex = if (isDarkNow) "#BDE0FE" else "#EEEEEE"
        )

        setupMenuCard(
            view.findViewById(R.id.cardContact), getString(R.string.menu_contact_dev),
            android.R.drawable.ic_dialog_email,
            "#C8E6C9"
        )
        setupMenuCard(
            view.findViewById(R.id.cardPrivacy), getString(R.string.menu_privacy),
            android.R.drawable.ic_menu_info_details,
            "#EEEEEE"
        )
        setupMenuCard(
            view.findViewById(R.id.instructions), getString(R.string.menu_instructions),
            android.R.drawable.ic_menu_help,
            "#EEEEEE"
        )
        setupMenuCard(
            view.findViewById(R.id.cardLogout), getString(R.string.menu_logout),
            android.R.drawable.ic_lock_power_off,
            "#F3F4F6"
        )

        val cardDelete = view.findViewById<View>(R.id.cardDeleteAccount)
        setupMenuCard(
            cardDelete,
            getString(R.string.menu_delete_account),
            android.R.drawable.ic_delete,
            "#FFCDD2"
        )
        cardDelete?.findViewById<TextView>(R.id.tvTitle)?.setTextColor(Color.RED)

        view.findViewById<View>(R.id.userPhoto)?.setOnClickListener { pickImage.launch("image/*") }
        view.findViewById<View>(R.id.cardEditProfile)?.setOnClickListener { openEditProfile() }
        view.findViewById<View>(R.id.instructions)?.setOnClickListener { openInstruction() }
        view.findViewById<View>(R.id.cardTheme)?.setOnClickListener { toggleTheme() }
        view.findViewById<View>(R.id.cardLogout)
            ?.setOnClickListener { viewModel.logout(); restartApp() }
        view.findViewById<View>(R.id.cardDeleteAccount)
            ?.setOnClickListener { showDeleteAccountDialog() }

        view.findViewById<View>(R.id.passwordForPartner)?.setOnClickListener {
            myPartnerCode?.let { code -> copyToClipboard(code) }
        }

        view.findViewById<View>(R.id.cardShareCode)?.setOnClickListener {
            if (myPartnerCode != null && myPartnerCode != getString(R.string.code_not_created)) {
                shareCode(myPartnerCode!!)
            } else {
                Toast.makeText(context, getString(R.string.code_loading), Toast.LENGTH_SHORT).show()
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

        username?.text = user.displayName ?: getString(R.string.default_user)
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
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_code_text, code))
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_code_title)))
    }

    /**
     * Копирование кода в буфер обмена.
     */
    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Partner Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
    }

    /**
     * Открытие экрана с инструкцией
     */
    private fun openInstruction() {
        (activity as? MainActivity)?.replaceFragment(InstructionFragment())
    }

    /**
     * Обновление фото профиля пользователя.
     */
    private fun updateProfilePhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        Toast.makeText(context, getString(R.string.loading), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await()

                val updates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUrl).build()
                user.updateProfile(updates).await()
                db.collection("users").document(user.uid).update("photoUrl", downloadUrl.toString())
                    .await()

                loadUserData()
                Toast.makeText(context, getString(R.string.photo_updated), Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    context, getString(R.string.error_generic, e.message), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Открытие диалога удаления аккаунта.
     */
    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteAccount() }
            .setNegativeButton(getString(R.string.cancel), null).show()
    }

    /**
     * Удаление аккаунта пользователя.
     */
    private fun deleteAccount() {
        val user = auth.currentUser ?: return

        user.delete().addOnSuccessListener {
            db.collection("users").document(user.uid).delete()
            Toast.makeText(context, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()
            restartApp()
        }.addOnFailureListener { e ->
            if (e is FirebaseAuthRecentLoginRequiredException) {
                showReauthDialog(user)
            } else {
                Toast.makeText(
                    context, getString(R.string.error_generic, e.message), Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Открытие диалога для повторной авторизации.
     */
    private fun showReauthDialog(user: com.google.firebase.auth.FirebaseUser) {
        val input = EditText(context).apply {
            hint = getString(R.string.enter_current_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_deletion))
            .setMessage(getString(R.string.confirm_deletion_message)).setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val pass = input.text.toString()
                if (pass.isNotEmpty() && user.email != null) {
                    val credential = EmailAuthProvider.getCredential(user.email!!, pass)
                    user.reauthenticate(credential).addOnSuccessListener {
                        deleteAccount()
                    }.addOnFailureListener {
                        Toast.makeText(
                            context, getString(R.string.wrong_password), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Отправка письма на почту разработчика.
     */
    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("atnzvdev@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
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

            handler.removeCallbacks(resetClickRunnable)

            if (clickCount == 3) {
                (activity as? MainActivity)?.replaceFragment(VersionInfoFragment())
                clickCount = 0
            } else {
                handler.postDelayed(resetClickRunnable, RESET_CLICK_COUNT_DELAY)
            }
        }
    }
}

