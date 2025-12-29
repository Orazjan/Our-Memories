package com.example.ourmemories.Models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Модель данных для воспоминания (фотоальбома).
 *
 * @property id Уникальный идентификатор документа.
 * @property title Название альбома.
 * @property description Описание события.
 * @property imageUrl URL обложки альбома.
 * @property timestamp Дата события (время в миллисекундах).
 * @property createdAt Дата создания записи.
 * @property uploaderUid UID создателя.
 * @property images Список URL всех фотографий в альбоме.
 */
@IgnoreExtraProperties
data class Memory(
    var id: String = "",
    
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",

    val timestamp: Long = 0L,
    val createdAt: Long = 0L,
    val uploaderUid: String = "",
    
    val images: List<String> = emptyList()
)
