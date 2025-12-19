package com.example.ourmemories.Models

import com.google.firebase.firestore.PropertyName

data class WishItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",

    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,

    val createdBy: String = "",
    val timestamp: Long = 0
)