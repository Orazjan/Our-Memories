package com.example.ourmemories.Adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R

class WishlistAdapter(
    private val onCheckClick: (WishItem, Boolean) -> Unit,
    private val onLongClick: (WishItem) -> Unit
) : ListAdapter<WishItem, WishlistAdapter.WishViewHolder>(WishDiffCallback()) {

    inner class WishViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val cbComplete: CheckBox = itemView.findViewById(R.id.cbComplete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_wishlist, parent, false)
        return WishViewHolder(view)
    }

    override fun onBindViewHolder(holder: WishViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvTitle.text = item.title

        if (item.description.isNotEmpty()) {
            holder.tvDescription.text = item.description
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }

        // Сначала снимаем слушатель, чтобы изменение isChecked не вызвало колбек
        holder.cbComplete.setOnCheckedChangeListener(null)

        // Устанавливаем текущее состояние
        holder.cbComplete.isChecked = item.isCompleted

        // Применяем визуальное оформление (зачеркивание)
        updateStrikeThrough(holder.tvTitle, holder.tvDescription, item.isCompleted)

        // Используем setOnClickListener вместо setOnCheckedChangeListener для надежности
        holder.cbComplete.setOnClickListener {
            val isChecked = holder.cbComplete.isChecked
            // Мгновенно обновляем визуал
            updateStrikeThrough(holder.tvTitle, holder.tvDescription, isChecked)
            // Отправляем событие во фрагмент
            onCheckClick(item, isChecked)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private fun updateStrikeThrough(title: TextView, desc: TextView, isCompleted: Boolean) {
        if (isCompleted) {
            title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            title.alpha = 0.5f
            desc.alpha = 0.5f
        } else {
            title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            title.alpha = 1.0f
            desc.alpha = 1.0f
        }
    }

    class WishDiffCallback : DiffUtil.ItemCallback<WishItem>() {
        override fun areItemsTheSame(oldItem: WishItem, newItem: WishItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: WishItem, newItem: WishItem): Boolean {
            return oldItem == newItem
        }
    }
}