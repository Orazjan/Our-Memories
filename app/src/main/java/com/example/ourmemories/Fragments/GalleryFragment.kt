package com.example.ourmemories.Fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.MemoryAdapter
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GalleryFragment : Fragment(R.layout.gallery_fragment) {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "GalleryFragment"

    private lateinit var adapter: MemoryAdapter

    // Для поиска и сортировки
    private var allMemories = listOf<Memory>()
    private var isNewestFirst = true

    // Пагинация
    private var queryLimit: Long = 20
    private var isLoadingMore = false

    private var userListener: ListenerRegistration? = null
    private var memoriesListener: ListenerRegistration? = null
    private var currentUidsToLoad: List<String>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация Views
        val rvGallery = view.findViewById<RecyclerView>(R.id.rvGallery)
        val fabAdd = view.findViewById<View>(R.id.fabAddMemory)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshGallery)
        val tvEmpty = view.findViewById<View>(R.id.tvEmptyGallery)

        // Элементы поиска
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val layoutSearch = view.findViewById<LinearLayout>(R.id.layoutSearch)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnCloseSearch = view.findViewById<ImageView>(R.id.btnCloseSearch)
        val btnSearch = view.findViewById<View>(R.id.btnSearch)
        val btnSort = view.findViewById<View>(R.id.btnSort)

        // Настройка RecyclerView (Вертикальный список альбомов)
        val layoutManager = LinearLayoutManager(context)
        rvGallery.layoutManager = layoutManager
        rvGallery.itemAnimator = null

        // Инициализация адаптера с макетом АЛЬБОМА
        adapter = MemoryAdapter(layoutResId = R.layout.item_album, onClick = { memory ->
            // Открытие деталей (обычный клик)
            val detailFragment = MemoryDetailFragment.newInstance(
                memory.id,
                memory.title,
                memory.description,
                memory.imageUrl,
                memory.timestamp,
                memory.uploaderUid
            )
            parentFragmentManager.beginTransaction().setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).add(R.id.fragment_container, detailFragment).addToBackStack(null).commit()
        }, onLongClick = { memory ->
            showMemoryOptions(memory)
        })
        rvGallery.adapter = adapter

        // === ПОИСК И СОРТИРОВКА ===
        btnSearch.setOnClickListener {
            tvTitle.visibility = View.GONE
            layoutSearch.visibility = View.VISIBLE
            etSearch.requestFocus()
        }

        btnCloseSearch.setOnClickListener {
            layoutSearch.visibility = View.GONE
            tvTitle.visibility = View.VISIBLE
            etSearch.text.clear()
            filterMemories("")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterMemories(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSort.setOnClickListener { showSortMenu(it) }

        // === СКРОЛЛ И ПАГИНАЦИЯ ===
        rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Скрытие FAB
                if (dy > 10 && fabAdd.visibility == View.VISIBLE) {
                    fabAdd.animate().alpha(0f).setDuration(200)
                        .withEndAction { fabAdd.visibility = View.GONE }
                } else if (dy < -10 && fabAdd.visibility != View.VISIBLE) {
                    fabAdd.visibility = View.VISIBLE
                    fabAdd.animate().alpha(1f).setDuration(200)
                }

                // Пагинация (подгрузка при достижении низа)
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoadingMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    isLoadingMore = true
                    loadMoreMemories(tvEmpty, swipeRefresh)
                }
            }
        })

        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            queryLimit = 20 // Сброс лимита при обновлении
            currentUidsToLoad = null
            setupUserListener(tvEmpty, swipeRefresh)
        }

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction().setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).replace(R.id.fragment_container, AddMemoryFragment()).addToBackStack(null)
                .commit()
        }

        setupUserListener(tvEmpty, swipeRefresh)
    }

    private fun loadMoreMemories(tvEmpty: View, swipeRefresh: SwipeRefreshLayout) {
        queryLimit += 20
        Handler(Looper.getMainLooper()).postDelayed({
            if (currentUidsToLoad != null) {
                setupMemoriesListener(currentUidsToLoad!!, tvEmpty, swipeRefresh)
            }
        }, 500)
    }


    /**
     * ДИАЛОГ ОПЦИЙ (Long press)
     */
    private fun showMemoryOptions(memory: Memory) {
        val options = arrayOf("Поделиться", "Удалить")
        AlertDialog.Builder(requireContext()).setTitle(memory.title.ifEmpty { "Воспоминание" })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareMemoryImage(memory)
                    1 -> confirmDelete(memory)
                }
            }.show()
    }

    /**
     * ПОДЕЛИТЬСЯ
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
     * Удаление
     */
    private fun confirmDelete(memory: Memory) {
        AlertDialog.Builder(requireContext()).setTitle("Удалить фото?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteMemory(memory)
            }.setNegativeButton("Отмена", null).show()
    }

    /**
     * УДАЛЕНИЕ
     */
    private fun deleteMemory(memory: Memory) {
        lifecycleScope.launch {
            try {
                db.collection("memories").document(memory.id).delete().await()
                if (memory.imageUrl.isNotEmpty()) {
                    try {
                        storage.getReferenceFromUrl(memory.imageUrl).delete().await()
                    } catch (e: Exception) {
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * ВЫБОР СОРТИРОВКИ
     */
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Сначала новые")
        popup.menu.add(0, 2, 1, "Сначала старые")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (!isNewestFirst) {
                        isNewestFirst = true; reloadMemories()
                    }
                }

                2 -> {
                    if (isNewestFirst) {
                        isNewestFirst = false; reloadMemories()
                    }
                }
            }
            true
        }
        popup.show()
    }

    /**
     * ОБНОВЛЕНИЕ
     *
     */
    private fun reloadMemories() {
        if (currentUidsToLoad != null) {
            val tvEmpty = view?.findViewById<View>(R.id.tvEmptyGallery)
            val swipeRefresh = view?.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshGallery)
            if (tvEmpty != null && swipeRefresh != null) {
                swipeRefresh.isRefreshing = true
                setupMemoriesListener(currentUidsToLoad!!, tvEmpty, swipeRefresh)
            }
        }
    }

    /**
     * ФИЛЬТР
     */
    private fun filterMemories(query: String) {
        val filteredList = if (query.isEmpty()) allMemories else allMemories.filter {
            it.title.contains(query, ignoreCase = true) || it.description.contains(
                query, ignoreCase = true
            )
        }
        val sortedList =
            if (isNewestFirst) filteredList.sortedByDescending { it.timestamp } else filteredList.sortedBy { it.timestamp }
        adapter.submitList(sortedList)
    }

    /**
     * ОБРАБОТКА ИЗМЕНЕНИЙ ПОЛЬЗОВАТЕЛЯ
     */
    private fun setupUserListener(tvEmpty: View, swipeRefresh: SwipeRefreshLayout) {
        val myUid = auth.currentUser?.uid ?: return
        swipeRefresh.isRefreshing = true
        userListener?.remove()
        userListener = db.collection("users").document(myUid).addSnapshotListener { snapshot, e ->
            if (!isAdded) return@addSnapshotListener
            if (e != null) {
                swipeRefresh.isRefreshing = false; return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val partnerUid = snapshot.getString("partnerUid")
                val uidsToLoad = mutableListOf(myUid)
                if (partnerUid != null) uidsToLoad.add(partnerUid)

                if (currentUidsToLoad != uidsToLoad) {
                    currentUidsToLoad = uidsToLoad
                    setupMemoriesListener(uidsToLoad, tvEmpty, swipeRefresh)
                } else {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    /**
     * ОБРАБОТКА ИЗМЕНЕНИЙ АЛЬБОМА
     */
    private fun setupMemoriesListener(
        uids: List<String>, tvEmpty: View, swipeRefresh: SwipeRefreshLayout
    ) {
        memoriesListener?.remove()
        val direction = if (isNewestFirst) Query.Direction.DESCENDING else Query.Direction.ASCENDING

        memoriesListener =
            db.collection("memories").whereIn("uploaderUid", uids).orderBy("timestamp", direction)
                .limit(queryLimit).addSnapshotListener { snapshots, e ->
                    isLoadingMore = false
                    if (e != null) {
                        if (isAdded) swipeRefresh.isRefreshing = false
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        allMemories = snapshots.map { doc ->
                            doc.toObject(Memory::class.java).copy(id = doc.id)
                        }
                        // Применяем фильтр (если есть текст поиска) и отправляем в адаптер
                        filterMemories(view?.findViewById<EditText>(R.id.etSearch)?.text.toString())

                        if (isAdded) {
                            tvEmpty.visibility =
                                if (allMemories.isEmpty()) View.VISIBLE else View.GONE
                            swipeRefresh.isRefreshing = false
                        }
                    }
                }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        memoriesListener?.remove()
    }
}