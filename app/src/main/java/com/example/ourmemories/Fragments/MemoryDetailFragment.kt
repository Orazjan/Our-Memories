package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MemoryDetailFragment : Fragment(R.layout.fragment_memory_detail) {

    private val db = Firebase.firestore
    private val storage = Firebase.storage

    // Данные альбома
    private var memoryId: String = ""
    private var imagesList = mutableListOf<String>()
    private var currentTimestamp: Long = 0

    companion object {
        fun newInstance(
            id: String,
            title: String,
            description: String,
            imageUrl: String,
            timestamp: Long,
            uploaderUid: String
        ): MemoryDetailFragment {
            val args = Bundle()
            args.putString("id", id)
            args.putString("title", title)
            args.putString("description", description)
            args.putString("imageUrl", imageUrl)
            args.putLong("timestamp", timestamp)
            args.putString("uploaderUid", uploaderUid)
            val fragment = MemoryDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return
        memoryId = args.getString("id") ?: return
        val title = args.getString("title") ?: ""
        val description = args.getString("description") ?: ""
        currentTimestamp = args.getLong("timestamp")
        val coverUrl = args.getString("imageUrl") ?: ""

        // Инициализация Views
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val ivCover = view.findViewById<ImageView>(R.id.ivCover)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val btnEdit = view.findViewById<View>(R.id.btnEdit)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)

        // Настройка Toolbar
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Заполнение данными
        tvTitle.text = title
        tvDescription.text = description
        updateDateText(tvDate, currentTimestamp)
        GlideHelper.loadGalleryImage(ivCover, coverUrl)

        // Настройка сетки фото (3 колонки)
        rvPhotos.layoutManager = GridLayoutManager(context, 3)
        val adapter = AlbumPhotosAdapter(images = imagesList, onClick = { position ->
            // Открываем полноэкранный просмотр
            openFullScreenViewer(position)
        }, onLongClick = { url ->
            // Предлагаем сделать обложкой
            showSetCoverDialog(url, ivCover)
        })
        rvPhotos.adapter = adapter

        // Загрузка списка фото из базы
        loadImages(coverUrl, adapter)

        // Редактирование
        btnEdit.setOnClickListener {
            showEditDialog(tvTitle, tvDescription, tvDate)
        }
    }

    private fun loadImages(coverUrl: String, adapter: AlbumPhotosAdapter) {
        db.collection("memories").document(memoryId).get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                // Если поле images есть - берем его, если нет - берем imageUrl как одно фото
                val list = document.get("images") as? List<String>
                    ?: listOf(coverUrl).filter { it.isNotEmpty() }

                imagesList.clear()
                imagesList.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun openFullScreenViewer(position: Int) {
        // Передаем список URL и позицию в новый фрагмент просмотра
        val viewerFragment = PhotoViewerFragment.newInstance(ArrayList(imagesList), position)

        parentFragmentManager.beginTransaction().setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        ).add(R.id.fragment_container, viewerFragment) // Add поверх текущего
            .addToBackStack(null).commit()
    }

    private fun showEditDialog(tvTitle: TextView, tvDesc: TextView, tvDate: TextView) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_memory, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etEditTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etEditDesc)
        val etDateView = dialogView.findViewById<TextView>(R.id.tvEditDate)

        etTitle.setText(tvTitle.text)
        etDesc.setText(tvDesc.text)

        // Временная метка для диалога
        var newTimestamp = currentTimestamp
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        etDateView.text = sdf.format(Date(newTimestamp))

        // Используем кастомный пикер даты
        etDateView.setOnClickListener {
            showWheelDatePicker(newTimestamp) { selectedTime ->
                newTimestamp = selectedTime
                etDateView.text = sdf.format(Date(newTimestamp))
            }
        }

        AlertDialog.Builder(requireContext()).setTitle("Редактировать альбом")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDesc = etDesc.text.toString().trim()

                if (newTitle.isNotEmpty()) {
                    saveChanges(newTitle, newDesc, newTimestamp)
                    tvTitle.text = newTitle
                    tvDesc.text = newDesc
                    currentTimestamp = newTimestamp
                    updateDateText(tvDate, currentTimestamp)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Показывает диалоговое окно для выбора даты.
     */
    private fun showWheelDatePicker(initialTimestamp: Long, onDateSelected: (Long) -> Unit) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay)!!
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)!!
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear)!!
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate)!!

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = initialTimestamp
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        npYear.minValue = 1980
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
            val selectedCal = Calendar.getInstance()
            selectedCal.set(Calendar.YEAR, npYear.value)
            selectedCal.set(Calendar.MONTH, npMonth.value)
            selectedCal.set(Calendar.DAY_OF_MONTH, npDay.value)

            onDateSelected(selectedCal.timeInMillis)
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Сохранение изменений в базе данных.
     */
    private fun saveChanges(title: String, desc: String, timestamp: Long) {
        db.collection("memories").document(memoryId).update(
            mapOf(
                "title" to title, "description" to desc, "timestamp" to timestamp
            )
        ).addOnSuccessListener {
            Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Предлагаем сделать выбранное фото обложкой.
     */
    private fun showSetCoverDialog(url: String, ivCover: ImageView) {
        AlertDialog.Builder(requireContext()).setTitle("Сделать обложкой?")
            .setMessage("Это фото будет отображаться в ленте.").setPositiveButton("Да") { _, _ ->
                db.collection("memories").document(memoryId).update("imageUrl", url)
                    .addOnSuccessListener {
                        GlideHelper.loadGalleryImage(ivCover, url)
                        Toast.makeText(context, "Обложка обновлена", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Обновление текста даты на экране.
     */
    private fun updateDateText(textView: TextView, timestamp: Long) {
        if (timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            textView.text = sdf.format(Date(timestamp)).uppercase()
        }
    }

    /**
     * Адаптер для сетки фото в фрагменте просмотра альбома.
     */
    class AlbumPhotosAdapter(
        private val images: List<String>,
        private val onClick: (Int) -> Unit,
        private val onLongClick: (String) -> Unit
    ) : RecyclerView.Adapter<AlbumPhotosAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // Используем стандартный item_memory, так как он квадратный и подходит для сетки
            val imageView: ImageView = view.findViewById(R.id.ivMemory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = images[position]
            GlideHelper.loadGalleryImage(holder.imageView, url)

            holder.itemView.setOnClickListener { onClick(position) }
            holder.itemView.setOnLongClickListener {
                onLongClick(url)
                true
            }
        }

        override fun getItemCount() = images.size
    }
}