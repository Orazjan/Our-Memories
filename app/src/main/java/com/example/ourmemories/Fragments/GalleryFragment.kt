package com.example.ourmemories.Fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.MemoryAdapter
import com.example.ourmemories.Factory.GalleryFactory
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.GalleryRepository
import com.example.ourmemories.Repositories.MainRepository
import com.example.ourmemories.ViewModels.GalleryViewModel
import com.facebook.shimmer.ShimmerFrameLayout
import androidx.core.view.isVisible

class GalleryFragment : Fragment(R.layout.gallery_fragment) {
    private val viewModel: GalleryViewModel by viewModels {
        val application = requireActivity().application
        val repository = GalleryRepository()
        val mainRepository = MainRepository()
        GalleryFactory(application, repository, mainRepository)
    }
    private var isFirstLoad = true

    private lateinit var adapter: MemoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val layoutSearch = view.findViewById<LinearLayout>(R.id.layoutSearch)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val btnCloseSearch = view.findViewById<ImageView>(R.id.btnCloseSearch)
        val btnSearch = view.findViewById<View>(R.id.btnSearch)
        val btnSort = view.findViewById<View>(R.id.btnSort)

        val layoutManager = LinearLayoutManager(context)
        rvGallery.layoutManager = layoutManager
        rvGallery.itemAnimator = null

        val controller =
            AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_slide_up)
        rvGallery.layoutAnimation = controller

        adapter = MemoryAdapter(
            layoutResId = R.layout.item_album, onClick = { memory, imageView ->
                openMemoryDetail(memory, imageView)
            })
        rvGallery.adapter = adapter

        btnSearch.setOnClickListener {
            tvTitle.visibility = View.GONE
            layoutSearch.visibility = View.VISIBLE
            etSearch.requestFocus()
        }

        btnCloseSearch.setOnClickListener {
            layoutSearch.visibility = View.GONE
            tvTitle.visibility = View.VISIBLE
            etSearch.text.clear()
            viewModel.setSearchQuery("")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSort.setOnClickListener { showSortMenu(it) }

        rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 10 && fabAdd.isVisible) {
                    fabAdd.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200)
                        .withEndAction { fabAdd.visibility = View.GONE }.start()
                } else if (dy < -10 && fabAdd.visibility != View.VISIBLE) {
                    fabAdd.visibility = View.VISIBLE
                    fabAdd.scaleX = 0f
                    fabAdd.scaleY = 0f
                    fabAdd.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
                }

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    viewModel.loadMore()
                }
            }
        })

        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            isFirstLoad = true
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
        val rvGallery = view.findViewById<RecyclerView>(R.id.rvGallery)
        val shimmer = view.findViewById<ShimmerFrameLayout>(R.id.shimmerViewContainer)

//        viewModel.memories.observe(viewLifecycleOwner) { list ->
//            adapter.submitList(list)
//            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
//        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && adapter.itemCount == 0) {
                shimmer.startShimmer()
                shimmer.visibility = View.VISIBLE
                rvGallery.visibility = View.GONE
            } else {
                shimmer.stopShimmer()
                shimmer.visibility = View.GONE
                rvGallery.visibility = View.VISIBLE
            }
        }


        viewModel.memories.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list) {
                if (isFirstLoad && list.isNotEmpty()) {
                    rvGallery.scheduleLayoutAnimation()
                    isFirstLoad = false
                }
            }
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
     * Открытие экрана детальной информации
     */
    private fun openMemoryDetail(memory: Memory, sharedImageView: ImageView) {
        val detailFragment = MemoryDetailFragment.newInstance(
            memory.id,
            memory.title,
            memory.description,
            memory.imageUrl,
            memory.timestamp,
            memory.uploaderUid
        )

        val transitionName =
            ViewCompat.getTransitionName(sharedImageView) ?: "memory_image_${memory.id}"

        parentFragmentManager.beginTransaction().setReorderingAllowed(true)
            .addSharedElement(sharedImageView, transitionName)
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Показать меню сортировки
     */
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.first_new))
        popup.menu.add(0, 2, 1, getString(R.string.first_old))
        popup.setOnMenuItemClickListener { item ->
            isFirstLoad = true
            when (item.itemId) {
                1 -> viewModel.setSortOrder(true)
                2 -> viewModel.setSortOrder(false)
            }
            true
        }
        popup.show()
    }
}
