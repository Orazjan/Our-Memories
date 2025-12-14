package com.example.ourmemories.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import java.text.SimpleDateFormat
import java.util.Locale

class TimelineAdapter(private val onClick: (Memory) -> Unit) :
    ListAdapter<Memory, TimelineAdapter.TimelineViewHolder>(MemoryDiffCallback()) {

    class MemoryDiffCallback : DiffUtil.ItemCallback<Memory>() {
        override fun areItemsTheSame(oldItem: Memory, newItem: Memory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Memory, newItem: Memory) = oldItem == newItem
    }

    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDay)
        val tvMonth: TextView = itemView.findViewById(R.id.tvMonth)
        val ivImage: ImageView = itemView.findViewById(R.id.ivTimelineImage)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTimelineTitle)
        val tvDesc: TextView = itemView.findViewById(R.id.tvTimelineDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val memory = getItem(position)

        holder.tvTitle.text = memory.title
        holder.tvDesc.text = memory.description

        // Форматирование даты
        val date = java.util.Date(memory.timestamp)
        val dayFormat = SimpleDateFormat("dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

        holder.tvDay.text = dayFormat.format(date)
        holder.tvMonth.text = monthFormat.format(date).uppercase()

        Glide.with(holder.itemView.context)
            .load(memory.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(holder.ivImage)

        holder.itemView.setOnClickListener { onClick(memory) }
    }
}