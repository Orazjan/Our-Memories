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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MemoryDetailViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MemoryDetailFragment : Fragment(R.layout.fragment_memory_detail) {

    private lateinit var viewModel: MemoryDetailViewModel
    
    // Адаптер и список для него
    private var imagesList = mutableListOf<String>()
    private lateinit var adapter: AlbumPhotosAdapter

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

        viewModel = ViewModelProvider(this)[MemoryDetailViewModel::class.java]

        // Получаем аргументы и инициализируем ViewModel
        val args = arguments ?: return
        val memoryId = args.getString("id") ?: return
        
        viewModel.init(
            id = memoryId,
            initialTitle = args.getString("title") ?: "",
            initialDesc = args.getString("description") ?: "",
            initialTimestamp = args.getLong("timestamp"),
            initialCover = args.getString("imageUrl") ?: ""
        )

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Настройка пользовательского интерфейса
     */
    private fun setupUI(view: View) {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        val btnEdit = view.findViewById<View>(R.id.btnEdit)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Настройка списка фото
        rvPhotos.layoutManager = GridLayoutManager(context, 3)
        adapter = AlbumPhotosAdapter(images = imagesList, onClick = { position ->
            openFullScreenViewer(position)
        }, onLongClick = { url ->
            showSetCoverDialog(url)
        })
        rvPhotos.adapter = adapter

        btnEdit.setOnClickListener {
            val currentTitle = viewModel.title.value ?: ""
            val currentDesc = viewModel.description.value ?: ""
            val currentDate = viewModel.timestamp.value ?: System.currentTimeMillis()
            showEditDialog(currentTitle, currentDesc, currentDate)
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel
     */
    private fun observeViewModel(view: View) {
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val ivCover = view.findViewById<ImageView>(R.id.ivCover)

        // Обновление текстов
        viewModel.title.observe(viewLifecycleOwner) { tvTitle.text = it }
        viewModel.description.observe(viewLifecycleOwner) { tvDescription.text = it }
        viewModel.timestamp.observe(viewLifecycleOwner) { updateDateText(tvDate, it) }
        
        // Обновление обложки
        viewModel.coverUrl.observe(viewLifecycleOwner) { url ->
            GlideHelper.loadGalleryImage(ivCover, url)
        }

        // Обновление списка фото
        viewModel.images.observe(viewLifecycleOwner) { list ->
            imagesList.clear()
            imagesList.addAll(list)
            adapter.notifyDataSetChanged()
        }

        // Сообщения
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Открытие полноэкранного просмотра
     */
    private fun openFullScreenViewer(position: Int) {
        val viewerFragment = PhotoViewerFragment.newInstance(ArrayList(imagesList), position)

        parentFragmentManager.beginTransaction().setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        ).add(R.id.fragment_container, viewerFragment)
            .addToBackStack(null).commit()
    }

    /**
     * Диалог для выбора обложки
     */
    private fun showSetCoverDialog(url: String) {
        AlertDialog.Builder(requireContext()).setTitle("Сделать обложкой?")
            .setMessage("Это фото будет отображаться в ленте.")
            .setPositiveButton("Да") { _, _ ->
                viewModel.setCoverImage(url)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Диалог для редактирования
     */
    private fun showEditDialog(currentTitle: String, currentDesc: String, currentTimestamp: Long) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_memory, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etEditTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etEditDesc)
        val etDateView = dialogView.findViewById<TextView>(R.id.tvEditDate)

        etTitle.setText(currentTitle)
        etDesc.setText(currentDesc)

        // Локальная переменная для диалога
        var newTimestamp = currentTimestamp
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        etDateView.text = sdf.format(Date(newTimestamp))

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
                    viewModel.saveChanges(newTitle, newDesc, newTimestamp)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Диалог для выбора даты
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
     * Обновление даты
     */
    private fun updateDateText(textView: TextView, timestamp: Long) {
        if (timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            textView.text = sdf.format(Date(timestamp)).uppercase()
        }
    }

    /**
     * Адаптер для сетки фото
     */
    class AlbumPhotosAdapter(
        private val images: List<String>,
        private val onClick: (Int) -> Unit,
        private val onLongClick: (String) -> Unit
    ) : RecyclerView.Adapter<AlbumPhotosAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.ivMemory)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
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
