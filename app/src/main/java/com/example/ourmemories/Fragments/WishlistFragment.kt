package com.example.ourmemories.Fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.WishlistAdapter
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class WishlistFragment : Fragment(R.layout.fragment_wishlist) {

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: WishlistAdapter
    private val TAG = "WishlistFragment"

    private var wishesListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvWishlist = view.findViewById<RecyclerView>(R.id.rvWishlist)
        val fabAdd = view.findViewById<View>(R.id.fabAddWish)
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshWishlist)

        // Настройка адаптера
        adapter = WishlistAdapter(onCheckClick = { item, isChecked ->
            toggleWishStatus(item.id, isChecked)
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        rvWishlist.layoutManager = LinearLayoutManager(context)
        rvWishlist.adapter = adapter
        rvWishlist.itemAnimator = null

        // Настраиваем свайп
        setupSwipeToComplete(rvWishlist, swipeRefresh)

        fabAdd.setOnClickListener {
            showAddWishDialog()
        }

        // Настройка SwipeRefresh
        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления")
            userListener?.remove()
            wishesListener?.remove()
            setupListeners(layoutEmpty, swipeRefresh)
        }

        swipeRefresh.isRefreshing = true
        setupListeners(layoutEmpty, swipeRefresh)
    }

    // === SWIPE TO COMPLETE (Исправлено) ===
    private fun setupSwipeToComplete(recyclerView: RecyclerView, swipeRefresh: SwipeRefreshLayout) {
        val itemTouchHelperCallback =
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

                override fun onMove(
                    r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder
                ) = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && position < adapter.currentList.size) {
                        val item = adapter.currentList[position]

                        // Меняем статус
                        toggleWishStatus(item.id, !item.isCompleted)

                        // Используем notifyDataSetChanged для гарантированного сброса состояния ItemTouchHelper
                        adapter.notifyDataSetChanged()
                    }
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    // Блокируем SwipeRefresh во время горизонтального свайпа
                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                        if (dX != 0f) {
                            swipeRefresh.isEnabled = false
                        }
                    }

                    val itemView = viewHolder.itemView

                    // Рисуем фон только при свайпе влево и если есть смещение
                    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                        // Зеленый фон
                        val background = ColorDrawable(Color.parseColor("#4CAF50"))
                        background.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                        background.draw(c)

                        // Иконка галочки
                        val icon = ContextCompat.getDrawable(
                            requireContext(), android.R.drawable.checkbox_on_background
                        )
                        if (icon != null) {
                            icon.setTint(Color.WHITE)
                            val margin = (itemView.height - icon.intrinsicHeight) / 2
                            val iconTop =
                                itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                            val iconBottom = iconTop + icon.intrinsicHeight
                            val iconLeft = itemView.right - margin - icon.intrinsicWidth
                            val iconRight = itemView.right - margin

                            // Рисуем иконку только если она влезает
                            if (dX < -(margin + icon.intrinsicWidth)) {
                                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                                icon.draw(c)
                            }
                        }
                    }

                    // Всегда вызываем super, чтобы RecyclerView обработал смещение
                    super.onChildDraw(
                        c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                    )
                }

                // Гарантированная очистка
                override fun clearView(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    // Разблокируем SwipeRefresh
                    swipeRefresh.isEnabled = true
                    
                    viewHolder.itemView.translationX = 0f
                    viewHolder.itemView.alpha = 1f
                }
            }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun setupListeners(layoutEmpty: View, swipeRefresh: SwipeRefreshLayout) {
        val myUid = auth.currentUser?.uid ?: return

        userListener = db.collection("users").document(myUid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                swipeRefresh.isRefreshing = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val partnerUid = snapshot.getString("partnerUid")
                val uids = mutableListOf(myUid)
                if (partnerUid != null) uids.add(partnerUid)

                setupWishesListener(uids, layoutEmpty, swipeRefresh)
            } else {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupWishesListener(
        uids: List<String>, layoutEmpty: View, swipeRefresh: SwipeRefreshLayout
    ) {
        wishesListener?.remove()

        wishesListener = db.collection("wishes").whereIn("createdBy", uids)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                swipeRefresh.isRefreshing = false

                if (e != null) {
                    if (e.message?.contains("index") == true) {
                        Toast.makeText(context, "Требуется индекс. См. логи", Toast.LENGTH_LONG)
                            .show()
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val wishes = snapshots.map { doc ->
                        doc.toObject(WishItem::class.java).copy(id = doc.id)
                    }
                    // Сортировка: Сначала невыполненные
                    val sortedWishes = wishes.sortedBy { it.isCompleted }
                    adapter.submitList(sortedWishes)

                    layoutEmpty.visibility = if (wishes.isEmpty()) View.VISIBLE else View.GONE
                }
            }
    }

    private fun showAddWishDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_wish, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDesc)
        val rgCategories = dialogView.findViewById<RadioGroup>(R.id.rgCategories)

        AlertDialog.Builder(requireContext()).setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                val category = if (rgCategories != null) {
                    when (rgCategories.checkedRadioButtonId) {
                        R.id.catMovie -> "movie"
                        R.id.catFood -> "food"
                        R.id.catShopping -> "shopping"
                        R.id.catTravel -> "travel"
                        R.id.catDate -> "date"
                        else -> "other"
                    }
                } else "other"

                if (title.isNotEmpty()) {
                    addWish(title, desc, category)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun addWish(title: String, desc: String, category: String) {
        val user = auth.currentUser ?: return

        val wish = WishItem(
            title = title,
            description = desc,
            category = category,
            isCompleted = false,
            createdBy = user.uid,
            creatorPhotoUrl = user.photoUrl?.toString(),
            timestamp = System.currentTimeMillis()
        )

        db.collection("wishes").add(wish).addOnFailureListener {
            Toast.makeText(context, "Ошибка добавления", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleWishStatus(id: String, isCompleted: Boolean) {
        if (id.isNotEmpty()) {
            db.collection("wishes").document(id).update("isCompleted", isCompleted)
                .addOnFailureListener {
                    adapter.notifyDataSetChanged()
                }
        }
    }

    private fun showDeleteDialog(item: WishItem) {
        AlertDialog.Builder(requireContext()).setTitle("Удалить желание?")
            .setMessage("Вы уверены, что хотите удалить '${item.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteWish(item.id)
            }.setNegativeButton("Отмена", null).show()
    }

    private fun deleteWish(id: String) {
        if (id.isNotEmpty()) {
            db.collection("wishes").document(id).delete().addOnSuccessListener {
                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        wishesListener?.remove()
    }
}