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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class InstructionFragment : Fragment(R.layout.fragment_instruction) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupFaqItem(
            view.findViewById(R.id.faqItem1),
            R.string.faq_connect_partner_q,
            R.string.faq_connect_partner_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem2), R.string.faq_love_tree_q, R.string.faq_love_tree_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem3), R.string.faq_add_widget_q, R.string.faq_add_widget_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem4),
            R.string.faq_photos_loading_q,
            R.string.faq_photos_loading_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_WidgetBug),
            R.string.faq_widget_bug_q,
            R.string.faq_widget_bug_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_Tree),
            R.string.faq_earn_points_q,
            R.string.faq_earn_points_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_Swipe), R.string.faq_wishes_q, R.string.faq_wishes_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_Cover),
            R.string.faq_change_cover_q,
            R.string.faq_change_cover_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_Fridge), R.string.faq_fridge_q, R.string.faq_fridge_a
        )

        setupFaqItem(
            view.findViewById(R.id.faqItem_Privacy), R.string.faq_privacy_q, R.string.faq_privacy_a
        )
    }

    private fun setupFaqItem(card: View, question: Int, answer: Int) {
        val tvQuestion = card.findViewById<TextView>(R.id.tvQuestion)
        val tvAnswer = card.findViewById<TextView>(R.id.tvAnswer)
        val layoutAnswer = card.findViewById<LinearLayout>(R.id.layoutAnswer)
        val header = card.findViewById<LinearLayout>(R.id.layoutHeader)
        val arrow = card.findViewById<ImageView>(R.id.ivArrow)
        val cardView = card as CardView

        tvQuestion.setText(question)
        tvAnswer.setText(answer)

        header.setOnClickListener {

            TransitionManager.beginDelayedTransition(cardView.parent as ViewGroup, AutoTransition())

            if (layoutAnswer.isVisible) {
                layoutAnswer.visibility = View.GONE
                arrow.animate().rotation(0f).setDuration(200).start()
            } else {
                layoutAnswer.visibility = View.VISIBLE
                arrow.animate().rotation(180f).setDuration(200).start()
            }
        }
    }
}