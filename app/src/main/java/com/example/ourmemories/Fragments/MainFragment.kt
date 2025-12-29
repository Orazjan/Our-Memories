package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MainViewModel
import com.example.ourmemories.Widget.CoupleWidget
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainFragment : Fragment(R.layout.main_fragment) {

    // Инициализация ViewModel через ViewModelProvider (классический способ)
    private lateinit var viewModel: MainViewModel
    
    private lateinit var prefs: SharedPreferences
    private val updateHandler = Handler(Looper.getMainLooper())
    private var currentRelationshipTimestamp: Long = 0

    private val availableStatuses = listOf(
        "😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "🎉", "💪", "🎧", "🚗", "📚"
    )

    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)

        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
        scheduleNextUpdate()
    }

    private fun setupUI(view: View) {
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)
        val cardMyStatus = view.findViewById<View>(R.id.cardMyStatus)
        val cardFridge = view.findViewById<View>(R.id.cardFridge)
        val cardTree = view.findViewById<View>(R.id.cardTree)
        val tvHeart = view.findViewById<TextView>(R.id.tvHeartIcon)

        cardFridge?.setOnClickListener { showEditNoteDialog() }
        cardTree?.setOnClickListener { showTreeDialog() }

        if (tvHeart != null) {
            val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.heart_beat)
            tvHeart.startAnimation(pulseAnimation)
        }

        tvDaysCount.setOnClickListener {
            viewModel.currentUser.value?.let { user ->
                showRelationshipDatePicker(user.uid)
            }
        }

        val statusClickListener = View.OnClickListener {
            viewModel.currentUser.value?.let { user -> showStatusPickerDialog(user.uid) }
        }
        ivMyAvatar.setOnClickListener(statusClickListener)
        cardMyStatus.setOnClickListener(statusClickListener)

        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.startListening() // Перезапуск слушателей
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun observeViewModel(view: View) {
        // 1. Мои данные
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                updateMyUI(view, user)
                updateWidgetData(user, true)
            }
        }

        // 2. Данные партнера
        viewModel.partnerUser.observe(viewLifecycleOwner) { partner ->
            updatePartnerUI(view, partner)
            if (partner != null) updateWidgetData(partner, false)
        }

        // 3. Сообщения (Toast)
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    private fun updateMyUI(view: View, user: User) {
        view.findViewById<TextView>(R.id.tvMyName).text = user.name
        GlideHelper.loadAvatar(view.findViewById(R.id.ivMyAvatar), user.photoUrl, "MY_AVATAR")
        
        updateStatusUI(
            view.findViewById(R.id.cardMyStatus), 
            view.findViewById(R.id.tvMyStatus), 
            user.status
        )

        updateTreeUI(user.treePoints)

        // Записка
        val tvFridgeNote = view.findViewById<TextView>(R.id.tvFridgeNote)
        if (tvFridgeNote != null) {
            tvFridgeNote.text = if (user.sharedNote.isNullOrEmpty()) 
                "Оставьте записку для любимого человека..." 
            else 
                user.sharedNote
        }

        // Дата отношений
        currentRelationshipTimestamp = user.relationshipDate
        updateDaysCounter(view, user.relationshipDate)

        // Доступность счетчика
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        if (user.partnerUid != null) {
            tvDaysCount.isEnabled = true
            tvDaysCount.alpha = 1.0f
        } else {
            tvDaysCount.isEnabled = false
            tvDaysCount.alpha = 0.5f
        }
    }

    private fun updatePartnerUI(view: View, partner: User?) {
        val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
        val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
        val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)
        val cardPartnerStatus = view.findViewById<View>(R.id.cardPartnerStatus)
        val tvPartnerStatus = view.findViewById<TextView>(R.id.tvPartnerStatus)

        if (partner != null) {
            tvPartnerName.text = partner.name
            GlideHelper.loadAvatar(ivPartnerAvatar, partner.photoUrl, "PARTNER_AVATAR")
            updateStatusUI(cardPartnerStatus, tvPartnerStatus, partner.status)

            layoutPartner.setOnClickListener {
                showPartnerOptions(partner.uid, partner.name)
            }
        } else {
            // Нет партнера
            tvPartnerName.text = getString(R.string.invite)
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setColorFilter(android.graphics.Color.GRAY)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)
            
            cardPartnerStatus.visibility = View.GONE
            
            layoutPartner.setOnClickListener { 
                viewModel.currentUser.value?.let { me -> showInvitePartnerDialog(me.uid) }
            }
        }
    }

    // === Вспомогательные методы UI ===

    private fun updateStatusUI(card: View?, text: TextView?, status: String?) {
        if (!status.isNullOrEmpty()) {
            card?.visibility = View.VISIBLE
            text?.text = status
        } else {
            card?.visibility = View.GONE
        }
    }

    private fun showStatusPickerDialog(uid: String) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.setContentView(R.layout.dialog_status_picker)

        val grid = dialog.findViewById<GridLayout>(R.id.gridStatuses)
        val etCustomStatus = dialog.findViewById<EditText>(R.id.etCustomStatus)
        val btnSaveStatus = dialog.findViewById<Button>(R.id.btnSaveStatus)

        btnSaveStatus?.setOnClickListener {
            val text = etCustomStatus?.text.toString().trim()
            if (text.isNotEmpty()) {
                if (text.length <= 20) {
                    viewModel.updateStatus(text)
                    dialog.dismiss()
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
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { 
                    viewModel.updateStatus(emoji)
                    dialog.dismiss() 
                }
            }
            val params = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED)).apply {
                width = GridLayout.LayoutParams.WRAP_CONTENT
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(8, 8, 8, 8)
                setGravity(Gravity.CENTER)
            }
            grid?.addView(button, params)
        }

        dialog.findViewById<View>(R.id.btnClearStatus)?.setOnClickListener {
            viewModel.updateStatus(null)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val tvCurrent = view?.findViewById<TextView>(R.id.tvFridgeNote)

        val currentText = tvCurrent?.text.toString()
        if (currentText != "Оставьте записку..." && currentText != "Оставьте записку для любимого человека...") {
            etNote.setText(currentText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Записка на холодильнике")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newNote = etNote.text.toString().trim()
                viewModel.updateSharedNote(newNote)
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                btnConnect.isEnabled = false
                btnConnect.text = getString(R.string.Searching)
                
                viewModel.connectPartner(code, 
                    onSuccess = {
                        dialog.dismiss()
                        Toast.makeText(context, getString(R.string.connected), Toast.LENGTH_LONG).show()
                    },
                    onFailure = { error ->
                        btnConnect.isEnabled = true
                        btnConnect.text = getString(R.string.connect)
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(context, "Введите 8 цифр", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    @SuppressLint("StringFormatInvalid")
    private fun showPartnerOptions(partnerUid: String, partnerName: String) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.setContentView(R.layout.bottom_sheet_partner_options)
        dialog.findViewById<View>(R.id.btnDisconnect)?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                // Используем правильный строковый ресурс с параметром %s
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> 
                    viewModel.disconnectPartner(partnerUid)
                }
                .setNegativeButton("Нет", null)
                .show()
        }
        dialog.show()
    }

    // === Дерево и Счетчик (почти без изменений, но используют данные из Model) ===
    
    private fun updateTreeUI(points: Long) {
        val ivTree = view?.findViewById<ImageView>(R.id.ivTreeIcon)
        val tvLevel = view?.findViewById<TextView>(R.id.tvTreeLevel)
        val progress = view?.findViewById<ProgressBar>(R.id.progressTree)

        if (ivTree == null) return

        var maxPoints = 50
        var levelName = "Росток"
        var iconRes = R.drawable.ic_tree_stage_1

        if (points >= 1000) { maxPoints = 2000; levelName = "Древо Вечной Любви"; iconRes = R.drawable.ic_tree_stage_10 }
        else if (points >= 800) { maxPoints = 1000; levelName = "Волшебное Дерево"; iconRes = R.drawable.ic_tree_stage_9 }
        else if (points >= 650) { maxPoints = 800; levelName = "Изобильное Дерево"; iconRes = R.drawable.ic_tree_stage_8 }
        else if (points >= 500) { maxPoints = 650; levelName = "Дерево Любви"; iconRes = R.drawable.ic_tree_stage_7 }
        else if (points >= 350) { maxPoints = 500; levelName = "Цветущее Дерево"; iconRes = R.drawable.ic_tree_stage_6 }
        else if (points >= 200) { maxPoints = 350; levelName = "Взрослое Дерево"; iconRes = R.drawable.ic_tree_stage_5 }
        else if (points >= 100) { maxPoints = 200; levelName = "Крепкое Дерево"; iconRes = R.drawable.ic_tree_stage_4 }
        else if (points >= 50) { maxPoints = 100; levelName = "Молодое Дерево"; iconRes = R.drawable.ic_tree_stage_3 }
        else if (points >= 20) { maxPoints = 50; levelName = "Саженец"; iconRes = R.drawable.ic_tree_stage_2 }

        ivTree.setImageResource(iconRes)
        tvLevel?.text = "$levelName ($points очков)"
        progress?.max = maxPoints
        progress?.progress = points.toInt()
    }

    private fun showTreeDialog() {
        val points = viewModel.currentUser.value?.treePoints ?: 0
        AlertDialog.Builder(requireContext())
            .setTitle("🌳 Дерево Любви")
            .setMessage("Растите ваше дерево, заходя в приложение и добавляя воспоминания!\n\nТекущие очки: $points")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun calculateDays(startTimeInMillis: Long): Long {
        if (startTimeInMillis == 0L) return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = Calendar.getInstance().apply {
            timeInMillis = startTimeInMillis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diff = today.timeInMillis - start.timeInMillis
        return if (diff < 0) 0 else TimeUnit.MILLISECONDS.toDays(diff)
    }

    private fun updateDaysCounter(view: View, date: Long) {
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        val days = calculateDays(date)
        tvDaysCount.text = days.toString()
    }

    private fun updateDaysUI() {
        if (currentRelationshipTimestamp > 0) {
            val days = calculateDays(currentRelationshipTimestamp)
            view?.findViewById<TextView>(R.id.tvDaysCount)?.text = days.toString()
        }
    }

    private fun showRelationshipDatePicker(uid: String) {
        val dialog = BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.setContentView(R.layout.dialog_wheel_date_picker)
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        if (currentRelationshipTimestamp > 0) calendar.timeInMillis = currentRelationshipTimestamp
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.minValue = 1950; npYear.maxValue = currentYear
        npYear.value = calendar.get(Calendar.YEAR); npYear.wrapSelectorWheel = false
        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0; npMonth.maxValue = months.size - 1
        npMonth.displayedValues = months; npMonth.value = calendar.get(Calendar.MONTH)
        npDay.minValue = 1; npDay.maxValue = 31; npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        fun updateDaysInMonth() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, npYear.value); cal.set(Calendar.MONTH, npMonth.value); cal.set(Calendar.DAY_OF_MONTH, 1)
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        npMonth.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        npYear.setOnValueChangedListener { _, _, _ -> updateDaysInMonth() }
        updateDaysInMonth()

        btnConfirm.setOnClickListener {
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(Calendar.YEAR, npYear.value); selectedCalendar.set(Calendar.MONTH, npMonth.value); selectedCalendar.set(Calendar.DAY_OF_MONTH, npDay.value)
            viewModel.saveRelationshipDate(selectedCalendar.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun scheduleNextUpdate() {
        val now = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val delay = tomorrow.timeInMillis - now.timeInMillis + 1000
        updateHandler.removeCallbacks(updateRunnable)
        updateHandler.postDelayed(updateRunnable, delay)
    }

    // Сохранение для виджета (можно перенести в ViewModel, но оставил здесь как "UI side effect")
    private fun updateWidgetData(user: User, isMe: Boolean) {
        try {
            if (isMe) {
                prefs.edit()
                    .putString("my_name", user.name)
                    .putString("my_photo", user.photoUrl)
                    .putString("my_status", user.status)
                    .putString("shared_note", user.sharedNote)
                    .putString("partner_uid", user.partnerUid)
                    .putLong("relationship_date", user.relationshipDate)
                    .apply()
            } else {
                prefs.edit()
                    .putString("partner_name", user.name)
                    .putString("partner_photo", user.photoUrl)
                    .putString("partner_status", user.status)
                    .apply()
            }
            updateWidget()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateWidget() {
        try {
            val context = requireContext()
            val intent = Intent(context, CoupleWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CoupleWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // Log.e("Widget", "Error", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateHandler.removeCallbacks(updateRunnable)
        // ViewModel сам очистит свои слушатели в onCleared()
    }
}
