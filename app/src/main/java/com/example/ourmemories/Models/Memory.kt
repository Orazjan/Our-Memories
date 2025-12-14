package com.example.ourmemories.Models

data class Memory(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var imageUrl: String = "",

    var timestamp: Long = 0L,

    var createdAt: Long = 0L,
    var uploaderUid: String = ""
) {
    // Обязателен пустой конструктор для Firebase!
    constructor() : this("", "", "", "", 0L, 0L, "")
}