package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ourmemories.Adapters.WishlistAdapter
import com.example.ourmemories.Factory.WishListFactory
import com.example.ourmemories.Models.WishItem
import com.example.ourmemories.R
import com.example.ourmemories.Repositories.WishlistRepository
import com.example.ourmemories.Utils.AnimationHelper
import com.example.ourmemories.ViewModels.WishlistViewModel
import com.example.ourmemories.databinding.FragmentWishlistBinding
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class WishlistFragment : Fragment() {
    private var _binding: FragmentWishlistBinding? = null

    private val binding get() = _binding!!

    private var isFirstLoad = true


    private val viewModel: WishlistViewModel by viewModels {
        val application = requireActivity().application
        val repository = WishlistRepository()
        WishListFactory(application, repository)
    }

    private lateinit var adapter: WishlistAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        AnimationHelper.addTouchBounce(binding.fabAddWish)

        adapter = WishlistAdapter(onCheckClick = { item, isChecked, view ->
            viewModel.toggleWishStatus(item, isChecked)
            if (isChecked) {
                val location = IntArray(2)
                view.getLocationInWindow(location)

                val x = location[0] + view.width / 2f
                val y = location[1] + view.height / 2f

                playKonfetti(x, y)
            }
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        binding.rvWishlist.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WishlistFragment.adapter
            itemAnimator = null

            layoutAnimation =
                AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_slide_up)
        }


        binding.fabAddWish.setOnClickListener {
            showAddWishDialog()
        }

        binding.swipeRefreshWishlist.setColorSchemeResources(android.R.color.holo_red_light)
        binding.swipeRefreshWishlist.setOnRefreshListener {
            viewModel.startListening()
        }
    }

    private fun observeViewModel() {
        viewModel.wishes.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            if (isFirstLoad && list.isNotEmpty()) {
                binding.rvWishlist.scheduleLayoutAnimation()
                isFirstLoad = false
            }
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefreshWishlist.isRefreshing = isRefreshing
            isFirstLoad = true
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }
    }

    /**
     * Запуск анимации конфетти.
     */
    private fun playKonfetti(x: Float, y: Float) {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(100),
            position = Position.Absolute(x, y)
        )
        binding.konfettiView.start(party)
    }

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
                Toast.makeText(context, getString(R.string.error_enter_title), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteDialog(item: WishItem) {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.delete_wish_title))
            .setMessage(getString(R.string.delete_wish_confirm, item.title))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteWish(item)
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}