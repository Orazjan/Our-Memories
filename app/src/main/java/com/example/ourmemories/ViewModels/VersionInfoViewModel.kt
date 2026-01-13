package com.example.ourmemories.ViewModels

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class VersionInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val descriptions = LinkedHashMap<String, String>()
    
    // LiveData для UI
    private val _versionNames = MutableLiveData<List<String>>()
    val versionNames: LiveData<List<String>> = _versionNames

    private val _selectedDescription = MutableLiveData<String>()
    val selectedDescription: LiveData<String> = _selectedDescription

    private val _currentAppVersion = MutableLiveData<String>()
    val currentAppVersion: LiveData<String> = _currentAppVersion

    init {
        setupVersionData()
        fetchCurrentAppVersion()
    }

    /**
     * Настройка данных версий.
     */
    private fun setupVersionData() {
        // Данные обновлений
        descriptions["V 0.1.x (Текущая)"] =
            "V 0.1.7\n" +
                    "• Сделан редизайн кнопок и некоторых экранов\n" +
                    "• Добавлено удаление конкретной фотографии или же альбома\n" +
                    "• Удалено долгое нажатие на альбом с последующим отображением функций\n" +
                    "• Исправлено в датапикере фоновый цвет \n" +
                    "• Добавлен прогрессбар во время загрузки фотографии\n"

        descriptions["V 0.1.6 (Архив)"] =
            "V 0.1.6\n" +
                    "• Добавлена статистика\n" +
                    "• Добавлены уведомления\n" +
                    "• Удалён календарь\n" +
                    "• Добавлен список желаний\n" +
                    "• Свайп для отметки желания\n" +
                    "• Добавлено дерево Любви\n" +
                    "• Добавлена тёмная тема\n" +
                    "• Добавлен виджет на рабочий стол\n\n" +
            "V 0.1.5\n" +
                    "• Добавлены записки для партнёра\n" +
                    "• Добавлены статусы (эмодзи и текст)\n" +
                    "• Новый дизайн выбора даты\n" +
                    "• Возможность выбора обложки альбома\n" +
                    "• Исправлена загрузка фото в галерее\n" +
                    "• Обновлены экраны входа и регистрации"
        descriptions["V 0.1.4 (Архив)"] =
            "V 0.1.4\n" +
                    "• Анимация сердца на главном экране\n" +
                    "• Кнопка 'Смотреть все' в ленте\n" +
                    "• Политика конфиденциальности\n" +
                    "• Долгое нажатие на фото в галерее\n" +
                    "• Защита от случайного выхода\n\n" +
                    "V 0.1.3\n" +
                    "• Обновлен дизайн профиля\n" +
                    "• Исправлены баги календаря\n\n" +
                    "V 0.1.2\n" +
                    "• Финальный дизайн кнопок\n" +
                    "• Рабочая регистрация\n\n" +
                    "V 0.1.1\n" +
                    "• Экран профиля\n" +
                    "• Автовход и выход\n" +
                    "• Смена имени и фото"

        val names = ArrayList(descriptions.keys)
        _versionNames.value = names

        // По умолчанию выбираем первую версию
        if (names.isNotEmpty()) {
            selectVersion(names[0])
        }
    }

    /**
     * Выбор версии.
     */
    fun selectVersion(versionName: String) {
        val desc = descriptions[versionName]
        _selectedDescription.value = desc ?: "Информация отсутствует."
    }

    /**
     * Получение текущей версии приложения.
     */
    private fun fetchCurrentAppVersion() {
        try {
            val context = getApplication<Application>()
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            _currentAppVersion.value = "Текущая версия: ${pInfo.versionName}"
        } catch (e: Exception) {
            _currentAppVersion.value = "Версия не определена"
        }
    }
}
