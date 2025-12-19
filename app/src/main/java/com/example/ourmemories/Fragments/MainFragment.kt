package com.example.ourmemories.Fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    private var currentPartnerUid: String? = null
    private var currentRelationshipTimestamp: Long? = null

    // Переменная для хранения текущих очков дерева
    private var currentTreePoints: Long = 0

    // === ТАЙМЕР ДЛЯ ОБНОВЛЕНИЯ ДНЕЙ ===
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    // Список доступных статусов
    private val availableStatuses = listOf(
        "😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "💪", "🎧", "🚗"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)

        val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)
        val cardMyStatus = view.findViewById<View>(R.id.cardMyStatus)

        // 1. Записка на холодильнике
        val cardFridge = view.findViewById<View>(R.id.cardFridge)
        cardFridge?.setOnClickListener {
            showEditNoteDialog()
        }

        // 2. Дерево Любви
        val cardTree = view.findViewById<View>(R.id.cardTree)
        cardTree?.setOnClickListener {
            showTreeDialog()
        }

        // 3. Анимация сердца
        val tvHeart = view.findViewById<TextView>(R.id.tvHeartIcon)
        if (tvHeart != null) {
            val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.heart_beat)
            tvHeart.startAnimation(pulseAnimation)
        }

        tvDaysCount.isEnabled = false
        tvDaysCount.alpha = 0.5f

        // КЛИКИ
        tvDaysCount.setOnClickListener {
            val user = auth.currentUser
            if (user != null) showRelationshipDatePicker(user.uid)
        }

        // Клик по аватарке -> Выбор статуса
        ivMyAvatar.setOnClickListener {
            val user = auth.currentUser
            if (user != null) showStatusPickerDialog(user.uid)
        }

        // Клик по баблу статуса
        cardMyStatus.setOnClickListener {
            val user = auth.currentUser
            if (user != null) showStatusPickerDialog(user.uid)
        }

        // Swipe Refresh
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления")
            currentPartnerUid = null
            setupListeners(view)
        }

        setupListeners(view)
        scheduleNextUpdate()
    }

    // === ЛОГИКА ЗАПИСКИ ===
    private fun showEditNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val tvCurrent = view?.findViewById<TextView>(R.id.tvFridgeNote)

        // Предзаполняем текущим текстом
        val currentText = tvCurrent?.text.toString()
        if (currentText != "Оставьте записку для любимого человека...") {
            etNote.setText(currentText)
        }

        AlertDialog.Builder(requireContext()).setTitle("Записка на холодильнике")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newNote = etNote.text.toString().trim()
                updateSharedNote(newNote)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSharedNote(text: String) {
        val myUid = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>("sharedNote" to text)

        val batch = db.batch()

        // Обновляем у себя
        val myRef = db.collection("users").document(myUid)
        batch.update(myRef, updates)

        // Если есть партнер - обновляем и у него (чтобы он увидел сразу)
        if (currentPartnerUid != null) {
            val partnerRef = db.collection("users").document(currentPartnerUid!!)
            batch.update(partnerRef, updates)
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(
                context, "Записка обновлена!", Toast.LENGTH_SHORT
            ).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    // === ДЕРЕВО ЛЮБВИ (UI + Диалог) ===
    private fun updateTreeUI(points: Long) {
        currentTreePoints = points
        val ivTree = view?.findViewById<ImageView>(R.id.ivTreeIcon)
        val tvLevel = view?.findViewById<TextView>(R.id.tvTreeLevel)
        val progress = view?.findViewById<ProgressBar>(R.id.progressTree)

        if (ivTree == null) return

        var maxPoints = 50
        var levelName = "Росток"
        var iconRes = R.drawable.ic_tree_stage_1

        if (points >= 1000) {
            maxPoints = 2000; levelName = "Древо Вечной Любви"; iconRes =
                R.drawable.ic_tree_stage_10
        } else if (points >= 800) {
            maxPoints = 1000; levelName = "Волшебное Дерево"; iconRes = R.drawable.ic_tree_stage_9
        } else if (points >= 650) {
            maxPoints = 800; levelName = "Изобильное Дерево"; iconRes = R.drawable.ic_tree_stage_8
        } else if (points >= 500) {
            maxPoints = 650; levelName = "Дерево Любви"; iconRes = R.drawable.ic_tree_stage_7
        } else if (points >= 350) {
            maxPoints = 500; levelName = "Цветущее Дерево"; iconRes = R.drawable.ic_tree_stage_6
        } else if (points >= 200) {
            maxPoints = 350; levelName = "Взрослое Дерево"; iconRes = R.drawable.ic_tree_stage_5
        } else if (points >= 100) {
            maxPoints = 200; levelName = "Крепкое Дерево"; iconRes = R.drawable.ic_tree_stage_4
        } else if (points >= 50) {
            maxPoints = 100; levelName = "Молодое Дерево"; iconRes = R.drawable.ic_tree_stage_3
        } else if (points >= 20) {
            maxPoints = 50; levelName = "Саженец"; iconRes = R.drawable.ic_tree_stage_2
        }

        ivTree.setImageResource(iconRes)
        tvLevel?.text = "$levelName ($points очков)"
        progress?.max = maxPoints
        progress?.progress = points.toInt()
    }

    private fun showTreeDialog() {
        AlertDialog.Builder(requireContext()).setTitle("🌳 Дерево Любви")
            .setMessage("Растите ваше дерево, заходя в приложение и добавляя воспоминания!\n\nТекущие очки: $currentTreePoints")
            .setPositiveButton("Отлично") { _, _ -> }.show()
    }

    // === СЛУШАТЕЛИ FIREBASE ===
    private fun setupListeners(view: View) {
        val currentUser = auth.currentUser
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)

        if (currentUser == null) {
            swipeRefreshLayout.isRefreshing = false
            return
        }
        val myUid = currentUser.uid

        myListener?.remove()
        partnerListener?.remove()

        myListener = db.collection("users").document(myUid).addSnapshotListener { document, e ->
            swipeRefreshLayout.isRefreshing = false
            if (e != null) {
                Log.e(TAG, "Ошибка слушателя: ${e.message}")
                return@addSnapshotListener
            }

            if (isAdded && document != null && document.exists()) {
                val myName = document.getString("name") ?: "Я"
                val myPhotoUrl = document.getString("photoUrl")
                val myStatus = document.getString("status")
                // === 1. ЧИТАЕМ ЗАПИСКУ ===
                val sharedNote = document.getString("sharedNote")
                // === 2. ЧИТАЕМ ОЧКИ ДЕРЕВА ===
                val treePoints = document.getLong("treePoints") ?: 0

                val tvMyName = view.findViewById<TextView>(R.id.tvMyName)
                val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)
                val tvMyStatus = view.findViewById<TextView>(R.id.tvMyStatus)
                val cardMyStatus = view.findViewById<View>(R.id.cardMyStatus)
                // === 3. НАХОДИМ TextView ЗАПИСКИ ===
                val tvFridgeNote = view.findViewById<TextView>(R.id.tvFridgeNote)

                tvMyName.text = myName
                GlideHelper.loadAvatar(ivMyAvatar, myPhotoUrl, "MY_AVATAR")
                updateTreeUI(treePoints) // Обновляем дерево

                // Обновляем UI статуса
                if (!myStatus.isNullOrEmpty()) {
                    cardMyStatus.visibility = View.VISIBLE
                    tvMyStatus.text = myStatus
                } else {
                    cardMyStatus.visibility = View.GONE
                }

                // === 4. ОТОБРАЖАЕМ ЗАПИСКУ ===
                if (tvFridgeNote != null) {
                    if (!sharedNote.isNullOrEmpty()) {
                        tvFridgeNote.text = sharedNote
                    } else {
                        tvFridgeNote.text = "Оставьте записку для любимого человека..."
                    }
                }

                val relationshipDate = document.getLong("relationshipDate")
                currentRelationshipTimestamp = relationshipDate
                updateDaysCounter(view, relationshipDate)

                // === ЛОГИКА ЕЖЕДНЕВНЫХ ОЧКОВ ===
                val lastDailyDate = document.getLong("lastDailyDate") ?: 0L
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                if (lastDailyDate < today) {
                    val dailyBonus = 10L
                    db.collection("users").document(myUid).update(
                        mapOf(
                            "treePoints" to FieldValue.increment(dailyBonus),
                            "lastDailyDate" to today
                        )
                    ).addOnSuccessListener {
                        Toast.makeText(
                            context, "Ежедневный бонус: +$dailyBonus очков! 🌳", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val partnerUid = document.getString("partnerUid")
                val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)

                if (partnerUid != null) {
                    tvDaysCount.isEnabled = true
                    tvDaysCount.alpha = 1.0f
                } else {
                    tvDaysCount.isEnabled = false
                    tvDaysCount.alpha = 0.5f
                }

                // Сохраняем ID партнера для записки
                currentPartnerUid = partnerUid

                handlePartnerState(view, myUid, partnerUid)
            }
        }
    }

    private fun handlePartnerState(view: View, myUid: String, partnerUid: String?) {
        val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
        val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
        val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)

        val cardPartnerStatus = view.findViewById<View>(R.id.cardPartnerStatus)
        val tvPartnerStatus = view.findViewById<TextView>(R.id.tvPartnerStatus)

        if (partnerUid != null) {
            layoutPartner.setOnClickListener {
                val name = tvPartnerName.text.toString()
                showPartnerOptions(partnerUid, name)
            }

            if (partnerUid != currentPartnerUid || partnerListener == null) {
                partnerListener?.remove()

                partnerListener =
                    db.collection("users").document(partnerUid).addSnapshotListener { pDoc, pE ->
                        if (!isAdded) return@addSnapshotListener
                        if (pE != null) return@addSnapshotListener

                        if (pDoc != null && pDoc.exists()) {
                            val pName = pDoc.getString("name") ?: "Партнёр"
                            val pPhoto = pDoc.getString("photoUrl")
                            val pStatus = pDoc.getString("status")

                            tvPartnerName.text = pName
                            GlideHelper.loadAvatar(ivPartnerAvatar, pPhoto, "PARTNER_AVATAR")

                            // Статус партнера
                            if (!pStatus.isNullOrEmpty()) {
                                cardPartnerStatus.visibility = View.VISIBLE
                                tvPartnerStatus.text = pStatus
                            } else {
                                cardPartnerStatus.visibility = View.GONE
                            }
                        }
                    }
            }
        } else {
            partnerListener?.remove()
            // currentPartnerUid = null

            tvPartnerName.text = getString(R.string.invite)
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setColorFilter(android.graphics.Color.GRAY)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)

            cardPartnerStatus.visibility = View.GONE

            layoutPartner.setOnClickListener { showInvitePartnerDialog(myUid) }
        }
    }

    // === ВЫБОР СТАТУСА ===
    private fun showStatusPickerDialog(uid: String) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_status_picker)

        val grid = dialog.findViewById<GridLayout>(R.id.gridStatuses)
        val etCustomStatus = dialog.findViewById<EditText>(R.id.etCustomStatus)
        val btnSaveStatus = dialog.findViewById<Button>(R.id.btnSaveStatus)

        // Сохранение текста
        btnSaveStatus?.setOnClickListener {
            val text = etCustomStatus?.text.toString().trim()
            if (text.isNotEmpty()) {
                if (text.length <= 20) {
                    updateStatus(uid, text)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Максимум 20 символов", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Кнопки эмодзи
        availableStatuses.forEach { emoji ->
            val button = TextView(requireContext()).apply {
                text = emoji
                textSize = 32f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true
                )
                setBackgroundResource(outValue.resourceId)

                setOnClickListener {
                    updateStatus(uid, emoji)
                    dialog.dismiss()
                }
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED)
            ).apply {
                width = GridLayout.LayoutParams.WRAP_CONTENT
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(8, 8, 8, 8)
                setGravity(Gravity.CENTER)
            }
            grid?.addView(button, params)
        }

        dialog.findViewById<View>(R.id.btnClearStatus)?.setOnClickListener {
            updateStatus(uid, null) // Сброс
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateStatus(uid: String, status: String?) {
        val updates = if (status == null) {
            mapOf("status" to FieldValue.delete())
        } else {
            mapOf("status" to status)
        }
        db.collection("users").document(uid).update(updates).addOnFailureListener {
            Toast.makeText(context, "Ошибка обновления статуса", Toast.LENGTH_SHORT).show()
        }
    }

    // === ТАЙМЕР ===
    private fun updateDaysCounter(view: View, date: Long?) {
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        if (date != null) {
            val days = calculateDays(date)
            tvDaysCount.text = days.toString()
        } else {
            tvDaysCount.text = "0"
        }
    }

    private fun updateDaysUI() {
        if (isAdded && currentRelationshipTimestamp != null) {
            val days = calculateDays(currentRelationshipTimestamp!!)
            val tvDaysCount = view?.findViewById<TextView>(R.id.tvDaysCount)
            tvDaysCount?.text = days.toString()
        }
    }

    private fun scheduleNextUpdate() {
        val now = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val delay = tomorrow.timeInMillis - now.timeInMillis + 1000
        updateHandler.removeCallbacks(updateRunnable)
        updateHandler.postDelayed(updateRunnable, delay)
    }

    private fun calculateDays(startTimeInMillis: Long): Long {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(
            Calendar.SECOND, 0
        ); set(Calendar.MILLISECOND, 0)
        }
        val start = Calendar.getInstance().apply {
            timeInMillis = startTimeInMillis; set(
            Calendar.HOUR_OF_DAY, 0
        ); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = today.timeInMillis - start.timeInMillis
        return if (diff < 0) 0 else TimeUnit.MILLISECONDS.toDays(diff)
    }

    private fun saveRelationshipDate(uid: String, timestamp: Long) {
        val updates = mapOf("relationshipDate" to timestamp)
        db.collection("users").document(uid).update(updates)
        if (currentPartnerUid != null) {
            db.collection("users").document(currentPartnerUid!!).update(updates)
        }
    }

    private fun showRelationshipDatePicker(uid: String) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        if (currentRelationshipTimestamp != null) calendar.timeInMillis =
            currentRelationshipTimestamp!!
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.minValue = 1950; npYear.maxValue = currentYear
        npYear.value = calendar.get(Calendar.YEAR); npYear.wrapSelectorWheel = false
        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0; npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months; npMonth.value = calendar.get(Calendar.MONTH)
        npDay.minValue = 1; npDay.maxValue = 31; npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value); cal.set(Calendar.MONTH, npMonth.value); cal.set(
                Calendar.DAY_OF_MONTH, 1
            )
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
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

    private fun showInvitePartnerDialog(myUid: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_invite_partner, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etPartnerCode)
        val btnConnect = dialogView.findViewById<Button>(R.id.btnConnect)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnConnect.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length == 8) {
                btnConnect.isEnabled = false; btnConnect.text = getString(R.string.Searching)
                connectPartner(myUid, code, dialog)
            } else {
                Toast.makeText(context, "Введите 8 цифр", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun connectPartner(myUid: String, code: String, dialog: AlertDialog) {
        val btnConnect = dialog.findViewById<Button>(R.id.btnConnect)

        // 1. ПРОВЕРКА: У меня уже есть партнер?
        if (currentPartnerUid != null) {
            Toast.makeText(
                context, "У вас уже есть партнер! Сначала отключитесь.", Toast.LENGTH_SHORT
            ).show()
            btnConnect?.isEnabled = true
            btnConnect?.text = getString(R.string.connect)
            return
        }

        db.collection("users").whereEqualTo("partnerCode", code).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, getString(R.string.Code_not_found), Toast.LENGTH_SHORT)
                        .show()
                    btnConnect?.isEnabled = true; btnConnect?.text = getString(R.string.connect)
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id

                    // 2. ПРОВЕРКА: Это я сам?
                    if (partnerUid == myUid) {
                        Toast.makeText(
                            context,
                            getString(R.string.cant_add_yourself),
                            Toast.LENGTH_SHORT
                        ).show()
                        btnConnect?.isEnabled = true; btnConnect?.text = getString(R.string.connect)
                        return@addOnSuccessListener
                    }

                    // 3. ПРОВЕРКА: У партнера уже есть кто-то?
                    val targetCurrentPartner = partnerDoc.getString("partnerUid")
                    if (!targetCurrentPartner.isNullOrEmpty()) {
                        Toast.makeText(context, "Этот пользователь уже занят", Toast.LENGTH_SHORT)
                            .show()
                        btnConnect?.isEnabled = true; btnConnect?.text = getString(R.string.connect)
                        return@addOnSuccessListener
                    }

                    val myRef = db.collection("users").document(myUid)
                    val partnerRef = db.collection("users").document(partnerUid)
                    db.runBatch { batch ->
                        batch.update(myRef, "partnerUid", partnerUid)
                        batch.update(partnerRef, "partnerUid", myUid)
                    }.addOnSuccessListener {
                        Toast.makeText(context, getString(R.string.connected), Toast.LENGTH_LONG)
                            .show()
                        dialog.dismiss()
                    }.addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "${getString(R.string.error)}: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        btnConnect?.isEnabled = true
                        btnConnect?.text = getString(R.string.connect)
                    }
                }
            }.addOnFailureListener {
                Toast.makeText(context, getString(R.string.error), Toast.LENGTH_SHORT).show()
                btnConnect?.isEnabled = true
                btnConnect?.text = getString(R.string.connect)
            }
    }

    private fun showPartnerOptions(partnerUid: String, partnerName: String) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)
        val btnDisconnect = dialog.findViewById<View>(R.id.btnDisconnect)
        btnDisconnect?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> disconnectPartner(partnerUid) }
                .setNegativeButton(getString(R.string.cancel), null).show()
        }
        dialog.show()
    }

    private fun disconnectPartner(partnerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myRef = db.collection("users").document(myUid)
        val partnerRef = db.collection("users").document(partnerUid)
        db.runBatch { batch ->
            batch.update(myRef, "partnerUid", null)
            batch.update(partnerRef, "partnerUid", null)
        }.addOnSuccessListener {
            Toast.makeText(
                context, getString(R.string.partner_disconnected), Toast.LENGTH_SHORT
            ).show()
            currentPartnerUid = null // Обнуляем локально
            partnerListener?.remove()
            partnerListener = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myListener?.remove()
        partnerListener?.remove()
        updateHandler.removeCallbacks(updateRunnable)
    }
}