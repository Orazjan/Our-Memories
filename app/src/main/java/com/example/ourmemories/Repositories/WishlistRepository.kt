package com.example.ourmemories.Repositories

import com.example.ourmemories.Models.User
import com.example.ourmemories.Models.WishItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class WishlistRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUser() = auth.currentUser

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Слушает изменения пользователя (чтобы узнать partnerUid).
     */
    fun listenToUser(uid: String, onUserData: (User?) -> Unit): ListenerRegistration {
        return db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) {
                onUserData(null)
                return@addSnapshotListener
            }
            val user = snapshot.toObject(User::class.java)?.copy(uid = uid)
            onUserData(user)
        }
    }

    /**
     * Слушает коллекцию wishes для списка пользователей.
     */
    fun listenToWishes(
        uids: List<String>, onData: (List<WishItem>) -> Unit, onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("wishes").whereIn("createdBy", uids)
            .orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { snapshots, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val loadedWishes = snapshots.map { doc ->
                        doc.toObject(WishItem::class.java).copy(id = doc.id)
                    }
                    onData(loadedWishes)
                }
            }
    }

    fun addWish(wish: WishItem, onFailure: (Exception) -> Unit) {
        db.collection("wishes").add(wish).addOnFailureListener { onFailure(it) }
    }

    fun updateWishStatus(wishId: String, isCompleted: Boolean, onFailure: (Exception) -> Unit) {
        db.collection("wishes").document(wishId).update("isCompleted", isCompleted)
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteWish(wishId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("wishes").document(wishId).delete().addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}