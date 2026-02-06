package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.Factory.ProfileFactory
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.ProfileRepository
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.ProfileViewModel
import java.util.concurrent.TimeUnit

/**
 * Фрагмент профиля пользователя.
 * Отображает личную информацию, статистику пары и меню настроек.
 */
class ProfileFragment : Fragment(R.layout.profile_fragment) {
    private val viewModel: ProfileViewModel by viewModels {
        val application = requireActivity().application
        val repository = ProfileRepository()
        ProfileFactory(application, repository)
    }
    private lateinit var prefs: SharedPreferences

    private var clickCount = 0
    private val resetClickRunnable = Runnable { clickCount = 0 }
    private val RESET_CLICK_COUNT_DELAY = 500L
    private val handler = Handler(Looper.getMainLooper())

    private var myPartnerCode: String? = null


    /**
     * Инициализация фрагмента при создании View.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)

        applySavedTheme()

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI(view: View) {

        setupMenuCard(
            view.findViewById(R.id.cardEditProfile), getString(R.string.menu_edit_profile),
            android.R.drawable.ic_menu_edit, "#E3F2FD"
        )
        setupMenuCard(
            view.findViewById(R.id.cardShareCode), getString(R.string.menu_share_code),
            android.R.drawable.ic_menu_share, "#F3E5F5"
        )

        val cardSettings = view.findViewById<View>(R.id.settings)
        setupMenuCard(
            cardSettings, getString(R.string.settings), android.R.drawable.ic_menu_manage, "#E1F5FE"
        )

        setupMenuCard(
            view.findViewById(R.id.instructions), getString(R.string.instructions),
            android.R.drawable.ic_menu_help, "#E0F2F1"
        )

        setupMenuCard(
            view.findViewById(R.id.cardContact), getString(R.string.menu_contact_dev),
            android.R.drawable.ic_dialog_email, "#E0F2F1"
        )
        setupMenuCard(
            view.findViewById(R.id.cardPrivacy), getString(R.string.menu_privacy),
            android.R.drawable.ic_menu_info_details, "#ECEFF1"
        )

        setupMenuCard(
            view.findViewById(R.id.cardLogout), getString(R.string.menu_logout),
            android.R.drawable.ic_lock_power_off, "#FAFAFA"
        )

        val cardDelete = view.findViewById<View>(R.id.cardDeleteAccount)
        setupMenuCard(
            cardDelete,
            getString(R.string.menu_delete_account),
            android.R.drawable.ic_delete,
            "#FFEBEE"
        )
        cardDelete?.findViewById<TextView>(R.id.tvTitle)?.setTextColor(Color.RED)
        view.findViewById<View>(R.id.userPhoto)?.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.cardEditProfile)?.setOnClickListener { openEditProfile() }

        view.findViewById<View>(R.id.instructions)?.setOnClickListener { openInstructions() }

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

        cardSettings?.setOnClickListener {
            (activity as? MainActivity)?.replaceFragment(SettingsFragment())
        }

        view.findViewById<View>(R.id.cardContact)?.setOnClickListener { sendEmail() }
        view.findViewById<View>(R.id.cardPrivacy)?.setOnClickListener { openPrivacyPolicy() }

        view.findViewById<View>(R.id.cardLogout)?.setOnClickListener {
            viewModel.logout()
        }

        view.findViewById<View>(R.id.cardDeleteAccount)
            ?.setOnClickListener { showDeleteAccountDialog() }

        setupVersionInfo(view.findViewById(R.id.textVersion))
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                viewModel.uploadAvatar(uri)
            } else {
                Log.d("ProfileFragment", "Пользователь отменил выбор фото")
            }
        }

    private fun observeViewModel(view: View) {
        val userName = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)

        val tvStatMemories = view.findViewById<TextView>(R.id.tvStatMemories)
        val tvStatWishes = view.findViewById<TextView>(R.id.tvStatWishes)
        val tvStatDays = view.findViewById<TextView>(R.id.tvStatDays)


        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                userName?.text = user.name
                if (userPhoto != null) {
                    GlideHelper.loadAvatar(userPhoto, user.photoUrl, "ProfileAvatar")
                }

                myPartnerCode = user.partnerCode
                tvPartnerCode?.text = user.partnerCode ?: getString(R.string.code_not_created)

                if (tvStatDays != null) {
                    if (user.relationshipDate > 0) {
                        val diff = System.currentTimeMillis() - user.relationshipDate
                        val days = TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
                        tvStatDays.text = days.toString()
                    } else {
                        tvStatDays.text = "0"
                    }
                }
            }
        }
        val pbAvatarLoading = view.findViewById<ProgressBar>(R.id.pdAvatarLoader)

        viewModel.memoriesCount.observe(viewLifecycleOwner) {
            tvStatMemories?.text = it.toString()
        }
        viewModel.wishesCount.observe(viewLifecycleOwner) {
            tvStatWishes?.text = it.toString()
        }

        viewModel.actionState.observe(viewLifecycleOwner) { state ->
            if (state is ProfileViewModel.ActionState.Loading) {
                pbAvatarLoading?.visibility = View.VISIBLE
            } else {
                pbAvatarLoading?.visibility = View.GONE
            }

            when (state) {
                is ProfileViewModel.ActionState.NavigateToLogin -> restartApp()
                is ProfileViewModel.ActionState.ReAuthNeeded -> showReauthDialog(state.email)
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
     * Открытие экрана инструкций.
     */
    private fun openInstructions() {
        (activity as? MainActivity)?.replaceFragment(InstructionFragment())

    }

    /**
     * Установка карточки меню.
     */
    private fun setupMenuCard(card: View?, title: String, iconRes: Int, colorHex: String) {
        if (card == null) return
        card.findViewById<TextView>(R.id.tvTitle)?.text = title
        card.findViewById<ImageView>(R.id.ivIcon)?.setImageResource(iconRes)
        try {
            val rootLayout = (card as CardView).getChildAt(0) as android.view.ViewGroup
            val iconCard = rootLayout.getChildAt(0) as CardView
            iconCard.setCardBackgroundColor(colorHex.toColorInt())
        } catch (e: Exception) {
            e.stackTrace
        }
    }

    /**
     * Открытие диалога для повторной аутентификации.
     */
    private fun showReauthDialog(email: String) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.enter_current_password)
            setPadding(50, 30, 50, 30)
            }
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.confirm_deletion))
            .setMessage(getString(R.string.confirm_deletion_message)).setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val pass = input.text.toString()
                if (pass.isNotEmpty()) viewModel.reauthenticateAndDelete(pass)
            }.setNegativeButton(getString(R.string.cancel), null).show()
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
        viewModel.deleteAccount()

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
            e.printStackTrace()
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
            e.printStackTrace()
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
            e.printStackTrace()
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