package com.example.ourmemories.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.data.models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.utils.GlideHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Универсальный адаптер для списка воспоминаний.
 */
class MemoryAdapter(
    @LayoutRes private val layoutResId: Int = R.layout.item_memory,
    private val onClick: (Memory, ImageView) -> Unit,
    private val onLongClick: ((Memory) -> Unit)? = null
) : ListAdapter<Memory, MemoryAdapter.MemoryViewHolder>(MemoryDiffCallback()) {

    private val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    /**
     * Вьюхолдер для элемента списка
     */
    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView =
            itemView.findViewById(R.id.ivMemory) ?: itemView.findViewById(R.id.ivTimelineImage)

        val tvDay: TextView? = itemView.findViewById(R.id.tvDay)
        val tvMonth: TextView? = itemView.findViewById(R.id.tvMonth)
        val tvTitle: TextView? = itemView.findViewById(R.id.tvTimelineTitle)
        val tvDesc: TextView? = itemView.findViewById(R.id.tvTimelineDesc)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onClick(getItem(position), imageView)
                }
            }

            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick?.invoke(getItem(position))
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = getItem(position)

        GlideHelper.loadGalleryImage(holder.imageView, memory.imageUrl)


        ViewCompat.setTransitionName(holder.imageView, "memory_image_${memory.id}")

        if (holder.tvTitle != null) {
            holder.tvTitle.text = memory.title.ifEmpty { "Без названия" }
            holder.tvDesc?.text = memory.description.ifEmpty { "" }

            if (memory.timestamp > 0) {
                val date = Date(memory.timestamp)
                holder.tvDay?.text = dayFormat.format(date)
                holder.tvMonth?.text = monthFormat.format(date).uppercase()
            }
        }
    }

    /**
     * Класс для сравнения элементов списка
     */
    class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem == newItem
        }
    }
}