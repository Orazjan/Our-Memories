package com.example.ourmemories.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Универсальный адаптер для списка воспоминаний.
 * Поддерживает разные макеты (плитка, горизонтальная лента, список альбомов).
 */
class MemoryAdapter(
    @LayoutRes private val layoutResId: Int = R.layout.item_memory, // По умолчанию - плитка
    private val onClick: (Memory) -> Unit,
    private val onLongClick: ((Memory) -> Unit)? = null
) : ListAdapter<Memory, MemoryAdapter.MemoryViewHolder>(MemoryDiffCallback()) {

    // Форматтеры даты для режима "Альбом"
    private val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView =
            itemView.findViewById(R.id.ivMemory) ?: itemView.findViewById(R.id.ivTimelineImage)

        // Текстовые поля есть только в макете item_album, поэтому они nullable (?)
        val tvDay: TextView? = itemView.findViewById(R.id.tvDay)
        val tvMonth: TextView? = itemView.findViewById(R.id.tvMonth)
        val tvTitle: TextView? = itemView.findViewById(R.id.tvTimelineTitle)
        val tvDesc: TextView? = itemView.findViewById(R.id.tvTimelineDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = getItem(position)

        GlideHelper.loadGalleryImage(holder.imageView, memory.imageUrl)

        // Заполнение текстов (только если мы используем макет альбома)
        if (holder.tvTitle != null) {
            holder.tvTitle.text = if (memory.title.isNotEmpty()) memory.title else "Без названия"
            holder.tvDesc?.text = if (memory.description.isNotEmpty()) memory.description else ""

            // Форматирование даты (например: "26" "ОКТ")
            if (memory.timestamp > 0) {
                val date = Date(memory.timestamp)
                holder.tvDay?.text = dayFormat.format(date)
                holder.tvMonth?.text = monthFormat.format(date).uppercase()
            }
        }

        holder.itemView.setOnClickListener {
            onClick(memory)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(memory)
            true // true = событие обработано
        }
    }

    class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem == newItem
        }
    }
}