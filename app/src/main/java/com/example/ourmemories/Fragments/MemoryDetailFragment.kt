package com.example.ourmemories.Fragments

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
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
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.ourmemories.Factory.MemoryDetailFactory
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.MemoryDetailRepository
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MemoryDetailViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MemoryDetailFragment : Fragment(R.layout.fragment_memory_detail) {

    private val viewModel: MemoryDetailViewModel by viewModels {
        val application = requireActivity().application
        val repository = MemoryDetailRepository()
        MemoryDetailFactory(application, repository)
    }
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transitionSet = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeImageTransform())
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 500
        }

        sharedElementEnterTransition = transitionSet
        sharedElementReturnTransition = transitionSet
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val args = arguments ?: return
        val memoryId = args.getString("id") ?: return
        val imageUrl = args.getString("imageUrl") ?: ""
        val ivCover = view.findViewById<ImageView>(R.id.ivCover)
        ViewCompat.setTransitionName(ivCover, "memory_image_${memoryId}")

        postponeEnterTransition()
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .dontAnimate()
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }
                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        startPostponedEnterTransition()
                        return false
                    }
                })
                .into(ivCover)
        } else {
            startPostponedEnterTransition()
        }

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
        val btnDelete = view.findViewById<View>(R.id.btnDelete)
        val rvPhotos = view.findViewById<RecyclerView>(R.id.rvPhotos)
        val tvToolbarTitle = view.findViewById<TextView>(R.id.tvToolbarTitle)
        val appBar = view.findViewById<AppBarLayout>(R.id.appBar)

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

            if (percentage > 0.7f) {
                val alpha = (percentage - 0.7f) / 0.3f
                tvToolbarTitle.alpha = alpha
            } else {
                tvToolbarTitle.alpha = 0f
            }
        }

        rvPhotos.layoutManager = GridLayoutManager(context, 3)
        adapter = AlbumPhotosAdapter(images = imagesList, onClick = { position ->
            openFullScreenViewer(position)
        }, onLongClick = { url ->
            showPhotoOptionsDialog(url)
        })
        rvPhotos.adapter = adapter

        btnEdit.setOnClickListener {
            val currentTitle = viewModel.title.value ?: ""
            val currentDesc = viewModel.description.value ?: ""
            val currentDate = viewModel.timestamp.value ?: System.currentTimeMillis()
            showEditDialog(currentTitle, currentDesc, currentDate)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.delete_album_title))
                .setMessage(getString(R.string.delete_album_message))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.deleteAlbum()
                }.setNegativeButton(getString(R.string.cancel), null).show()
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
        val tvTitleToolbar = view.findViewById<TextView>(R.id.tvToolbarTitle)

        viewModel.title.observe(viewLifecycleOwner) {
            tvTitle.text = it
            tvTitleToolbar.text = it
        }
        viewModel.description.observe(viewLifecycleOwner) {
            tvDescription.text = it
            if (tvDescription.text.isEmpty()) {
                tvDescription.visibility = View.GONE
            } else {
                tvDescription.visibility = View.VISIBLE
            }
        }
        tvTitleToolbar.text = viewModel.title.value
        tvTitleToolbar.alpha = 1.0f
        viewModel.timestamp.observe(viewLifecycleOwner) { updateDateText(tvDate, it) }

        viewModel.coverUrl.observe(viewLifecycleOwner) { url ->
            if (url != arguments?.getString("imageUrl")) {
                GlideHelper.loadGalleryImage(ivCover, url)
            }
        }

        viewModel.isDeleted.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                parentFragmentManager.popBackStack()
            }
        }

        viewModel.images.observe(viewLifecycleOwner) { list ->
            imagesList.clear()
            imagesList.addAll(list)
            adapter.notifyDataSetChanged()
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    private fun showPhotoOptionsDialog(url: String) {
        val options = arrayOf(
            getString(R.string.action_make_cover), getString(R.string.action_delete_photo)
        )

        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.choose_action))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSetCoverDialog(url)
                    1 -> showDeletePhotoConfirmDialog(url)
                }
            }.show()
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
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.set_cover_title))
            .setMessage(getString(R.string.set_cover_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.setCoverImage(url)
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeletePhotoConfirmDialog(url: String) {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.delete_photo_title))
            .setMessage(getString(R.string.delete_photo_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deletePhoto(url)
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    /**
     * Диалог для редактирования
     */
    private fun showEditDialog(currentTitle: String, currentDesc: String, currentTimestamp: Long) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_memory, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etEditTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etEditDesc)
        val etDateView = dialogView.findViewById<TextView>(R.id.tvEditDate)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<View>(R.id.btnAdd)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        etTitle.setText(currentTitle)
        etDesc.setText(currentDesc)

        var newTimestamp = currentTimestamp
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        etDateView.text = sdf.format(Date(newTimestamp))

        etDateView.setOnClickListener {
            showWheelDatePicker(newTimestamp) { selectedTime ->
                newTimestamp = selectedTime
                etDateView.text = sdf.format(Date(newTimestamp))
            }
        }

        btnAdd?.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newDesc = etDesc.text.toString().trim()

            if (newTitle.isNotEmpty()) {
                viewModel.saveChanges(newTitle, newDesc, newTimestamp)
            } else {
                Toast.makeText(context, getString(R.string.error_empty_title), Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.dismiss()
        }
        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Диалог для выбора даты
     */
    private fun showWheelDatePicker(initialTimestamp: Long, onDateSelected: (Long) -> Unit) {
        val dialog = BottomSheetDialog(
            requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        )
        dialog.setContentView(R.layout.dialog_wheel_date_picker)

        val npDay = dialog.findViewById<NumberPicker>(R.id.npDay) ?: return
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth) ?: return
        val npYear = dialog.findViewById<NumberPicker>(R.id.npYear) ?: return
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmDate) ?: return

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
