package com.example.ourmemories.Repositories

import com.example.ourmemories.Models.Memory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class GalleryRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Слушает коллекцию memories для списка пользователей (я + партнер).
     * @param uids Список UID (мой и партнера).
     * @param isNewestFirst Сортировка.
     * @param limit Лимит записей.
     * @param onDataCallback Функция, которая примет список загруженных фото.
     * @param onError Функция для ошибок.
     * @return Возвращает ListenerRegistration, чтобы ViewModel могла остановить прослушивание.
     */
    fun listenToMemories(
        uids: List<String>,
        isNewestFirst: Boolean,
        limit: Long,
        onDataCallback: (List<Memory>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val direction = if (isNewestFirst) Query.Direction.DESCENDING else Query.Direction.ASCENDING

        return db.collection("memories").whereIn("uploaderUid", uids)
            .orderBy("timestamp", direction).limit(limit).addSnapshotListener { snapshots, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val memories = snapshots.map { doc ->
                        doc.toObject(Memory::class.java).copy(id = doc.id)
                    }
                    onDataCallback(memories)
                }
            }
    }

}