package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ourmemories.Adapters.TimelineAdapter
import com.example.ourmemories.Models.Memory
import com.example.ourmemories.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class CalendarFragment : Fragment() {

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private lateinit var adapter: TimelineAdapter
    private var memoriesListener: ListenerRegistration? = null

    // Используем ваш новый макет calendar_fragment
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.calendar_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.rvGallery) // ID из вашего XML
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val btnAdd = view.findViewById<View>(R.id.btnAddEvent) // Кнопка плюсика вверху

        rv.layoutManager = LinearLayoutManager(context) // Вертикальный список для хронологии

        // Инициализация адаптера (TimelineAdapter)
        adapter = TimelineAdapter { memory ->
            // Открытие деталей при клике на событие
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
                ).replace(R.id.fragment_container, detailFragment).addToBackStack(null).commit()
        }
        rv.adapter = adapter

        // Кнопка добавления (переход на AddMemoryFragment)
        btnAdd.setOnClickListener {
            parentFragmentManager.beginTransaction().setCustomAnimations(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                ).replace(R.id.fragment_container, AddMemoryFragment()).addToBackStack(null)
                .commit()
        }

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = "$dayOfMonth.${month + 1}.$year"
            Toast.makeText(context, "Выбрано: $selectedDate", Toast.LENGTH_SHORT).show()
        }

        // Загрузка данных
        loadTimeline()
    }

    private fun loadTimeline() {
        val myUid = auth.currentUser?.uid ?: return

        // 1. Узнаем ID партнера, чтобы показывать и его события тоже
        db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
            val partnerUid = doc.getString("partnerUid")

            // Собираем список авторов (Я + Партнер)
            val uids = mutableListOf(myUid)
            if (partnerUid != null) {
                uids.add(partnerUid)
            }

            // 2. Грузим воспоминания из Firestore
            // Сортируем по дате (сначала новые)
            memoriesListener = db.collection("memories").whereIn("uploaderUid", uids)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (value != null) {
                        // Преобразуем документы в объекты Memory
                        val list = value.toObjects(Memory::class.java)
                        // Важно: копируем ID документа внутрь объекта
                        for (i in list.indices) {
                            list[i].id = value.documents[i].id
                        }

                        // Обновляем список в адаптере
                        adapter.submitList(list)
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        memoriesListener?.remove()
    }
}