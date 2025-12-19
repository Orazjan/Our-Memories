package com.example.ourmemories.Fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
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

        // Настройка списка
        adapter = WishlistAdapter(onCheckClick = { item, isChecked ->
            toggleWishStatus(item.id, isChecked)
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        rvWishlist.layoutManager = LinearLayoutManager(context)
        rvWishlist.adapter = adapter
        rvWishlist.itemAnimator = null

        fabAdd.setOnClickListener {
            showAddWishDialog()
        }

        // Настройка SwipeRefresh
        swipeRefresh.setColorSchemeResources(android.R.color.holo_red_light)
        swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "Свайп обновления")
            // Перезапускаем слушатели для "чистого" обновления
            userListener?.remove()
            wishesListener?.remove()
            setupListeners(layoutEmpty, swipeRefresh)
        }

        // Запускаем загрузку
        swipeRefresh.isRefreshing = true
        setupListeners(layoutEmpty, swipeRefresh)
    }

    private fun setupListeners(layoutEmpty: View, swipeRefresh: SwipeRefreshLayout) {
        val myUid = auth.currentUser?.uid ?: return

        // Слушаем профиль, чтобы узнать ID партнера (для общего списка)
        userListener = db.collection("users").document(myUid).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Ошибка загрузки профиля", e)
                swipeRefresh.isRefreshing = false
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val partnerUid = snapshot.getString("partnerUid")

                // Собираем список ID: мой + партнера
                val uids = mutableListOf(myUid)
                if (partnerUid != null) uids.add(partnerUid)

                // Запускаем слушатель желаний
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

        // Слушатель Firestore: Работает в реальном времени
        wishesListener = db.collection("wishes").whereIn("createdBy", uids) // Фильтр по авторам
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                // Останавливаем анимацию загрузки при любом исходе
                swipeRefresh.isRefreshing = false

                if (e != null) {
                    Log.e(TAG, "Ошибка загрузки желаний.", e)
                    if (e.message?.contains("index") == true) {
                        Toast.makeText(
                            context, "Требуется индекс Firestore. См. логи.", Toast.LENGTH_LONG
                        ).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val wishes = snapshots.map { doc ->
                        doc.toObject(WishItem::class.java).copy(id = doc.id)
                    }

                    // Сортировка: Сначала невыполненные, потом выполненные
                    val sortedWishes = wishes.sortedBy { it.isCompleted }

                    adapter.submitList(sortedWishes)

                    // Управление видимостью заглушки
                    if (wishes.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                    } else {
                        layoutEmpty.visibility = View.GONE
                    }
                }
            }
    }

    private fun showAddWishDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_wish, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etDesc)

        AlertDialog.Builder(requireContext()).setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (title.isNotEmpty()) {
                    addWish(title, desc)
                }
            }.setNegativeButton("Отмена", null).show()
    }

    private fun addWish(title: String, desc: String) {
        val uid = auth.currentUser?.uid ?: return
        val wish = WishItem(
            title = title, description = desc, isCompleted = false,
            createdBy = uid, timestamp = System.currentTimeMillis()
        )

        db.collection("wishes").add(wish).addOnSuccessListener {
            Toast.makeText(context, "Желание добавлено", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error adding wish", e)
        }
    }

    private fun toggleWishStatus(id: String, isCompleted: Boolean) {
        if (id.isEmpty()) return

        Log.d(TAG, "Обновляем статус: $id -> $isCompleted")

        db.collection("wishes").document(id).update("isCompleted", isCompleted)
            .addOnFailureListener { e ->
                Log.e(TAG, "Ошибка обновления статуса", e)
                Toast.makeText(context, "Не удалось обновить: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                adapter.notifyDataSetChanged()
            }
    }

    private fun showDeleteDialog(item: WishItem) {
        AlertDialog.Builder(requireContext()).setTitle("Удалить желание?")
            .setMessage("Вы уверены, что хотите удалить '${item.title}'?")
            .setPositiveButton("Удалить") { _, _ ->
                db.collection("wishes").document(item.id).delete()
            }.setNegativeButton("Отмена", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        wishesListener?.remove()
    }
}