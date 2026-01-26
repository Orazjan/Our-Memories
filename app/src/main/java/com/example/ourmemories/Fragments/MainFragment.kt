package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Models.TreeInfo
import com.example.ourmemories.Models.User
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MainViewModel
import com.example.ourmemories.Widget.CoupleWidget
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("AppCache", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        viewModel.updateLastActive()

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
        view.findViewById<View>(R.id.btnAction)?.setOnClickListener {
            if (viewModel.currentUser.value?.partnerUid != null) {
                try {
                    pickWidgetImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка запуска пикера", e)
                    Toast.makeText(context, getString(R.string.error_gallery), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, getString(R.string.add_partner_first), Toast.LENGTH_SHORT).show()
            }
        }

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

    val TAG = "MainFragment"
    private val pickWidgetImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d(TAG, "Фото выбрано: $uri")
            viewModel.sendWidgetPhoto(uri)
        } else {
            Log.d(TAG, "Выбор фото отменен")
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

        val btnSendWidget =
            view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnAction)

        viewModel.isWidgetLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                btnSendWidget.text = getString(R.string.sending_photo)
                btnSendWidget.isEnabled = false
                btnSendWidget.alpha = 0.7f
            } else {
                btnSendWidget.text = getString(R.string.send_photo_to_widget)
                btnSendWidget.isEnabled = true
                btnSendWidget.alpha = 1.0f
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        viewModel.partnerUser.observe(viewLifecycleOwner) { partner ->
            updatePartnerUI(view, partner)
            partner?.let { updateWidgetData(it, false) }
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

        val treeInfo = viewModel.getTreeInfo(user.treePoints)
        updateTreeUI(view, treeInfo)

        view.findViewById<TextView>(R.id.tvFridgeNote)?.text =
            user.sharedNote?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.leave_note_lovely_person)

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

            val lastActiveDate = partner.lastActive
            val date = if (lastActiveDate > 0) Date(lastActiveDate) else null

            val partnerDr = partner.birthDate
            val treepoints = partner.treePoints
            GlideHelper.loadAvatar(ivPartnerAvatar, partner.photoUrl, "PARTNER_AVATAR")
            updateStatusUI(
                cardPartnerStatus, view.findViewById(R.id.tvPartnerStatus), partner.status
            )
            layoutPartner.setOnClickListener {
                showPartnerOptions(
                    partner.uid, partner.name, partner.photoUrl, partnerDr, treepoints, date
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

        btnSaveStatus?.setOnClickListener {
            val text = etCustomStatus?.text.toString().trim()
            if (text.isNotEmpty()) {
                if (text.length <= 20) {
                    viewModel.updateStatus(text)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, getString(R.string.error_max_chars), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        val statuses = viewModel.getStatuses()

        statuses.forEach { emoji ->
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

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnAdd).setOnClickListener {
            val newNote = etNote.text.toString().trim()
            viewModel.updateSharedNote(newNote)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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
    @SuppressLint("StringFormatInvalid")
    private fun showPartnerOptions(
        partnerUid: String,
        partnerName: String,
        partnerPhoto: String?,
        partnerDr: String?, points: Long, date: Date?
    ) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)

        dialog.findViewById<TextView>(R.id.userName)?.text = partnerName
        dialog.findViewById<TextView>(R.id.drPartner)?.text = partnerDr
        dialog.findViewById<TextView>(R.id.tvtreepoints)?.text = points.toString()
        dialog.findViewById<TextView>(R.id.zodiac)?.text = viewModel.getZodiacSign(partnerDr)
        dialog.findViewById<TextView>(R.id.lastActive)?.text = if (date != null) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date)
        } else {
            getString(R.string.unknown)
        }

        val ivAvatar = dialog.findViewById<ImageView>(R.id.userPhoto)
        if (ivAvatar != null) {
            GlideHelper.loadAvatar(ivAvatar, partnerPhoto, "PARTNER_OPTIONS")
        }

        dialog.findViewById<View>(R.id.btnHello)?.setOnClickListener {
            dialog.dismiss()
            viewModel.sendHello(partnerUid)
        }

        dialog.findViewById<View>(R.id.btnDisconnect)?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    viewModel.disconnectPartner(partnerUid)
                }.setNegativeButton(getString(R.string.no), null).show()
        }
        dialog.show()
    }

    /**
     * Обновление дерева любви
     */
    private fun updateTreeUI(view: View, treeInfo: TreeInfo) {
        val ivTree = view.findViewById<ImageView>(R.id.ivTreeIcon)

        ivTree.setImageResource(treeInfo.iconRes)
    }

    /**
     * Показ дерева любви
     */
    @SuppressLint("StringFormatInvalid")
    private fun showTreeDialog() {
        val points = viewModel.currentUser.value?.treePoints ?: 0L
        val treeInfo = viewModel.getTreeInfo(points)

        val dialogView = layoutInflater.inflate(R.layout.dialog_tree_info, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<ImageView>(R.id.ivTreeLarge).setImageResource(treeInfo.iconRes)

        dialogView.findViewById<TextView>(R.id.tvLevelName).text =
            getString(treeInfo.levelNameResId)

        val progressBar = dialogView.findViewById<ProgressBar>(R.id.pbLevelProgress)
        progressBar.max = treeInfo.maxPoints.toInt()
        progressBar.progress = treeInfo.currentPoints.toInt()

        val pointsText =
            getString(R.string.points_format, treeInfo.currentPoints, treeInfo.maxPoints)
        dialogView.findViewById<TextView>(R.id.tvPointsInfo).text = pointsText

        dialogView.findViewById<View>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay) ?: return
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth) ?: return
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear) ?: return

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
