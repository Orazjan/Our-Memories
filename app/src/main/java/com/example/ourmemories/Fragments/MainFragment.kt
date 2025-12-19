package com.example.ourmemories.Fragments

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.example.ourmemories.Widget.CoupleWidget
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

    private lateinit var prefs: SharedPreferences

    private var myListener: ListenerRegistration? = null
    private var partnerListener: ListenerRegistration? = null

    private var currentPartnerUid: String? = null
    private var currentRelationshipTimestamp: Long? = null

    private var currentTreePoints: Long = 0

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    private val availableStatuses = listOf(
        "😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "🎉", "💪", "🎧", "🚗", "📚"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)

        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)

        val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)
        val cardMyStatus = view.findViewById<View>(R.id.cardMyStatus)

        // Карточки
        val cardFridge = view.findViewById<View>(R.id.cardFridge)
        val cardTree = view.findViewById<View>(R.id.cardTree)
        val tvHeart = view.findViewById<TextView>(R.id.tvHeartIcon)

        // Обработчики
        cardFridge?.setOnClickListener { showEditNoteDialog() }
        cardTree?.setOnClickListener { showTreeDialog() }

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

        ivMyAvatar.setOnClickListener {
            val user = auth.currentUser
            if (user != null) showStatusPickerDialog(user.uid)
        }

        cardMyStatus.setOnClickListener {
            val user = auth.currentUser
            if (user != null) showStatusPickerDialog(user.uid)
        }

        // Загрузка кэша
        loadCachedData(view)

        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления")
            // Сбрасываем партнеров, чтобы переподключиться
            currentPartnerUid = null
            partnerListener?.remove()
            partnerListener = null
            setupListeners(view, swipeRefreshLayout)
        }

        setupListeners(view, swipeRefreshLayout)
        scheduleNextUpdate()
    }

    // === ВИДЖЕТ И КЭШ ===
    private fun updateWidget() {
        try {
            val context = requireContext()
            val intent = Intent(context, CoupleWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, CoupleWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка виджета", e)
        }
    }

    private fun loadCachedData(view: View) {
        val cachedDate = prefs.getLong("relationship_date", 0)
        if (cachedDate > 0) {
            currentRelationshipTimestamp = cachedDate
            updateDaysCounter(view, cachedDate)
            view.findViewById<TextView>(R.id.tvDaysCount)
                .let { it.isEnabled = true; it.alpha = 1.0f }
        }

        val cachedNote = prefs.getString("shared_note", "")
        if (!cachedNote.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.tvFridgeNote)?.text = cachedNote
        }

        // Мои данные
        val myName = prefs.getString("my_name", "Я")
        val myPhoto = prefs.getString("my_photo", null)
        val myStatus = prefs.getString("my_status", null)

        view.findViewById<TextView>(R.id.tvMyName).text = myName
        GlideHelper.loadAvatar(view.findViewById(R.id.ivMyAvatar), myPhoto, "CACHE_MY")
        updateStatusUI(
            view.findViewById(R.id.cardMyStatus), view.findViewById(R.id.tvMyStatus), myStatus
        )

        // Партнер
        val pUid = prefs.getString("partner_uid", null)
        currentPartnerUid = pUid

        if (pUid != null) {
            val pName = prefs.getString("partner_name", "Партнёр")
            val pPhoto = prefs.getString("partner_photo", null)
            val pStatus = prefs.getString("partner_status", null)

            val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
            val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
            val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)
            val cardPartnerStatus = view.findViewById<View>(R.id.cardPartnerStatus)
            val tvPartnerStatus = view.findViewById<TextView>(R.id.tvPartnerStatus)

            tvPartnerName.text = pName
            GlideHelper.loadAvatar(ivPartnerAvatar, pPhoto, "CACHE_PARTNER")
            updateStatusUI(cardPartnerStatus, tvPartnerStatus, pStatus)

            layoutPartner.setOnClickListener { showPartnerOptions(pUid, pName!!) }
        }
    }

    private fun saveToCache(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    private fun saveLongToCache(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    private fun updateStatusUI(card: View?, text: TextView?, status: String?) {
        if (!status.isNullOrEmpty()) {
            card?.visibility = View.VISIBLE
            text?.text = status
        } else {
            card?.visibility = View.GONE
        }
    }

    // === СЛУШАТЕЛИ FIREBASE ===
    private fun setupListeners(view: View, swipeRefreshLayout: SwipeRefreshLayout) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            swipeRefreshLayout.isRefreshing = false
            return
        }
        val myUid = currentUser.uid

        myListener?.remove()

        myListener = db.collection("users").document(myUid).addSnapshotListener { document, e ->
            swipeRefreshLayout.isRefreshing = false
            if (e != null) return@addSnapshotListener

            if (isAdded && document != null && document.exists()) {
                val myName = document.getString("name") ?: "Я"
                val myPhotoUrl = document.getString("photoUrl")
                val myStatus = document.getString("status")
                val sharedNote = document.getString("sharedNote")
                val treePoints = document.getLong("treePoints") ?: 0
                val partnerUid = document.getString("partnerUid")
                val relationshipDate = document.getLong("relationshipDate") ?: 0

                // Ежедневный бонус
                val lastDailyDate = document.getLong("lastDailyDate") ?: 0L
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(
                    Calendar.SECOND, 0
                ); set(Calendar.MILLISECOND, 0)
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

                // Кэш и Виджет
                saveToCache("my_name", myName)
                saveToCache("my_photo", myPhotoUrl)
                saveToCache("my_status", myStatus)
                saveToCache("shared_note", sharedNote)
                saveToCache("partner_uid", partnerUid)
                saveLongToCache("relationship_date", relationshipDate)
                updateWidget()

                // UI
                view.findViewById<TextView>(R.id.tvMyName).text = myName
                GlideHelper.loadAvatar(view.findViewById(R.id.ivMyAvatar), myPhotoUrl, "MY_AVATAR")
                updateTreeUI(treePoints)
                updateStatusUI(
                    view.findViewById(R.id.cardMyStatus),
                    view.findViewById(R.id.tvMyStatus),
                    myStatus
                )

                if (view.findViewById<TextView>(R.id.tvFridgeNote) != null) {
                    val noteText =
                        if (sharedNote.isNullOrEmpty()) "Оставьте записку для любимого человека..." else sharedNote
                    view.findViewById<TextView>(R.id.tvFridgeNote).text = noteText
                }

                currentRelationshipTimestamp = relationshipDate
                updateDaysCounter(view, relationshipDate)

                val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
                if (partnerUid != null) {
                    tvDaysCount.isEnabled = true
                    tvDaysCount.alpha = 1.0f
                } else {
                    tvDaysCount.isEnabled = false
                    tvDaysCount.alpha = 0.5f
                    saveToCache("partner_name", null)
                    saveToCache("partner_photo", null)
                    updateWidget()
                }

                // Исправлено: Не обновляем currentPartnerUid здесь, чтобы сработала проверка в handlePartnerState
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

            // ПРОВЕРКА ИСПРАВЛЕНА: Теперь мы обновляем currentPartnerUid внутри
            if (partnerUid != currentPartnerUid || partnerListener == null) {
                currentPartnerUid = partnerUid
                partnerListener?.remove()

                partnerListener =
                    db.collection("users").document(partnerUid).addSnapshotListener { pDoc, pE ->
                        if (!isAdded) return@addSnapshotListener
                        if (pE != null) return@addSnapshotListener

                        if (pDoc != null && pDoc.exists()) {
                            val pName = pDoc.getString("name") ?: "Партнёр"
                            val pPhoto = pDoc.getString("photoUrl")
                            val pStatus = pDoc.getString("status")

                            saveToCache("partner_name", pName)
                            saveToCache("partner_photo", pPhoto)
                            saveToCache("partner_status", pStatus)
                            updateWidget()

                            tvPartnerName.text = pName
                            GlideHelper.loadAvatar(ivPartnerAvatar, pPhoto, "PARTNER_AVATAR")
                            updateStatusUI(cardPartnerStatus, tvPartnerStatus, pStatus)
                        }
                    }
            }
        } else {
            partnerListener?.remove()
            partnerListener = null
            currentPartnerUid = null

            tvPartnerName.text = getString(R.string.invite)
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setColorFilter(android.graphics.Color.GRAY)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)

            cardPartnerStatus.visibility = View.GONE
            layoutPartner.setOnClickListener { showInvitePartnerDialog(myUid) }
        }
    }

    // ... Остальные методы (updateTreeUI, showTreeDialog, showEditNoteDialog, updateSharedNote, showStatusPickerDialog, updateStatus, showInvitePartnerDialog, connectPartner, showPartnerOptions, disconnectPartner, updateDaysCounter, calculateDays, updateDaysUI, scheduleNextUpdate, saveRelationshipDate, showRelationshipDatePicker, onDestroyView) ...
    // Вставьте сюда остальные методы из вашего файла или предыдущих ответов (они корректны)

    // === ДЕРЕВО ЛЮБВИ (10 СТАДИЙ) ===
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
            .setPositiveButton("Понятно", null).show()
    }

    // === ЗАПИСКА НА ХОЛОДИЛЬНИКЕ ===
    private fun showEditNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val tvCurrent = view?.findViewById<TextView>(R.id.tvFridgeNote)

        val currentText = tvCurrent?.text.toString()
        if (currentText != "Оставьте записку..." && currentText != "Оставьте записку для любимого человека...") {
            etNote.setText(currentText)
        }

        AlertDialog.Builder(requireContext()).setTitle("Записка на холодильнике")
            .setView(dialogView).setPositiveButton("Сохранить") { _, _ ->
                val newNote = etNote.text.toString().trim()
                updateSharedNote(newNote)
            }.setNegativeButton("Отмена", null).show()
    }

    private fun updateSharedNote(text: String) {
        val myUid = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>("sharedNote" to text)
        val batch = db.batch()

        batch.update(db.collection("users").document(myUid), updates)
        if (currentPartnerUid != null) {
            batch.update(db.collection("users").document(currentPartnerUid!!), updates)
        }

        batch.commit().addOnSuccessListener {
            saveToCache("shared_note", text)
            Toast.makeText(context, "Записка обновлена!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    // === СТАТУСЫ ===
    private fun showStatusPickerDialog(uid: String) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_status_picker)

        val grid = dialog.findViewById<GridLayout>(R.id.gridStatuses)
        val etCustomStatus = dialog.findViewById<EditText>(R.id.etCustomStatus)
        val btnSaveStatus = dialog.findViewById<Button>(R.id.btnSaveStatus)

        btnSaveStatus?.setOnClickListener {
            val text = etCustomStatus?.text.toString().trim()
            if (text.isNotEmpty()) {
                if (text.length <= 20) {
                    updateStatus(uid, text); dialog.dismiss()
                } else {
                    Toast.makeText(context, "Максимум 20 символов", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
                setOnClickListener { updateStatus(uid, emoji); dialog.dismiss() }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED)
            ).apply {
                width = GridLayout.LayoutParams.WRAP_CONTENT; height =
                GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(8, 8, 8, 8); setGravity(Gravity.CENTER)
            }
            grid?.addView(button, params)
        }

        dialog.findViewById<View>(R.id.btnClearStatus)
            ?.setOnClickListener { updateStatus(uid, null); dialog.dismiss() }
        dialog.show()
    }

    private fun updateStatus(uid: String, status: String?) {
        val updates =
            if (status == null) mapOf("status" to FieldValue.delete()) else mapOf("status" to status)
        db.collection("users").document(uid).update(updates).addOnFailureListener {
            Toast.makeText(
                context, "Ошибка обновления статуса", Toast.LENGTH_SHORT
            ).show()
        }
    }

    // === ДИАЛОГИ И ЛОГИКА ===
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
        if (currentPartnerUid != null) {
            Toast.makeText(context, "У вас уже есть партнер!", Toast.LENGTH_SHORT).show()
            btnConnect?.isEnabled = true; btnConnect?.text = getString(R.string.connect)
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
                    if (partnerUid == myUid || !partnerDoc.getString("partnerUid")
                            .isNullOrEmpty()
                    ) {
                        Toast.makeText(context, "Невозможно подключиться", Toast.LENGTH_SHORT)
                            .show()
                        btnConnect?.isEnabled = true; btnConnect?.text = getString(R.string.connect)
                        return@addOnSuccessListener
                    }
                    val myRef = db.collection("users").document(myUid)
                    val partnerRef = db.collection("users").document(partnerUid)
                    db.runBatch { batch ->
                        batch.update(
                            myRef, "partnerUid", partnerUid
                        ); batch.update(partnerRef, "partnerUid", myUid)
                    }.addOnSuccessListener {
                        Toast.makeText(
                            context, getString(R.string.connected), Toast.LENGTH_LONG
                        ).show(); dialog.dismiss()
                    }
                }
            }
    }

    private fun showPartnerOptions(partnerUid: String, partnerName: String) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)
        dialog.findViewById<View>(R.id.btnDisconnect)?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> disconnectPartner(partnerUid) }
                .setNegativeButton("Нет", null).show()
        }
        dialog.show()
    }

    private fun disconnectPartner(partnerUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        val myRef = db.collection("users").document(myUid)
        val partnerRef = db.collection("users").document(partnerUid)
        db.runBatch { batch ->
            batch.update(myRef, "partnerUid", null); batch.update(
            partnerRef, "partnerUid", null
        )
        }.addOnSuccessListener {
            saveToCache("partner_uid", null)
            updateWidget()
            Toast.makeText(
                context, getString(R.string.partner_disconnected), Toast.LENGTH_SHORT
            ).show()
        }
    }

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
            add(Calendar.DAY_OF_YEAR, 1); set(
            Calendar.HOUR_OF_DAY, 0
        ); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
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
        if (currentPartnerUid != null) db.collection("users").document(currentPartnerUid!!)
            .update(updates)
        saveLongToCache("relationship_date", timestamp)
        updateWidget()
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
            selectedCalendar.set(Calendar.YEAR, npYear.value); selectedCalendar.set(
            Calendar.MONTH, npMonth.value
        ); selectedCalendar.set(Calendar.DAY_OF_MONTH, npDay.value)
            saveRelationshipDate(uid, selectedCalendar.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myListener?.remove()
        partnerListener?.remove()
        updateHandler.removeCallbacks(updateRunnable)
    }
}