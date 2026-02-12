package com.example.ourmemories.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ourmemories.R

/**
 * Адаптер для списка выбранных фотографий при создании воспоминания.
 * Позволяет удалять фото и выбирать обложку.
 */
class SelectedImagesAdapter(
    var images: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit, private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.ViewHolder>() {

    private var coverUri: Uri? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivImage)
        val btnRemove: View = view.findViewById(R.id.btnRemove)
        val tvCoverLabel: TextView = view.findViewById(R.id.tvCoverLabel)
        val border: View = view.findViewById(R.id.viewSelectionBorder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_selected_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = images[position]

        Glide.with(holder.itemView.context).load(uri).centerCrop().into(holder.image)

        holder.btnRemove.setOnClickListener { onRemoveClick(position) }
        holder.itemView.setOnClickListener { onImageClick(position) }

        val isCover = uri == coverUri
        if (isCover) {
            holder.tvCoverLabel.visibility = View.VISIBLE
            holder.border.visibility = View.VISIBLE
        } else {
            holder.tvCoverLabel.visibility = View.GONE
            holder.border.visibility = View.GONE
        }
    }

    override fun getItemCount() = images.size

    /**
     * Устанавливает URI текущей обложки и обновляет список для перерисовки рамок.
     */
    fun setCover(uri: Uri?) {
        coverUri = uri
        notifyDataSetChanged()
    }
}