package com.example.ourmemories.ui.gallery

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.adapters.MemoryAdapter
import com.example.ourmemories.ui.addmemory.AddMemoryFragment
import com.example.ourmemories.ui.memorydetail.MemoryDetailFragment
import com.example.ourmemories.data.models.Memory
import com.example.ourmemories.R
import com.example.ourmemories.data.repositories.GalleryRepository
import com.example.ourmemories.data.repositories.MainRepository
import com.example.ourmemories.databinding.GalleryFragmentBinding

class GalleryFragment : Fragment() {
    private var _binding: GalleryFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels {
        val application = requireActivity().application
        val repository = GalleryRepository()
        val mainRepository = MainRepository()
        GalleryFactory(application, repository, mainRepository)
    }
    private var isFirstLoad = true

    private lateinit var adapter: MemoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = GalleryFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
    }

    /**
     * Настройка пользовательского интерфейса.
     */
    private fun setupUI() {

        val layoutManager = LinearLayoutManager(context)
        binding.rvGallery.layoutManager = layoutManager
        binding.rvGallery.itemAnimator = null

        val controller =
            AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_slide_up)
        binding.rvGallery.layoutAnimation = controller

        adapter = MemoryAdapter(
            layoutResId = R.layout.item_album, onClick = { memory, imageView ->
                openMemoryDetail(memory, imageView)
            })
        binding.rvGallery.adapter = adapter

        binding.btnSearch.setOnClickListener {
            binding.tvTitle.visibility = View.GONE
            binding.layoutSearch.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
        }

        binding.btnCloseSearch.setOnClickListener {
            binding.layoutSearch.visibility = View.GONE
            binding.tvTitle.visibility = View.VISIBLE
            binding.etSearch.text.clear()
            viewModel.setSearchQuery("")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnSort.setOnClickListener { showSortMenu(it) }

        binding.rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 10 && binding.fabAddMemory.isVisible) {
                    binding.fabAddMemory.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200)
                        .withEndAction { binding.fabAddMemory.visibility = View.GONE }.start()
                } else if (dy < -10 && binding.fabAddMemory.visibility != View.VISIBLE) {
                    binding.fabAddMemory.visibility = View.VISIBLE
                    binding.fabAddMemory.scaleX = 0f
                    binding.fabAddMemory.scaleY = 0f
                    binding.fabAddMemory.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200)
                        .start()
                }

                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    viewModel.loadMore()
                }
            }
        })

        binding.swipeRefreshGallery.setColorSchemeResources(android.R.color.holo_red_light)
        binding.swipeRefreshGallery.setOnRefreshListener {
            isFirstLoad = true
            viewModel.refresh()
        }

        binding.fabAddMemory.setOnClickListener {
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
    private fun observeViewModel() {
        //        viewModel.memories.observe(viewLifecycleOwner) { list ->
//            adapter.submitList(list)
//            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
//        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && adapter.itemCount == 0) {
                binding.shimmerViewContainer.startShimmer()
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.rvGallery.visibility = View.GONE
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.rvGallery.visibility = View.VISIBLE
            }
        }


        viewModel.memories.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list) {
                if (isFirstLoad && list.isNotEmpty()) {
                    binding.rvGallery.scheduleLayoutAnimation()
                    isFirstLoad = false
                }
            }
            binding.tvEmptyGallery.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefreshGallery.isRefreshing = isRefreshing
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
        val detailFragment = MemoryDetailFragment.Companion.newInstance(
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}