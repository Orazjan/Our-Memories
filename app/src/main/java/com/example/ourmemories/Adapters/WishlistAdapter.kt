package com.example.ourmemories.Adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R
import com.example.ourmemories.Utils.GlideHelper

class WishlistAdapter(
    private val onCheckClick: (WishItem, Boolean) -> Unit,
    private val onLongClick: (WishItem) -> Unit
) : ListAdapter<WishItem, WishlistAdapter.WishViewHolder>(WishDiffCallback()) {

    inner class WishViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvCategoryIcon: TextView = itemView.findViewById(R.id.tvCategoryIcon)
        val ivAuthorAvatar: ImageView = itemView.findViewById(R.id.ivAuthorAvatar)
        val cbComplete: CheckBox = itemView.findViewById(R.id.cbComplete)

        init {
            cbComplete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    val isChecked = cbComplete.isChecked
                    
                    updateStrikeThrough(tvTitle, tvDescription, isChecked)

                    onCheckClick(item, isChecked)
                }
            }

            cbComplete.setOnClickListener {
                performClickAction()
            }

            itemView.setOnClickListener {
                cbComplete.isChecked = !cbComplete.isChecked
                performClickAction()
            }


            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick(getItem(position))
                    true
                } else {
                    false
                }
            }
        }

        private fun performClickAction() {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val item = getItem(position)
                val isChecked = cbComplete.isChecked


                updateStrikeThrough(tvTitle, tvDescription, isChecked)

                onCheckClick(item, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_wishlist, parent, false)
        return WishViewHolder(view)
    }

    override fun onBindViewHolder(holder: WishViewHolder, position: Int) {
        val item = getItem(position)


        holder.itemView.translationX = 0f
        holder.itemView.alpha = 1f

        holder.tvTitle.text = item.title
        holder.tvCategoryIcon.text = getEmojiForCategory(item.category)

        GlideHelper.loadAvatar(holder.ivAuthorAvatar, item.creatorPhotoUrl, "WishAuthor")

        if (item.description.isNotEmpty()) {
            holder.tvDescription.text = item.description
            holder.tvDescription.visibility = View.VISIBLE
        } else {
            holder.tvDescription.visibility = View.GONE
        }


        holder.cbComplete.isChecked = item.isCompleted

        updateStrikeThrough(holder.tvTitle, holder.tvDescription, item.isCompleted)
    }

    private fun getEmojiForCategory(category: String): String {
        return when (category) {
            "movie" -> "🎬"
            "shopping" -> "🛒"
            "travel" -> "✈️"
            "date" -> "❤️"
            "food" -> "🍔"
            else -> "✨"
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
        override fun areItemsTheSame(oldItem: WishItem, newItem: WishItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: WishItem, newItem: WishItem) = oldItem == newItem
    }
}
