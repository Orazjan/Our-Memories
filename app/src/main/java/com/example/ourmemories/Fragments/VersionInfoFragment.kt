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


        descriptions["V 0.1.3"] =
            "V 0.1.3\nДобавлено: \n– Убрано панель управления\n– Переработано страница профиля\n– Исправлены мелкие недочёты\n– Исправлены вкладки календарь и профиль\n– Во вкладке профиль добавлены новые элементы" +
                    "\n\nV 0.1.2 \nДобавлено: \n– Все кнопки рабочие\n– Дизайн окончательный\n– Окно для регистрации и входа сделаны" +
                    "\n\nV 0.1.1 \nДобавлено:\n– Экран профиля\n– Настроен автовход\n– Выход из аккаунта\n– Возможность смены имени и изображения\n– Настроена панель 'Последние воспоминания\n– Страница галереи" +
                    "\n\nV 0.1   \nДобавлено:\n– Языки русский и английский\n– Вход через email\n– Регистрация через email\n– Восстановление пароля\n– Экран приветствия\n– Настройка и запонение профиля"

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

            infoText.text = description ?: "$selectedVersion. Информация отсутствует."
        }

        versionSpinner.setOnClickListener {
            versionSpinner.showDropDown()
        }
    }

    companion object {
        private const val TAG = "VersionInfoFragment"
    }
}