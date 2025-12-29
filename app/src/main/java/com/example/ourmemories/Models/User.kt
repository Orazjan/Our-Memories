package com.example.ourmemories.Models

/**
 * Модель пользователя.
 *
 * @property uid Уникальный ID пользователя (из Firebase Auth).
 * @property name Имя пользователя.
 * @property photoUrl Ссылка на аватар.
 * @property status Текущий статус (эмодзи).
 * @property sharedNote Общая записка ("На холодильнике").
 * @property treePoints Очки дерева любви.
 * @property partnerUid UID партнера (если есть).
 * @property relationshipDate Дата начала отношений (timestamp).
 * @property lastDailyDate Дата последнего получения ежедневного бонуса.
 * @property partnerCode Код для подключения партнера.
 */
data class User(
    val uid: String = "",
    val name: String = "Я",
    val photoUrl: String? = null,
    val status: String? = null,
    val sharedNote: String? = null,
    val treePoints: Long = 0,
    val partnerUid: String? = null,
    val relationshipDate: Long = 0,
    val lastDailyDate: Long = 0,
    val partnerCode: String? = null
)
