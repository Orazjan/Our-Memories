package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.ourmemories.R
import com.github.chrisbanes.photoview.PhotoView

class PhotoViewerFragment : Fragment(R.layout.fragment_photo_viewer) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val images = arguments?.getStringArrayList("images") ?: return
        val startPos = arguments?.getInt("pos") ?: 0

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val btnClose = view.findViewById<View>(R.id.btnClose)
        val tvCounter = view.findViewById<TextView>(R.id.tvCounter)

        // Настройка ViewPager
        viewPager.adapter = FullScreenAdapter(images)
        viewPager.setCurrentItem(startPos, false)

        // Счетчик
        tvCounter.text = "${startPos + 1} / ${images.size}"
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvCounter.text = "${position + 1} / ${images.size}"
            }
        })

        btnClose.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    // Адаптер для полноэкранного просмотра
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