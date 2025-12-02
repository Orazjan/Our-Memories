package com.example.ourmemories.Fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainFragment : Fragment(R.layout.main_fragment) {

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "MainFragment"

    // Слушатели для реального времени
    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    private var currentPartnerUid: String? = null
    private var currentRelationshipTimestamp: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        val btnSettings = view.findViewById<View>(R.id.btnSettings)
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)

        btnSettings.setOnClickListener {
            val intent = Intent(requireActivity(), EnterActivity::class.java)
            startActivity(intent)
        }

        // Восстановлено: Клик по счетчику дней
        tvDaysCount.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                showRelationshipDatePicker(user.uid)
            }
        }

        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления: перезагружаем слушатели")
            setupListeners(view)
        }

        // Первичный запуск
        setupListeners(view)
    }

    private fun setupListeners(view: View) {
        val currentUser = auth.currentUser
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)

        if (currentUser == null) {
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val myUid = currentUser.uid

        // Отключаем старые слушатели
        myListener?.remove()
        partnerListener?.remove()
        currentPartnerUid = null

        // Слушаем МОЙ документ
        myListener = db.collection("users").document(myUid).addSnapshotListener { document, e ->
            swipeRefreshLayout.isRefreshing = false

            if (e != null) {
                Log.e(TAG, "Ошибка загрузки моего профиля", e)
                return@addSnapshotListener
            }

            if (isAdded && document != null && document.exists()) {
                // --- Мои данные ---
                val myName = document.getString("name") ?: "Я"
                val myPhotoUrl = document.getString("photoUrl")

                val tvMyName = view.findViewById<TextView>(R.id.tvMyName)
                val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)

                tvMyName.text = myName
                loadAvatar(myPhotoUrl, ivMyAvatar)

                // --- Дата отношений ---
                val relationshipDate = document.getLong("relationshipDate")
                currentRelationshipTimestamp = relationshipDate
                updateDaysCounter(view, relationshipDate)

                // --- Партнер ---
                val partnerUid = document.getString("partnerUid")
                handlePartnerState(view, myUid, partnerUid)
            }
        }
    }

    private fun handlePartnerState(view: View, myUid: String, partnerUid: String?) {
        val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
        val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
        val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)

        if (partnerUid != null) {
            // Если партнер есть
            layoutPartner.setOnClickListener(null)

            if (partnerUid != currentPartnerUid || partnerListener == null) {
                currentPartnerUid = partnerUid
                partnerListener?.remove()

                // 3. Слушаем документ ПАРТНЕРА
                partnerListener = db.collection("users").document(partnerUid).addSnapshotListener { pDoc, pE ->
                    if (pE != null) return@addSnapshotListener

                    if (pDoc != null && pDoc.exists()) {
                        val pName = pDoc.getString("name") ?: "Партнёр"
                        val pPhoto = pDoc.getString("photoUrl")

                        tvPartnerName.text = pName
                        loadAvatar(pPhoto, ivPartnerAvatar)
                    }
                }
            }
        } else {
            // Партнера нет
            partnerListener?.remove()
            currentPartnerUid = null

            tvPartnerName.text = "Пригласить"
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)

            layoutPartner.setOnClickListener {
                showInvitePartnerDialog(myUid)
            }
        }
    }

    // === Логика даты ===
    private fun updateDaysCounter(view: View, date: Long?) {
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        if (date != null) {
            val days = calculateDays(date)
            tvDaysCount.text = days.toString()
        } else {
            tvDaysCount.text = "0"
        }
    }

    private fun calculateDays(startTimeInMillis: Long): Long {
        val today = Calendar.getInstance().timeInMillis
        val diff = today - startTimeInMillis
        if (diff < 0) return 0
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    private fun saveRelationshipDate(uid: String, timestamp: Long) {
        val updates = mapOf("relationshipDate" to timestamp)

        // Обновляем дату себе
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Дата обновлена!", Toast.LENGTH_SHORT).show()
            }

        // Обновляем дату партнеру (чтобы у него тоже изменилась)
        if (currentPartnerUid != null) {
            db.collection("users").document(currentPartnerUid!!).update(updates)
        }
    }

    // === Диалог выбора даты ===
    private fun showRelationshipDatePicker(uid: String) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        if (currentRelationshipTimestamp != null) {
            calendar.timeInMillis = currentRelationshipTimestamp!!
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.minValue = 1950
        npYear.maxValue = currentYear
        npYear.value = calendar.get(Calendar.YEAR)
        npYear.wrapSelectorWheel = false

        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value)
            cal.set(Calendar.MONTH, npMonth.value)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            npDay.maxValue = maxDay
        }

        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        updateDaysInMonth()

        btnConfirm.setOnClickListener {
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(Calendar.YEAR, npYear.value)
            selectedCalendar.set(Calendar.MONTH, npMonth.value)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, npDay.value)

            saveRelationshipDate(uid, selectedCalendar.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    // Оптимизированный Glide
    private fun loadAvatar(url: String?, imageView: ImageView) {
        if (!url.isNullOrEmpty()) {
            imageView.setPadding(0, 0, 0, 0)

            val requestOptions = RequestOptions()
                .timeout(30000)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.stat_notify_error)
                .circleCrop()

            Glide.with(this)
                .load(url)
                .apply(requestOptions)
                .thumbnail(0.1f)
                .into(imageView)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
            imageView.setPadding(0, 0, 0, 0)
        }
    }

    private fun showInvitePartnerDialog(myUid: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_invite_partner, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etPartnerCode)
        val btnConnect = dialogView.findViewById<Button>(R.id.btnConnect)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnConnect.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length == 8) {
                btnConnect.isEnabled = false
                btnConnect.text = "Поиск..."
                connectPartner(myUid, code, dialog)
            } else {
                Toast.makeText(context, "Введите 8 цифр", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun connectPartner(myUid: String, code: String, dialog: AlertDialog) {
        db.collection("users")
            .whereEqualTo("partnerCode", code)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, "Код не найден", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id

                    if (partnerUid == myUid) {
                        Toast.makeText(context, "Нельзя добавить самого себя", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        return@addOnSuccessListener
                    }

                    val myRef = db.collection("users").document(myUid)
                    val partnerRef = db.collection("users").document(partnerUid)

                    db.runBatch { batch ->
                        batch.update(myRef, "partnerUid", partnerUid)
                        batch.update(partnerRef, "partnerUid", myUid)
                    }.addOnSuccessListener {
                        Toast.makeText(context, "Ура! Вы соединены!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ошибка поиска", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myListener?.remove()
        partnerListener?.remove()
    }
}