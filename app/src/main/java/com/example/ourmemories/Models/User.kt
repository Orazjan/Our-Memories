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
 * @property birthday День рождения.
 * @property hasWidget Флаг наличия виджета.
 * @property lastActive Последняя активность (timestamp).
 */
data class User(
    val uid: String = "",
    val name: String = "Я",
    val email: String? = null,
    val photoUrl: String? = null,
    val status: String? = null,
    val sharedNote: String? = null,
    val treePoints: Long = 0,
    val partnerUid: String? = null,
    val relationshipDate: Long = 0,
    val lastDailyDate: Long = 0,
    val partnerCode: String? = null,
    val birthDate: String? = null,
    val hasWidget: Boolean = false,
    val lastActive: Long = 0,


    val fcmToken: String? = null,
    val widgetImageUrl: String? = null

)
