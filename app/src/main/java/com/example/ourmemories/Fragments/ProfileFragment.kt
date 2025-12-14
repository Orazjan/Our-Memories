package com.example.ourmemories.Fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.LogAndReg.SetupProfileFragment
import com.example.ourmemories.MainActivity
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment(R.layout.profile_fragment) {

    private var clickCount = 0
    private val RESET_CLICK_COUNT_DELAY = 500L

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private var myPartnerCode: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            updateProfilePhoto(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация основных Views
        val username = view.findViewById<TextView>(R.id.userName)
        val userPhoto = view.findViewById<ImageView>(R.id.userPhoto)
        val tvPartnerCode = view.findViewById<TextView>(R.id.passwordForPartner)
        val textVersion = view.findViewById<TextView>(R.id.textVersion)

        // Кнопки меню (Находим по ID include)
        val cardEdit = view.findViewById<View>(R.id.cardEditProfile)
        val cardShare = view.findViewById<View>(R.id.cardShareCode)
        val cardTheme = view.findViewById<View>(R.id.cardTheme)
        val cardContact = view.findViewById<View>(R.id.cardContact)
        val cardPrivacy = view.findViewById<View>(R.id.cardPrivacy)
        val cardLogout = view.findViewById<View>(R.id.cardLogout)
        val cardDelete = view.findViewById<View>(R.id.cardDeleteAccount)

        // === НАСТРОЙКА ВИЗУАЛА КНОПОК ===
        setupMenuCard(cardEdit, "Редактировать профиль", android.R.drawable.ic_menu_edit, "#BDE0FE")
        setupMenuCard(cardShare, "Поделиться кодом", android.R.drawable.ic_menu_share, "#FAD1E6")
        setupMenuCard(cardTheme, "Тема приложения", android.R.drawable.ic_menu_view, "#EEEEEE")
        setupMenuCard(
            cardContact, "Написать разработчику", android.R.drawable.ic_dialog_email, "#C8E6C9"
        )
        setupMenuCard(
            cardPrivacy,
            "Политика конфиденциальности",
            android.R.drawable.ic_menu_info_details,
            "#EEEEEE"
        )
        setupMenuCard(
            cardLogout, "Выйти из аккаунта", android.R.drawable.ic_lock_power_off, "#F3F4F6"
        )

        setupMenuCard(cardDelete, "Удалить аккаунт", android.R.drawable.ic_delete, "#FFCDD2")
        cardDelete.findViewById<TextView>(R.id.tvTitle).setTextColor(Color.RED)


        // === ЗАГРУЗКА ДАННЫХ ===
        val user = auth.currentUser
        if (user != null) {
            username.text = user.displayName ?: "Пользователь"
            loadUserPhoto(user.photoUrl, userPhoto)
            loadPartnerCode(user.uid, tvPartnerCode)

            userPhoto.setOnClickListener { pickImage.launch("image/*") }
            username.setOnClickListener { showChangeNameDialog(username) }
        }

        // 1. Редактировать
        cardEdit.setOnClickListener {
            parentFragmentManager.beginTransaction().setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            ).replace(R.id.fragment_container, SetupProfileFragment()).addToBackStack(null).commit()
        }

        // 2. Поделиться
        cardShare.setOnClickListener {
            if (myPartnerCode != null) {
                shareCode(myPartnerCode!!)
            } else {
                Toast.makeText(context, "Код загружается...", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Смена темы
        cardTheme.setOnClickListener {
            toggleTheme()
        }

        // 4. Написать разработчику
        cardContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@ourmemories.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Отзыв о приложении OurMemories")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Нет почтового приложения", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Политика
        cardPrivacy.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
            startActivity(browserIntent)
        }

        // 6. Выход
        cardLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireActivity(), EnterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // 7. Удаление аккаунта
        cardDelete.setOnClickListener {
            showDeleteAccountDialog()
        }

        // Пасхалка с версией
        val versionOfApp = "V 0.1"
        textVersion.text = versionOfApp
        textVersion.setOnClickListener {
            clickCount++
            if (clickCount == 3) {
                val versionFragment = VersionInfoFragment()
                (activity as? MainActivity)?.replaceFragment(versionFragment)
                clickCount = 0
            } else {
                Handler(Looper.getMainLooper()).postDelayed(
                    { if (clickCount < 3) clickCount = 0 }, RESET_CLICK_COUNT_DELAY
                )
            }
        }
    }

    private fun setupMenuCard(card: View, title: String, iconRes: Int, colorHex: String) {
        val tvTitle = card.findViewById<TextView>(R.id.tvTitle)
        val ivIcon = card.findViewById<ImageView>(R.id.ivIcon)

        // Находим фон иконки (CardView внутри LinearLayout внутри корневого CardView)
        // Структура include: RootCard -> LinearLayout -> [IconCard -> ImageView], TextView, Arrow
        try {
            val rootLayout = (card as CardView).getChildAt(0) as android.widget.LinearLayout
            val iconCard = rootLayout.getChildAt(0) as CardView
            iconCard.setCardBackgroundColor(Color.parseColor(colorHex))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tvTitle.text = title
        ivIcon.setImageResource(iconRes)
    }

    private fun loadUserPhoto(url: Uri?, imageView: ImageView) {
        if (url != null) {
            val requestOptions =
                RequestOptions().timeout(30000).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .error(android.R.drawable.stat_notify_error).circleCrop()
            Glide.with(this).load(url).apply(requestOptions).thumbnail(0.1f).into(imageView)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun loadPartnerCode(uid: String, textView: TextView) {
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val code = document.getString("partnerCode")
                myPartnerCode = code
                textView.text = code ?: "Код не создан"
            }
        }
    }

    private fun shareCode(code: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Мой код в OurMemories: $code")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Отправить код"))
    }

    private fun showChangeNameDialog(textView: TextView) {
        val editText = EditText(context)
        editText.setText(textView.text)
        editText.setPadding(40, 40, 40, 40)
        AlertDialog.Builder(requireContext()).setTitle("Изменить имя").setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) updateProfileName(newName, textView)
            }.setNegativeButton("Отмена", null).show()
    }

    private fun updateProfileName(newName: String, textView: TextView) {
        val user = auth.currentUser ?: return
        lifecycleScope.launch {
            try {
                val updates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                user.updateProfile(updates).await()
                db.collection("users").document(user.uid).update("name", newName).await()
                textView.text = newName
                Toast.makeText(context, "Имя обновлено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProfilePhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        Toast.makeText(context, "Загрузка фото...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val compressedData = compressImage(uri)
                val storageRef = storage.reference.child("avatars/${user.uid}.jpg")
                storageRef.putBytes(compressedData).await()
                val downloadUrl = storageRef.downloadUrl.await()

                val updates = UserProfileChangeRequest.Builder().setPhotoUri(downloadUrl).build()
                user.updateProfile(updates).await()
                db.collection("users").document(user.uid).update("photoUrl", downloadUrl.toString())
                    .await()
                user.reload().await()

                val userPhoto = view?.findViewById<ImageView>(R.id.userPhoto)
                if (userPhoto != null) loadUserPhoto(downloadUrl, userPhoto)
                Toast.makeText(context, "Фото обновлено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                requireContext().contentResolver, uri
            )
        }
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.toByteArray()
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext()).setTitle("Удалить аккаунт?")
            .setMessage("Это действие необратимо. Все ваши данные будут удалены.")
            .setPositiveButton("Удалить") { _, _ -> deleteAccount() }
            .setNegativeButton("Отмена", null).show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        lifecycleScope.launch {
            try {
                db.collection("users").document(user.uid).delete().await()
                user.delete().await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Аккаунт удален", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireActivity(), EnterActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "Для удаления нужно выйти и войти снова", Toast.LENGTH_LONG
                    ).show()
                    auth.signOut()
                    startActivity(Intent(requireActivity(), EnterActivity::class.java))
                }
            }
        }
    }

    private fun toggleTheme() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            Toast.makeText(context, "Светлая тема", Toast.LENGTH_SHORT).show()
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Toast.makeText(context, "Темная тема", Toast.LENGTH_SHORT).show()
        }
    }
}