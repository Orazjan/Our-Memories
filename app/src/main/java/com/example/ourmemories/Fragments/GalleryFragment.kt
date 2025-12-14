package com.example.ourmemories.Fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ourmemories.Adapters.MemoryAdapter
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class GalleryFragment : Fragment(R.layout.gallery_fragment) {

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "GalleryFragment"

    private lateinit var adapter: MemoryAdapter

    private var userListener: ListenerRegistration? = null
    private var memoriesListener: ListenerRegistration? = null

    private var currentUidsToLoad: List<String>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvGallery = view.findViewById<RecyclerView>(R.id.rvGallery)
        val fabAdd = view.findViewById<View>(R.id.fabAddMemory)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshGallery)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyGallery)

        val layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE
        rvGallery.layoutManager = layoutManager
        rvGallery.itemAnimator = null

        adapter = MemoryAdapter { memory ->
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
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        }
        rvGallery.adapter = adapter

        // Скрытие FAB при скролле
        rvGallery.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 10 && fabAdd.visibility == View.VISIBLE) {
                    fabAdd.animate().alpha(0f).setDuration(200).withEndAction {
                        fabAdd.visibility = View.GONE
                    }
                } else if (dy < -10 && fabAdd.visibility != View.VISIBLE) {
                    fabAdd.visibility = View.VISIBLE
                    fabAdd.animate().alpha(1f).setDuration(200)
                }
            }
        })

        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            currentUidsToLoad = null
            setupUserListener(tvEmpty, swipeRefresh)
        }

        fabAdd.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, AddMemoryFragment())
                .addToBackStack(null)
                .commit()
        }

        setupUserListener(tvEmpty, swipeRefresh)
    }

    private fun setupUserListener(tvEmpty: TextView, swipeRefresh: SwipeRefreshLayout) {
        val myUid = auth.currentUser?.uid ?: return
        swipeRefresh.isRefreshing = true

        userListener?.remove()
        userListener = db.collection("users").document(myUid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Ошибка загрузки профиля", e)
                swipeRefresh.isRefreshing = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val partnerUid = snapshot.getString("partnerUid")

                val uidsToLoad = mutableListOf(myUid)
                if (partnerUid != null) {
                    uidsToLoad.add(partnerUid)
                }

                if (currentUidsToLoad != uidsToLoad) {
                    currentUidsToLoad = uidsToLoad
                    setupMemoriesListener(uidsToLoad, tvEmpty, swipeRefresh)
                } else {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun setupMemoriesListener(uids: List<String>, tvEmpty: TextView, swipeRefresh: SwipeRefreshLayout) {
        memoriesListener?.remove()

        memoriesListener = db.collection("memories")
            .whereIn("uploaderUid", uids)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshots, e ->
                swipeRefresh.isRefreshing = false

                if (e != null) {
                    Log.e(TAG, "Ошибка загрузки ленты", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val newMemories = snapshots.map { doc ->
                        doc.toObject(Memory::class.java).copy(id = doc.id)
                    }

                    adapter.submitList(newMemories)

                    if (newMemories.isEmpty()) {
                        tvEmpty.visibility = View.VISIBLE
                    } else {
                        tvEmpty.visibility = View.GONE
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