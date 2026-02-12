package com.example.ourmemories.ui.photoviewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.ourmemories.R
import com.example.ourmemories.databinding.FragmentPhotoViewerBinding
import com.github.chrisbanes.photoview.PhotoView

class PhotoViewerFragment : Fragment() {

    private var _binding: FragmentPhotoViewerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PhotoViewerViewModel

    companion object {
        fun newInstance(images: ArrayList<String>, startPosition: Int): PhotoViewerFragment {
            val args = Bundle()
            args.putStringArrayList("images", images)
            args.putInt("pos", startPosition)
            val fragment = PhotoViewerFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PhotoViewerViewModel::class.java]

        val argsImages = arguments?.getStringArrayList("images")
        val argsStartPos = arguments?.getInt("pos") ?: 0
        viewModel.initData(argsImages, argsStartPos)

        setupUI()
        observeViewModel(view)
    }

    private fun setupUI() {

        binding.btnClose.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.onPageChanged(position)
            }
        })
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)

        viewModel.images.observe(viewLifecycleOwner) { images ->
            if (images.isNotEmpty()) {
                viewPager.adapter = FullScreenAdapter(images)

                val startPos = viewModel.currentPosition.value ?: 0
                viewPager.setCurrentItem(startPos, false)
            }
        }

        viewModel.currentPosition.observe(viewLifecycleOwner) { pos ->
            val total = viewModel.images.value?.size ?: 0
            tvCounter.text = "${pos + 1} / $total"
        }
    }

    /**
     * Адаптер для полноэкранного просмотра
     */
    class FullScreenAdapter(private val images: List<String>) :
        RecyclerView.Adapter<FullScreenAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.ivFullPhoto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_fullscreen, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            Glide.with(holder.itemView.context).load(images[position]).into(holder.photoView)
        }

        override fun getItemCount() = images.size
    }
}