package com.example.ourmemories.Models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Модель желания для списка желаний.
 *
 * @property id Уникальный ID документа.
 * @property title Название желания.
 * @property description Описание или ссылка.
 * @property category Категория (фильм, еда, покупка и т.д.).
 * @property creatorPhotoUrl Фото автора (для отображения в списке).
 * @property isCompleted Статус выполнения (true - выполнено).
 */
@IgnoreExtraProperties
data class WishItem(
    var id: String = "",
    
    val title: String = "",
    val description: String = "",
    val category: String = "other",
    
    val creatorPhotoUrl: String? = null,
    val createdBy: String = "",
    
    val timestamp: Long = 0L,

    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false
)
