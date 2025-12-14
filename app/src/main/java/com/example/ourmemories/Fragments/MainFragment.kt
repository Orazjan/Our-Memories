package com.example.ourmemories.Fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.MemoryAdapter
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
    private var memoriesListener: ListenerRegistration? = null

    private var currentPartnerUid: String? = null
    private var currentRelationshipTimestamp: Long? = null
    private var currentUidsToLoad: List<String>? = null

    private lateinit var recentAdapter: MemoryAdapter

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable {
        updateDaysUI()
        scheduleNextUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
        val rvRecent = view.findViewById<RecyclerView>(R.id.rvRecentMemories)
        val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)
        val tvSeeAll = view.findViewById<TextView>(R.id.tvSeeAllMemories)

        // Анимация сердца
        val tvHeart = view.findViewById<TextView>(R.id.tvHeartIcon)
        if (tvHeart != null) {
            val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.heart_beat)
            tvHeart.startAnimation(pulseAnimation)
        }

        rvRecent.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // === ИСПРАВЛЕНИЕ ОШИБКИ ЗДЕСЬ ===
        // Явно указываем макет и onClick через именованные аргументы
        recentAdapter = MemoryAdapter(
            layoutResId = R.layout.item_memory_horizontal,
            onClick = { memory ->
                val detailFragment = MemoryDetailFragment.newInstance(
                    memory.id,
                    memory.title,
                    memory.description,
                    memory.imageUrl,
                    memory.timestamp,
                    memory.uploaderUid
                )

                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    .add(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit()
            }
        )
        rvRecent.adapter = recentAdapter

        checkMemoriesState(view, 0)

        tvDaysCount.isEnabled = false
        tvDaysCount.alpha = 0.5f

        tvDaysCount.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                showRelationshipDatePicker(user.uid)
            }
        }

        ivMyAvatar.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                showMyOptions(user.uid)
            }
        }

        tvSeeAll.setOnClickListener {
            try {
                val bottomNav =
                    requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
                bottomNav.selectedItemId = R.id.nav_gallery
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка навигации: ${e.message}")
            }
        }

        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления")
            currentUidsToLoad = null
            currentPartnerUid = null
            setupListeners(view)
        }

        setupListeners(view)
        scheduleNextUpdate()
    }

    // ... (Остальные методы без изменений) ...

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

    private fun checkMemoriesState(view: View, memoriesCount: Int) {
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmptyMemories)
        val rvRecent = view.findViewById<View>(R.id.rvRecentMemories)
        val tvSeeAll = view.findViewById<TextView>(R.id.tvSeeAllMemories)

        if (layoutEmpty != null && rvRecent != null) {
            if (memoriesCount == 0) {
                layoutEmpty.visibility = View.VISIBLE
                rvRecent.visibility = View.GONE
                tvSeeAll?.visibility = View.GONE
            } else {
                layoutEmpty.visibility = View.GONE
                rvRecent.visibility = View.VISIBLE
                tvSeeAll?.visibility = View.VISIBLE
            }
        }
    }

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
                Log.e(TAG, "Ошибка загрузки: ${e.message}")
                return@addSnapshotListener
            }

            if (isAdded && document != null && document.exists()) {
                val myName = document.getString("name") ?: "Я"
                val myPhotoUrl = document.getString("photoUrl")
                val tvMyName = view.findViewById<TextView>(R.id.tvMyName)
                val ivMyAvatar = view.findViewById<ImageView>(R.id.ivMyAvatar)

                tvMyName.text = myName
                GlideHelper.loadAvatar(ivMyAvatar, myPhotoUrl, "MY_AVATAR")

                val relationshipDate = document.getLong("relationshipDate")
                currentRelationshipTimestamp = relationshipDate
                updateDaysCounter(view, relationshipDate)

                val partnerUid = document.getString("partnerUid")

                val tvDaysCount = view.findViewById<TextView>(R.id.tvDaysCount)
                if (partnerUid != null) {
                    tvDaysCount.isEnabled = true
                    tvDaysCount.alpha = 1.0f
                } else {
                    tvDaysCount.isEnabled = false
                    tvDaysCount.alpha = 0.5f
                }

                val uidsToLoad = mutableListOf(myUid)
                if (partnerUid != null) {
                    uidsToLoad.add(partnerUid)
                }

                if (currentUidsToLoad != uidsToLoad) {
                    currentUidsToLoad = uidsToLoad
                    setupMemoriesListener(uidsToLoad, view)
                }

                handlePartnerState(view, myUid, partnerUid)
            }
        }
    }

    private fun setupMemoriesListener(uids: List<String>, view: View) {
        memoriesListener?.remove()

        memoriesListener = db.collection("memories")
            .whereIn("uploaderUid", uids)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshots, e ->
                if (!isAdded) return@addSnapshotListener
                if (e != null) return@addSnapshotListener

                if (snapshots != null) {
                    val newMemories = snapshots.map { doc ->
                        doc.toObject(Memory::class.java).copy(id = doc.id)
                    }
                    recentAdapter.submitList(newMemories)
                    checkMemoriesState(view, newMemories.size)
                }
            }
    }

    private fun handlePartnerState(view: View, myUid: String, partnerUid: String?) {
        val layoutPartner = view.findViewById<LinearLayout>(R.id.layoutPartner)
        val tvPartnerName = view.findViewById<TextView>(R.id.tvPartnerName)
        val ivPartnerAvatar = view.findViewById<ImageView>(R.id.ivPartnerAvatar)

        if (partnerUid != null) {
            layoutPartner.setOnClickListener {
                val name = tvPartnerName.text.toString()
                showPartnerOptions(partnerUid, name)
            }

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
                            tvPartnerName.text = pName
                            GlideHelper.loadAvatar(ivPartnerAvatar, pPhoto, "PARTNER_AVATAR")
                        }
                    }
            }
        } else {
            partnerListener?.remove()
            currentPartnerUid = null
            tvPartnerName.text = getString(R.string.invite)
            ivPartnerAvatar.setImageResource(android.R.drawable.ic_input_add)
            ivPartnerAvatar.setColorFilter(android.graphics.Color.GRAY)
            ivPartnerAvatar.setPadding(20, 20, 20, 20)
            layoutPartner.setOnClickListener { showInvitePartnerDialog(myUid) }
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

    private fun calculateDays(startTimeInMillis: Long): Long {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = Calendar.getInstance().apply {
            timeInMillis = startTimeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = today.timeInMillis - start.timeInMillis
        if (diff < 0) return 0
        return TimeUnit.MILLISECONDS.toDays(diff)
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
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
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
                btnConnect.isEnabled = false
                btnConnect.text = getString(R.string.Searching)
                connectPartner(myUid, code, dialog)
            } else {
                Toast.makeText(context, "Введите 8 цифр", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun connectPartner(myUid: String, code: String, dialog: AlertDialog) {
        val btnConnect = dialog.findViewById<Button>(R.id.btnConnect)
        db.collection("users").whereEqualTo("partnerCode", code).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, getString(R.string.Code_not_found), Toast.LENGTH_SHORT)
                        .show()
                    btnConnect?.isEnabled = true
                    btnConnect?.text = getString(R.string.connect)
                } else {
                    val partnerDoc = documents.documents[0]
                    val partnerUid = partnerDoc.id
                    if (partnerUid == myUid) {
                        Toast.makeText(
                            context,
                            getString(R.string.cant_add_yourself),
                            Toast.LENGTH_SHORT
                        ).show()
                        btnConnect?.isEnabled = true
                        btnConnect?.text = getString(R.string.connect)
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

    private fun showMyOptions(myUid: String) {
        val dialog = BottomSheetDialog(
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_my_options)

        dialog.findViewById<View>(R.id.btnCopyCode)?.setOnClickListener {
            db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
                val code = doc.getString("partnerCode") ?: "Нет кода"
                val clipboard =
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Partner Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, getString(R.string.code_copied, code), Toast.LENGTH_SHORT)
                    .show()
                dialog.dismiss()
            }
        }
        dialog.findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            dialog.dismiss()
            auth.signOut()
            val intent = Intent(requireActivity(), EnterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        dialog.show()
    }

    private fun showPartnerOptions(partnerUid: String, partnerName: String) {
        val dialog = BottomSheetDialog(
            requireContext(),
            com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.bottom_sheet_partner_options)

        val btnDisconnect = dialog.findViewById<View>(R.id.btnDisconnect)
        btnDisconnect?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.disconnect_partner_title))
                .setMessage(getString(R.string.disconnect_partner_message, partnerName))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> disconnectPartner(partnerUid) }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
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
            Toast.makeText(context, getString(R.string.partner_disconnected), Toast.LENGTH_SHORT)
                .show()
        }.addOnFailureListener {
            Toast.makeText(
                context,
                "${getString(R.string.error)}: ${it.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        myListener?.remove()
        partnerListener?.remove()
        memoriesListener?.remove()
        updateHandler.removeCallbacks(updateRunnable)
    }
}