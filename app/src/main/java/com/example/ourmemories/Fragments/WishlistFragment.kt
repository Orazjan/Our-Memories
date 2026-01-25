package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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

        adapter = WishlistAdapter(onCheckClick = { item, isChecked ->
            viewModel.toggleWishStatus(item, isChecked)
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        rvWishlist.layoutManager = LinearLayoutManager(context)
        rvWishlist.adapter = adapter
        rvWishlist.itemAnimator = null

        fabAdd.setOnClickListener {
            showAddWishDialog()
        }

        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            viewModel.startListening()
        }

        observeViewModel(view)
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshWishlist)

        viewModel.wishes.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Открытие диалога для добавления желания.
     */
    private fun showAddWishDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_wish, null)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDesc)
        val rgCategories = dialogView.findViewById<RadioGroup>(R.id.rgCategories)
        val btnAdd = dialogView.findViewById<View>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)

        btnAdd.setOnClickListener {
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
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Введите название", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Открытие диалога для удаления желания.
     */
    private fun showDeleteDialog(item: WishItem) {
        AlertDialog.Builder(requireContext()).setTitle("Удалить желание?")
            .setMessage("Вы уверены, что хотите удалить '${item.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteWish(item)
            }.setNegativeButton("Отмена", null).show()
    }
}
