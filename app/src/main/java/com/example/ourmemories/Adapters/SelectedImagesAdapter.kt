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
    private val onRemoveClick: (Int) -> Unit,
    private val onImageClick: (Int) -> Unit // Callback для установки обложки
) : RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder>() {

    // Индекс текущей обложки (по умолчанию 0 - первое фото)
    var selectedCoverPosition = 0

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        val btnRemove: View = itemView.findViewById(R.id.btnRemove)

        // Элементы для индикации обложки (из XML)
        val tvCoverLabel: TextView = itemView.findViewById(R.id.tvCoverLabel)
        val viewSelectionBorder: View = itemView.findViewById(R.id.viewSelectionBorder)

        init {
            // Удаление фото
            btnRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(position)
                }
            }

            // Выбор фото как обложки
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Обновляем UI: снимаем выделение со старого, ставим на новое
                    val oldPosition = selectedCoverPosition
                    selectedCoverPosition = position

                    // Обновляем только два элемента для производительности
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

        // Загружаем фото (локальный URI)
        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .into(holder.ivImage)

        // Логика отображения рамки и метки "Обложка"
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

        // Если список уменьшился и выбранный индекс вышел за пределы, сбрасываем на 0
        if (selectedCoverPosition >= images.size && images.isNotEmpty()) {
            selectedCoverPosition = 0
        } else if (images.isEmpty()) {
            selectedCoverPosition = 0
        }

        notifyDataSetChanged()
    }
}
