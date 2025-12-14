package com.example.ourmemories.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper

class MemoryAdapter(
    @LayoutRes private val layoutResId: Int = R.layout.item_memory,
    private val onClick: (Memory) -> Unit,
    private val onLongClick: ((Memory) -> Unit)? = null // Новый параметр, по умолчанию null
) : ListAdapter<Memory, MemoryAdapter.MemoryViewHolder>(MemoryDiffCallback()) {

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivMemory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = getItem(position)
        GlideHelper.loadGalleryImage(holder.imageView, memory.imageUrl)

        holder.itemView.setOnClickListener { onClick(memory) }

        // Обработка долгого нажатия
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(memory)
            true // Возвращаем true, чтобы событие было поглощено
        }
    }

    class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Memory, newItem: Memory) = oldItem == newItem
    }
}