package com.example.ourmemories.Fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
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
import com.example.ourmemories.Factory.MainViewModelFactory
import com.example.ourmemories.Models.TreeInfo
import com.example.ourmemories.Models.User
import com.example.ourmemories.Models.Zodiac
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.MainRepository
import com.example.ourmemories.Utils.AnimationHelper
import com.example.ourmemories.Utils.Constants
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MainViewModel
import com.example.ourmemories.Widget.CoupleWidget
import com.example.ourmemories.databinding.MainFragmentBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
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

    private var _binding: MainFragmentBinding? = null
    private val binding get() = _binding!!

    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = MainRepository()
        prefs = requireContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val factory = MainViewModelFactory(requireActivity().application, repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.updateLastActive()

        setupUI()
        observeViewModel(view)
        scheduleNextUpdate()
    }

    override fun onResume() {
        super.onResume()
        try {
            val context = requireContext()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(
                    context, CoupleWidget::class.java
                )
            )
            val hasWidget = ids.isNotEmpty()

            viewModel.updateWidgetStatus(hasWidget)
            viewModel.updateLastActive()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI() {


        binding.btnAction.setOnClickListener {
            if (viewModel.currentUser.value?.partnerUid != null) {
                try {
                    pickWidgetImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: Exception) {
                    Toast.makeText(context, getString(R.string.error_gallery), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, getString(R.string.add_partner_first), Toast.LENGTH_SHORT).show()
            }
        }
        val locationBuffer = IntArray(2)
        binding.tvHeartIcon.setOnClickListener { view ->
            view.getLocationOnScreen(locationBuffer)
            val heartX = locationBuffer[0]
            val heartY = locationBuffer[1]

            binding.konfettiView.getLocationOnScreen(locationBuffer)
            val konfettiX = locationBuffer[0]
            val konfettiY = locationBuffer[1]

            val finalX = (heartX - konfettiX) + view.width / 2f
            val finalY = (heartY - konfettiY) + view.height / 2f

            playKonfetti(finalX, finalY)
        }


        binding.ivTreeIcon.setOnClickListener { _ ->
            AnimationHelper.animateJelly(binding.ivTreeIcon)
        }
        AnimationHelper.animateJelly(binding.ivTreeIcon)
        binding.cardFridge.setOnClickListener { showEditNoteDialog() }
        binding.cardTree.setOnClickListener { showTreeDialog() }

        binding.tvHeartIcon.startAnimation(
            AnimationUtils.loadAnimation(
                context, R.anim.heart_beat
            )
        )

        binding.tvDaysCount.setOnClickListener {
            viewModel.currentUser.value?.let { showRelationshipDatePicker() }
        }

        val statusClickListener = View.OnClickListener { showStatusPickerDialog() }
        binding.ivMyAvatar.setOnClickListener(statusClickListener)
        binding.cardMyStatus.setOnClickListener(statusClickListener)

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.startListening()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private val pickWidgetImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        try {
            if (uri != null) {
                viewModel.sendWidgetPhoto(uri)
            }
        } catch (e: Exception) {
            e.stackTraceToString()
        }

    }

    private fun playKonfetti(x: Float, y: Float) {
        val party = Party(
            speed = 10f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(100),
            position = Position.Absolute(x, y)
        )
        binding.konfettiView.start(party)
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

        viewModel.isWidgetLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.btnAction.text = getString(R.string.sending_photo)
                binding.btnAction.isEnabled = false
                binding.btnAction.alpha = 0.7f
            } else {
                binding.btnAction.text = getString(R.string.send_photo_to_widget)
                binding.btnAction.isEnabled = true
                binding.btnAction.alpha = 1.0f
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
            if (partner == null) {
                updateDaysCounter(view, 0)
            }
            if (partner != null) {
                updateWidgetData(partner, false)
            } else {

                updateDaysCounter(view, 0)

                prefs.edit().putString("partner_name", null).putString("partner_photo", null)
                    .putString("partner_status", null).apply()

                updateWidget()
            }
            partner?.let { updateWidgetData(it, false) }
        }
    }

    /**
     * Обновление интерфейса пользователя
     */
    private fun updateMyUI(view: View, user: User) {
        binding.tvMyName.text = user.name
        GlideHelper.loadAvatar(binding.ivMyAvatar, user.photoUrl, "MY_AVATAR")

        updateStatusUI(
            binding.cardMyStatus, binding.tvMyStatus,
            user.status
        )

        val treeInfo = TreeInfo.getTreeInfo(user.treePoints)
        updateTreeUI(view, treeInfo)

        binding.tvFridgeNote.text =
            user.sharedNote?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.leave_note_lovely_person)

        currentRelationshipTimestamp = user.relationshipDate
        updateDaysCounter(view, user.relationshipDate)

        binding.tvDaysCount.isEnabled = user.partnerUid != null
        binding.tvDaysCount.alpha = if (user.partnerUid != null) 1.0f else 0.5f
    }

    /**
     * Обновление интерфейса партнёра
     */
    private fun updatePartnerUI(view: View, partner: User?) {
        if (partner != null) {
            binding.tvPartnerName.text = partner.name

            val lastActiveDate = partner.lastActive
            val date = if (lastActiveDate > 0) Date(lastActiveDate) else null

            val partnerDr = partner.birthDate
            val treepoints = partner.treePoints
            GlideHelper.loadAvatar(binding.ivPartnerAvatar, partner.photoUrl, "PARTNER_AVATAR")
            updateStatusUI(
                binding.cardPartnerStatus, view.findViewById(R.id.tvPartnerStatus), partner.status
            )
            binding.layoutPartner.setOnClickListener {
                showPartnerOptions(
                    partner.uid, partner.name, partner.photoUrl, partnerDr, treepoints, date
                )
            }
        } else {
            binding.
            tvPartnerName.text = getString(R.string.invite)
            binding.ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            binding.ivPartnerAvatar.setPadding(20, 20, 20, 20)
            binding.cardPartnerStatus.visibility = View.GONE
            binding.layoutPartner.setOnClickListener { showInvitePartnerDialog() }
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
        val zodiac = Zodiac.getZodiacSign(partnerDr)

        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)

        dialog.findViewById<TextView>(R.id.userName)?.text = partnerName
        dialog.findViewById<TextView>(R.id.drPartner)?.text = partnerDr
        dialog.findViewById<TextView>(R.id.tvtreepoints)?.text = points.toString()
        dialog.findViewById<TextView>(R.id.zodiac)?.text = zodiac
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
    private fun showTreeDialog() {
        val points = viewModel.currentUser.value?.treePoints ?: 0L
        val treeInfo = TreeInfo.getTreeInfo(points)

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

    /**
     * Обновление счетчика дней
     */
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

    /**
     * Обновление виджета
     */
    private fun updateWidget() {
        CoupleWidget.sendRefreshBroadcast(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateHandler.removeCallbacks(updateRunnable)
        _binding = null
    }
}
