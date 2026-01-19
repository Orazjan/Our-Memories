package com.example.ourmemories.Fragments

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class InstructionFragment : Fragment(R.layout.fragment_instruction) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Кнопка Назад
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Настройка вопросов
        setupFaqItem(
            view.findViewById(R.id.faqItem1),
            "Как подключить партнера?",
            "1. Зайдите в Профиль -> Поделиться кодом.\n2. Отправьте код партнеру.\n3. Партнер нажимает «Пригласить» на Главной и вводит ваш код."
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem2),
            "Как работает Дерево Любви?",
            "Дерево растет, когда вы пользуетесь приложением. Вы получаете очки за ежедневный вход (+10) и за загрузку фотографий (+5). Всего есть 10 стадий роста!"
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem3),
            "Как добавить Виджет?",
            "Зажмите палец на пустом месте рабочего стола -> Виджеты -> Найдите Our Memories -> Перетащите виджет на экран. Он обновляется автоматически."
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem4),
            "Фотографии не загружаются?",
            "Проверьте интернет-соединение. Если вы используете Xiaomi/Poco, убедитесь, что в настройках телефона для приложения включен доступ к сети и автозапуск."
        )
    }

    private fun setupFaqItem(card: View, question: String, answer: String) {
        val tvQuestion = card.findViewById<TextView>(R.id.tvQuestion)
        val tvAnswer = card.findViewById<TextView>(R.id.tvAnswer)
        val layoutAnswer = card.findViewById<LinearLayout>(R.id.layoutAnswer)
        val header = card.findViewById<LinearLayout>(R.id.layoutHeader)
        val arrow = card.findViewById<ImageView>(R.id.ivArrow)
        val cardView = card as CardView

        tvQuestion.text = question
        tvAnswer.text = answer

        header.setOnClickListener {

            TransitionManager.beginDelayedTransition(cardView.parent as ViewGroup, AutoTransition())

            if (layoutAnswer.visibility == View.VISIBLE) {
                layoutAnswer.visibility = View.GONE
                arrow.animate().rotation(0f).setDuration(200).start()
            } else {
                layoutAnswer.visibility = View.VISIBLE
                arrow.animate().rotation(180f).setDuration(200).start()
            }
        }
    }
}