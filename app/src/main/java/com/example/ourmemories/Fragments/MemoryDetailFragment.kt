package com.example.ourmemories.Fragments

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.ChangeTransform
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.ourmemories.Factory.MemoryDetailFactory
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.MemoryDetailRepository
import com.example.ourmemories.Utils.Constants
import com.example.ourmemories.Utils.DatePickerHelper
import com.example.ourmemories.Utils.GlideHelper
import com.example.ourmemories.ViewModels.MemoryDetailViewModel
import com.example.ourmemories.databinding.FragmentMemoryDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MemoryDetailFragment : Fragment() {
    private var _binding: FragmentMemoryDetailBinding? = null
    private val binding get() = _binding!!


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transitionSet = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeImageTransform())
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 300
        }

        sharedElementEnterTransition = transitionSet
        sharedElementReturnTransition = transitionSet
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val args = arguments ?: return
        val memoryId = args.getString("id") ?: return
        val imageUrl = args.getString(Constants.ARG_IMAGE_URL) ?: ""
        val ivCover = view.findViewById<ImageView>(R.id.ivCover)
        ViewCompat.setTransitionName(ivCover, "memory_image_${memoryId}")

        postponeEnterTransition()
        startPostponedEnterTransitionWithTimeout()
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
            initialCover = args.getString(Constants.ARG_IMAGE_URL) ?: ""
        )

        setupUI()
        observeViewModel()
    }

    /**
     * Настройка пользовательского интерфейса
     */
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.appBar.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange
            val percentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()

            if (percentage > 0.7f) {
                val alpha = (percentage - 0.7f) / 0.3f
                binding.tvToolbarTitle.alpha = alpha
            } else {
                binding.tvToolbarTitle.alpha = 0f
            }
        }

        binding.rvPhotos.layoutManager = GridLayoutManager(context, 3)
        adapter = AlbumPhotosAdapter(images = imagesList, onClick = { position ->
            openFullScreenViewer(position)
        }, onLongClick = { url ->
            showPhotoOptionsDialog(url)
        })
        binding.rvPhotos.adapter = adapter

        binding.btnEdit.setOnClickListener {
            val currentTitle = viewModel.title.value ?: ""
            val currentDesc = viewModel.description.value ?: ""
            val currentDate = viewModel.timestamp.value ?: System.currentTimeMillis()
            showEditDialog(currentTitle, currentDesc, currentDate)
        }

        binding.btnDelete.setOnClickListener {
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
    private fun observeViewModel() {
        viewModel.title.observe(viewLifecycleOwner) {
            binding.tvTitle.text = it
            binding.tvToolbarTitle.text = it
        }
        viewModel.description.observe(viewLifecycleOwner) {
            binding.tvDescription.text = it
            if (binding.tvDescription.text.isEmpty()) {
                binding.tvDescription.visibility = View.GONE
            } else {
                binding.tvDescription.visibility = View.VISIBLE
            }
        }
        binding.tvToolbarTitle.text = viewModel.title.value
        binding.tvToolbarTitle.alpha = 1.0f
        viewModel.timestamp.observe(viewLifecycleOwner) { updateDateText(binding.tvDate, it) }

        viewModel.coverUrl.observe(viewLifecycleOwner) { url ->
            if (!url.isNullOrEmpty() && url != arguments?.getString(Constants.ARG_IMAGE_URL)) {
                GlideHelper.loadGalleryImage(binding.ivCover, url)
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

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        etDateView.text = sdf.format(Date(currentTimestamp))

        etDateView.setOnClickListener {
            DatePickerHelper.showDatePicker(requireContext()) { dateString, timestamp ->
                etDateView.text = dateString
            }
        }

        btnAdd?.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newDesc = etDesc.text.toString().trim()

            if (newTitle.isNotEmpty()) {
                viewModel.saveChanges(newTitle, newDesc, currentTimestamp)
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
     * Обновление даты
     */
    private fun updateDateText(textView: TextView, timestamp: Long) {
        if (timestamp > 0) {
            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            textView.text = sdf.format(Date(timestamp)).uppercase()
        }
    }

    private fun startPostponedEnterTransitionWithTimeout() {
        view?.postDelayed({
            startPostponedEnterTransition()
        }, 1000)
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
