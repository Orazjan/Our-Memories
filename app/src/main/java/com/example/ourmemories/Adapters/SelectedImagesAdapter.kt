package com.example.ourmemories.Adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ourmemories.R

class SelectedImagesAdapter(
    private val images: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit, private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder>() {

    var selectedCoverPosition = 0

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        val btnRemove: View = itemView.findViewById(R.id.btnRemove)

        val tvCoverLabel: TextView = itemView.findViewById(R.id.tvCoverLabel)
        val viewSelectionBorder: View = itemView.findViewById(R.id.viewSelectionBorder)

        init {
            btnRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(position)
                }
            }

            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val oldPosition = selectedCoverPosition
                    selectedCoverPosition = position

                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedCoverPosition)

                    onImageClick(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_selected_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = images[position]
        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .into(holder.ivImage)

        if (position == selectedCoverPosition) {
            holder.tvCoverLabel.visibility = View.VISIBLE
            holder.viewSelectionBorder.visibility = View.VISIBLE
        } else {
            holder.tvCoverLabel.visibility = View.GONE
            holder.viewSelectionBorder.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = images.size

    /**
     * Обновляет список изображений и перерисовывает RecyclerView.
     * Используется во Fragment при наблюдении за ViewModel.
     */
    fun updateList(newImages: List<Uri>) {
        images.clear()
        images.addAll(newImages)

        if (selectedCoverPosition >= images.size && images.isNotEmpty()) {
            selectedCoverPosition = 0
        } else if (images.isEmpty()) {
            selectedCoverPosition = 0
        }

        notifyDataSetChanged()
    }
}
