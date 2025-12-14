package com.example.ourmemories.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter // Используем ListAdapter вместо RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R

// Оптимизированный адаптер на базе ListAdapter
class MemoryAdapter(
    private val onClick: (Memory) -> Unit
) : ListAdapter<Memory, MemoryAdapter.MemoryViewHolder>(MemoryDiffCallback()) {

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.ivMemory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = getItem(position) // Получаем элемент через встроенный метод

        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.stat_notify_error)

        Glide.with(holder.itemView.context)
            .load(memory.imageUrl)
            .apply(requestOptions)
            .thumbnail(0.1f)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onClick(memory)
        }
    }

    // Класс для сравнения списков
    class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
        // Проверяем, тот ли это элемент (по ID)
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem.id == newItem.id
        }

        // Проверяем, изменилось ли содержимое (по hashcode data-класса)
        override fun areContentsTheSame(oldItem: Memory, newItem: Memory): Boolean {
            return oldItem == newItem
        }
    }
}