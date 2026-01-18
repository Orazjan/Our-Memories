package com.example.ourmemories.Fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

/**
 * Главный экран приложения.
 *
 * Отображает:
 * - Счётчик дней вместе.
 * - Статусы пользователей.
 * - "Дерево любви" (система прогресса).
 * - Общую заметку ("Холодильник").
 */
class MainFragment : Fragment(R.layout.main_fragment) {

    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences
    private val updateHandler = Handler(Looper.getMainLooper())
    private var currentRelationshipTimestamp: Long = 0

    private val availableStatuses =
        listOf("😴", "💼", "❤️", "🏠", "🎮", "🍔", "☕", "🎉", "💪", "🎧", "🚗", "📚")

    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
        scheduleNextUpdate()
    }

    /**
     * Настройка пользовательского интерфейса.
     */
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

        tvHeart?.startAnimation(AnimationUtils.loadAnimation(context, R.anim.heart_beat))

        tvDaysCount.setOnClickListener {
            viewModel.currentUser.value?.let { showRelationshipDatePicker() }
        }

        val statusClickListener = View.OnClickListener { showStatusPickerDialog() }
        ivMyAvatar.setOnClickListener(statusClickListener)
        cardMyStatus.setOnClickListener(statusClickListener)

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.startListening()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            user?.let {
                updateMyUI(view, it)
                updateWidgetData(it, true)
            }
        }

        viewModel.partnerUser.observe(viewLifecycleOwner) { partner ->
            updatePartnerUI(view, partner)
            partner?.let { updateWidgetData(it, false) }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Обновление интерфейса пользователя
     */
    private fun updateMyUI(view: View, user: User) {
        view.findViewById<TextView>(R.id.tvMyName).text = user.name
        GlideHelper.loadAvatar(view.findViewById(R.id.ivMyAvatar), user.photoUrl, "MY_AVATAR")

        updateStatusUI(
            view.findViewById(R.id.cardMyStatus), view.findViewById(R.id.tvMyStatus),
            user.status
        )
        updateTreeUI(user.treePoints)

        view.findViewById<TextView>(R.id.tvFridgeNote)?.text =
            user.sharedNote?.takeIf { it.isNotEmpty() }
                ?: "Оставьте записку для любимого человека..."

        currentRelationshipTimestamp = user.relationshipDate
        updateDaysCounter(view, user.relationshipDate)

        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        tvDaysCount.isEnabled = user.partnerUid != null
        tvDaysCount.alpha = if (user.partnerUid != null) 1.0f else 0.5f
    }

    /**
     * Обновление интерфейса партнёра
     */
    private fun updatePartnerUI(view: View, partner: User?) {
        val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
        val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
        val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)
        val cardPartnerStatus = view.findViewById<View>(R.id.cardPartnerStatus)

        if (partner != null) {
            tvPartnerName.text = partner.name
            val partnerDr = partner.birthDate
            val treepoints = partner.treePoints
            GlideHelper.loadAvatar(ivPartnerAvatar, partner.photoUrl, "PARTNER_AVATAR")
            updateStatusUI(
                cardPartnerStatus, view.findViewById(R.id.tvPartnerStatus), partner.status
            )
            layoutPartner.setOnClickListener {
                showPartnerOptions(
                    partner.uid, partner.name, partner.photoUrl, partnerDr, treepoints
                )
            }
        } else {
            tvPartnerName.text = getString(R.string.invite)
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)
            cardPartnerStatus.visibility = View.GONE
            layoutPartner.setOnClickListener { showInvitePartnerDialog() }
        }
    }

    /**
     * Обновление статуса
     */
    private fun updateStatusUI(card: View?, text: TextView?, status: String?) {
        val hasStatus = !status.isNullOrEmpty()
        card?.visibility = if (hasStatus) View.VISIBLE else View.GONE
        text?.text = status
    }

    /**
     * Показ диалога для выбора статуса
     */
    private fun showStatusPickerDialog() {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_status_picker)

        val grid = dialog.findViewById<GridLayout>(R.id.gridStatuses)
        val etCustomStatus = dialog.findViewById<EditText>(R.id.etCustomStatus)
        val btnSaveStatus = dialog.findViewById<Button>(R.id.btnSaveStatus)

        // Обработка кнопки ОК для текстового статуса
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

        // Кнопки смайликов
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
                    viewModel.updateStatus(emoji)
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
        dialog.show()
    }

    /**
     * Показ диалога для редактирования записки
     */
    private fun showEditNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)
        val currentNote = viewModel.currentUser.value?.sharedNote
        etNote.setText(currentNote)

        AlertDialog.Builder(requireContext())
            .setTitle("Записка на холодильнике")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                viewModel.updateSharedNote(
                    etNote.text.toString().trim()
                )
            }.setNegativeButton("Отмена", null).show()
    }

    /**
     * Показ диалога для приглашения партнёра
     */
    private fun showInvitePartnerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_invite_partner, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etPartnerCode)
        val btnConnect = dialogView.findViewById<Button>(R.id.btnConnect)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        
        btnConnect.setOnClickListener {
            val code = etCode.text.toString().trim()
            if (code.length == 8) {
                btnConnect.isEnabled = false
                viewModel.connectPartner(
                    code,
                    { dialog.dismiss() },
                    { btnConnect.isEnabled = true })
            }
        }
        dialog.show()
    }

    /**
     * Показ меню опций партнёра
     */
    private fun showPartnerOptions(
        partnerUid: String,
        partnerName: String,
        partnerPhoto: String?,
        partnerDr: String?,
        points: Long
    ) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)

        // Заполняем данные
        dialog.findViewById<TextView>(R.id.userName)?.text = partnerName
        dialog.findViewById<TextView>(R.id.drPartner)?.text = partnerDr
        dialog.findViewById<TextView>(R.id.tvtreepoints)?.text = points.toString()
        val ivAvatar = dialog.findViewById<ImageView>(R.id.userPhoto)
        if (ivAvatar != null) {
            GlideHelper.loadAvatar(ivAvatar, partnerPhoto, "PARTNER_OPTIONS")
        }

        dialog.findViewById<View>(R.id.btnDisconnect)?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    viewModel.disconnectPartner(partnerUid)
                }.setNegativeButton("Нет", null).show()
        }
        dialog.show()
    }

    /**
     * Обновление дерева любви
     */
    private fun updateTreeUI(points: Long) {
        val ivTree = view?.findViewById<ImageView>(R.id.ivTreeIcon) ?: return
        val tvLevel = view?.findViewById<TextView>(R.id.tvTreeLevel)
        val progress = view?.findViewById<ProgressBar>(R.id.progressTree)

        val (levelName, iconRes, maxPoints) = when {
            points >= 1000 -> Triple("Древо Вечной Любви", R.drawable.ic_tree_stage_10, 2000)
            points >= 800 -> Triple("Волшебное Дерево", R.drawable.ic_tree_stage_9, 1000)
            points >= 650 -> Triple("Изобильное Дерево", R.drawable.ic_tree_stage_8, 800)
            points >= 500 -> Triple("Древо Любви", R.drawable.ic_tree_stage_7, 650)
            points >= 350 -> Triple("Цветущее Дерево", R.drawable.ic_tree_stage_6, 500)
            points >= 200 -> Triple("Взрослое Дерево", R.drawable.ic_tree_stage_5, 350)
            points >= 100 -> Triple("Крепкое Дерево", R.drawable.ic_tree_stage_4, 200)
            points >= 50 -> Triple("Молодое Дерево", R.drawable.ic_tree_stage_3, 100)
            points >= 20 -> Triple("Саженец", R.drawable.ic_tree_stage_2, 50)
            else -> Triple("Росток", R.drawable.ic_tree_stage_1, 20)
        }

        ivTree.setImageResource(iconRes)
        tvLevel?.text = "$levelName ($points очков)"
        progress?.max = maxPoints
        progress?.progress = points.toInt()
    }

    /**
     * Показ дерева любви
     */
    private fun showTreeDialog() {
        val points = viewModel.currentUser.value?.treePoints ?: 0
        AlertDialog.Builder(requireContext())
            .setTitle("🌳 Дерево Любви")
            .setMessage("Растите ваше дерево, заходя в приложение и добавляя воспоминания!\n\nТекущие очки: $points")
            .setPositiveButton("Понятно", null).show()
    }

    private fun updateDaysCounter(view: View, date: Long) {
        view.findViewById<TextView>(R.id.tvDaysCount).text = calculateDays(date).toString()
    }

    /**
     * Обновление счетчика дней
     */
    private fun updateDaysUI() {
        view?.findViewById<TextView>(R.id.tvDaysCount)?.text =
            calculateDays(currentRelationshipTimestamp).toString()
    }

    /**
     * Подсчет дней
     */
    private fun calculateDays(timestamp: Long): Long {
        if (timestamp == 0L) return 0
        val diff = System.currentTimeMillis() - timestamp
        return TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
    }

    /**
     * Диалог для выбора даты
     */
    private fun showRelationshipDatePicker() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_wheel_date_picker)
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!

        val calendar = Calendar.getInstance()
        if (currentRelationshipTimestamp > 0) calendar.timeInMillis = currentRelationshipTimestamp

        npYear.minValue = 1950
        npYear.maxValue = Calendar.getInstance().get(Calendar.YEAR)
        npYear.value = calendar.get(Calendar.YEAR)
        
        val months = DateFormatSymbols(Locale.getDefault()).shortMonths
        npMonth.minValue = 0
        npMonth.maxValue = 11
        npMonth.displayedValues = months
        npMonth.value = calendar.get(Calendar.MONTH)

        npDay.minValue = 1
        npDay.maxValue = 31
        npDay.value = calendar.get(Calendar.DAY_OF_MONTH)

        dialog.findViewById<Button>(R.id.btnConfirmDate)?.setOnClickListener {
            val selected = Calendar.getInstance()
            selected.set(npYear.value, npMonth.value, npDay.value)
            viewModel.saveRelationshipDate(selected.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Запланировать обновление через сутки
     */
    private fun scheduleNextUpdate() {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        }
        updateHandler.postDelayed(
            updateRunnable, tomorrow.timeInMillis - System.currentTimeMillis() + 1000
        )
    }

    /**
     * Обновление данных в виджете
     */
    private fun updateWidgetData(user: User, isMe: Boolean) {
        val editor = prefs.edit()
        if (isMe) {
            editor.putString("my_name", user.name).putString("my_photo", user.photoUrl)
                .putLong("relationship_date", user.relationshipDate).apply()
        } else {
            editor.putString("partner_name", user.name).putString("partner_photo", user.photoUrl)
                .apply()
        }
        context?.let { CoupleWidget.sendRefreshBroadcast(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateHandler.removeCallbacks(updateRunnable)
    }
}
