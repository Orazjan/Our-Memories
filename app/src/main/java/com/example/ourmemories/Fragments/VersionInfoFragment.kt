package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class VersionInfoFragment : Fragment(R.layout.version_info_fragment) {

    private lateinit var infoText: TextView
    private lateinit var versionSpinner: AutoCompleteTextView
    private lateinit var versionNames: List<String>
    private lateinit var versionDescriptions: Map<String, String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initUI(view)
        setupVersionData()
        setupSpinner()
        setupSpinnerListener()

        // Кнопка Назад
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Текущая версия внизу
        try {
            val pInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            view.findViewById<TextView>(R.id.tvAppVersion).text =
                "Текущая версия: ${pInfo.versionName}"
        } catch (e: Exception) {
        }

        if (versionNames.isNotEmpty()) {
            val initialVersionName = versionNames[0]
            infoText.text = versionDescriptions[initialVersionName]
            versionSpinner.setText(initialVersionName, false)
        }
    }

    private fun initUI(view: View) {
        infoText = view.findViewById(R.id.InfoText)
        versionSpinner = view.findViewById(R.id.mySpinner)
    }

    private fun setupVersionData() {
        val descriptions = LinkedHashMap<String, String>()

        // Данные обновлений
        descriptions["V 0.1.x (Текущая)"] =
            "V 0.1.6\n" +
                    "• Добавлено дерево Любви\n" +
            "V 0.1.5\n" +
                    "• Добавлены записки для партнёра\n" +
                    "• Добавлены статусы (эмодзи и текст)\n" +
                    "• Новый дизайн выбора даты\n" +
                    "• Возможность выбора обложки альбома\n" +
                    "• Исправлена загрузка фото в галерее\n" +
                    "• Обновлены экраны входа и регистрации"

        descriptions["V 0.0.x (Архив)"] =
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

        versionDescriptions = descriptions
        versionNames = ArrayList(descriptions.keys)
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_dropdown_item_1line, versionNames
        )
        versionSpinner.setAdapter(adapter)
    }

    private fun setupSpinnerListener() {
        versionSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedVersion = versionNames[position]
            val description = versionDescriptions[selectedVersion]

            infoText.text = description ?: "Информация отсутствует."
        }

        versionSpinner.setOnClickListener {
            versionSpinner.showDropDown()
        }
    }
}