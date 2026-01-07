package com.example.ourmemories.Fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.MemoryAdapter
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.ViewModels.GalleryViewModel

class GalleryFragment : Fragment(R.layout.gallery_fragment) {

    private lateinit var viewModel: GalleryViewModel
    private lateinit var adapter: MemoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[GalleryViewModel::class.java]

        setupUI(view)
        observeViewModel(view)
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI(view: View) {
        val rvGallery = view.findViewById<RecyclerView>(R.id.rvGallery)
        val fabAdd = view.findViewById<View>(R.id.fabAddMemory)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshGallery)
        
        // Поиск
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val layoutSearch = view.findViewById<LinearLayout>(R.id.layoutSearch)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnCloseSearch = view.findViewById<ImageView>(R.id.btnCloseSearch)
        val btnSearch = view.findViewById<View>(R.id.btnSearch)
        val btnSort = view.findViewById<View>(R.id.btnSort)

        // RecyclerView
        val layoutManager = LinearLayoutManager(context)
        rvGallery.layoutManager = layoutManager
        rvGallery.itemAnimator = null // Чтобы не мигало при обновлении

        adapter = MemoryAdapter(layoutResId = R.layout.item_album, onClick = { memory ->
            openMemoryDetail(memory)
        }, onLongClick = { memory ->
            showMemoryOptions(memory)
        })
        rvGallery.adapter = adapter

        // Логика поиска UI
        btnSearch.setOnClickListener {
            tvTitle.visibility = View.GONE
            layoutSearch.visibility = View.VISIBLE
            etSearch.requestFocus()
        }

        btnCloseSearch.setOnClickListener {
            layoutSearch.visibility = View.GONE
            tvTitle.visibility = View.VISIBLE
            etSearch.text.clear()
            viewModel.setSearchQuery("") // Сброс поиска
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSort.setOnClickListener { showSortMenu(it) }

        // Скролл и пагинация
        rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Анимация FAB
                if (dy > 10 && fabAdd.visibility == View.VISIBLE) {
                    fabAdd.animate().alpha(0f).setDuration(200).withEndAction { fabAdd.visibility = View.GONE }
                } else if (dy < -10 && fabAdd.visibility != View.VISIBLE) {
                    fabAdd.visibility = View.VISIBLE
                    fabAdd.animate().alpha(1f).setDuration(200)
                }

                // Пагинация
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Если прокрутили до конца
                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    viewModel.loadMore()
                }
            }
        })

        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, AddMemoryFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Наблюдение за изменениями в ViewModel.
     */
    private fun observeViewModel(view: View) {
        val tvEmpty = view.findViewById<View>(R.id.tvEmptyGallery)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshGallery)

        // Список воспоминаний
        viewModel.memories.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // Индикатор загрузки (SwipeRefresh)
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }

        // Тосты и сообщения
        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Открытие экрана детальной информации
     */
    private fun openMemoryDetail(memory: Memory) {
        val detailFragment = MemoryDetailFragment.newInstance(
            memory.id,
            memory.title,
            memory.description,
            memory.imageUrl,
            memory.timestamp,
            memory.uploaderUid
        )
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .add(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Показать меню сортировки
     */
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Сначала новые")
        popup.menu.add(0, 2, 1, "Сначала старые")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> viewModel.setSortOrder(true)
                2 -> viewModel.setSortOrder(false)
            }
            true
        }
        popup.show()
    }

    /**
     * Диалог для выбора опций
     */
    private fun showMemoryOptions(memory: Memory) {
        val options = arrayOf("Поделиться", "Удалить")
        AlertDialog.Builder(requireContext())
            .setTitle(memory.title.ifEmpty { "Воспоминание" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareMemoryImage(memory)
                    1 -> confirmDelete(memory)
                }
            }
            .show()
    }

    /**
     * Поделиться фотографией
     */
    private fun shareMemoryImage(memory: Memory) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Посмотри наше воспоминание! ${memory.imageUrl}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Поделиться"))
    }

    /**
     * Диалог для удаления фотографии
     */
    private fun confirmDelete(memory: Memory) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить фото?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteMemory(memory)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
