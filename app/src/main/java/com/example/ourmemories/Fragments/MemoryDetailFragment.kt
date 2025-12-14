package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MemoryDetailFragment : Fragment(R.layout.fragment_memory_detail) {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun newInstance(
            id: String,
            title: String,
            description: String,
            imageUrl: String,
            timestamp: Long,
            uploaderUid: String
        ): MemoryDetailFragment {
            val fragment = MemoryDetailFragment()
            val args = Bundle()
            args.putString("id", id)
            args.putString("title", title)
            args.putString("description", description)
            args.putString("imageUrl", imageUrl)
            args.putLong("timestamp", timestamp)
            args.putString("uploaderUid", uploaderUid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val id = arguments?.getString("id") ?: ""
        var title = arguments?.getString("title") ?: ""
        var description = arguments?.getString("description") ?: ""
        val imageUrl = arguments?.getString("imageUrl") ?: ""
        val timestamp = arguments?.getLong("timestamp") ?: 0L

        val ivFullImage = view.findViewById<ImageView>(R.id.ivFullImage)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val btnBack = view.findViewById<View>(R.id.btnBack)
        val btnDelete = view.findViewById<View>(R.id.btnDelete)
        val btnEdit = view.findViewById<View>(R.id.btnEdit)

        // Кнопки видны всем (общая собственность)
        btnDelete.visibility = View.VISIBLE
        btnEdit.visibility = View.VISIBLE

        tvTitle.text = title
        tvDescription.text = description
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        tvDate.text = sdf.format(timestamp)

        Glide.with(this)
            .load(imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(ivFullImage)

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        btnDelete.setOnClickListener { showDeleteDialog(id, imageUrl) }

        btnEdit.setOnClickListener {
            showEditDialog(id, title, description) { newTitle, newDesc ->
                // Обновляем UI локально
                tvTitle.text = newTitle
                tvDescription.text = newDesc
                title = newTitle
                description = newDesc
            }
        }
    }

    private fun showEditDialog(
        id: String,
        currentTitle: String,
        currentDesc: String,
        onUpdated: (String, String) -> Unit
    ) {
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etTitle = EditText(context).apply {
            hint = "Название"
            setText(currentTitle)
        }
        val etDesc = EditText(context).apply {
            hint = "Описание"
            setText(currentDesc)
            minLines = 3
        }

        dialogView.addView(etTitle)
        dialogView.addView(etDesc)

        AlertDialog.Builder(requireContext())
            .setTitle("Редактировать")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDesc = etDesc.text.toString().trim()
                updateMemory(id, newTitle, newDesc, onUpdated)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateMemory(
        id: String,
        title: String,
        desc: String,
        onUpdated: (String, String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                db.collection("memories").document(id)
                    .update(mapOf("title" to title, "description" to desc))
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Обновлено!", Toast.LENGTH_SHORT).show()
                    onUpdated(title, desc)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteDialog(memoryId: String, imageUrl: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить воспоминание?")
            .setMessage("Это действие удалит фото у обоих партнеров.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch { deleteMemory(memoryId, imageUrl) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private suspend fun deleteMemory(memoryId: String, imageUrl: String) {
        try {
            db.collection("memories").document(memoryId).delete().await()
            if (imageUrl.isNotEmpty()) {
                try {
                    storage.getReferenceFromUrl(imageUrl).delete().await()
                } catch (e: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}