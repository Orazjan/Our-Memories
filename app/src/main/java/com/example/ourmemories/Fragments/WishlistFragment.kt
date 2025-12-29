package com.example.ourmemories.Fragments

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.WishlistAdapter
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.WishlistViewModel

class WishlistFragment : Fragment(R.layout.fragment_wishlist) {

    private lateinit var viewModel: WishlistViewModel
    private lateinit var adapter: WishlistAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[WishlistViewModel::class.java]

        val rvWishlist = view.findViewById<RecyclerView>(R.id.rvWishlist)
        val fabAdd = view.findViewById<View>(R.id.fabAddWish)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshWishlist)

        // Настройка адаптера
        adapter = WishlistAdapter(onCheckClick = { item, isChecked ->
            viewModel.toggleWishStatus(item, isChecked)
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        rvWishlist.layoutManager = LinearLayoutManager(context)
        rvWishlist.adapter = adapter
        rvWishlist.itemAnimator = null // Отключаем анимацию для избежания "мигания" при обновлении

        // Настраиваем свайп
        setupSwipeToComplete(rvWishlist, swipeRefresh)

        fabAdd.setOnClickListener {
            showAddWishDialog()
        }

        // Настройка SwipeRefresh
        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            viewModel.startListening()
        }

        observeViewModel(view)
    }

    private fun observeViewModel(view: View) {
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshWishlist)

        // Список желаний
        viewModel.wishes.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // Индикатор загрузки
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }

        // Тосты
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    // === SWIPE TO COMPLETE ===
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

                        // Меняем статус через ViewModel
                        viewModel.toggleWishStatus(item, !item.isCompleted)

                        // Важно: немедленно уведомляем адаптер об изменении, 
                        // так как обновление из Firestore придет с задержкой, 
                        // а ItemTouchHelper уже убрал элемент визуально.
                        adapter.notifyItemChanged(position)
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

                    super.onChildDraw(
                        c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                    )
                }

                override fun clearView(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    // Разблокируем SwipeRefresh
                    swipeRefresh.isEnabled = true

                    // Сбрасываем состояние View, если свайп был отменен
                    viewHolder.itemView.translationX = 0f
                    viewHolder.itemView.alpha = 1f
                }
            }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
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
                    viewModel.addWish(title, desc, category)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun showDeleteDialog(item: WishItem) {
        AlertDialog.Builder(requireContext()).setTitle("Удалить желание?")
            .setMessage("Вы уверены, что хотите удалить '${item.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteWish(item)
            }.setNegativeButton("Отмена", null).show()
    }
}
