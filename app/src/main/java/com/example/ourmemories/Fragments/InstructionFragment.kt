package com.example.ourmemories.Fragments

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.ourmemories.R
import com.example.ourmemories.databinding.FragmentInstructionBinding
import com.example.ourmemories.databinding.ItemFaqCardBinding

class InstructionFragment : Fragment() {

    private var _binding: FragmentInstructionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupFaqItem(
            binding.faqItem1,
            R.string.faq_connect_partner_q,
            R.string.faq_connect_partner_a
        )

        setupFaqItem(
            binding.faqItem2,
            R.string.faq_love_tree_q,
            R.string.faq_love_tree_a
        )

        setupFaqItem(
            binding.faqItem3,
            R.string.faq_add_widget_q,
            R.string.faq_add_widget_a
        )

        setupFaqItem(
            binding.faqItem4,
            R.string.faq_photos_loading_q,
            R.string.faq_photos_loading_a
        )

        setupFaqItem(
            binding.faqItemWidgetBug,
            R.string.faq_widget_bug_q,
            R.string.faq_widget_bug_a
        )

        setupFaqItem(
            binding.faqItemTree,
            R.string.faq_earn_points_q,
            R.string.faq_earn_points_a
        )

        setupFaqItem(
            binding.faqItemSwipe,
            R.string.faq_wishes_q,
            R.string.faq_wishes_a
        )

        setupFaqItem(
            binding.faqItemCover,
            R.string.faq_change_cover_q,
            R.string.faq_change_cover_a
        )

        setupFaqItem(
            binding.faqItemFridge,
            R.string.faq_fridge_q,
            R.string.faq_fridge_a
        )

        setupFaqItem(
            binding.faqItemPrivacy,
            R.string.faq_privacy_q,
            R.string.faq_privacy_a
        )

    }

    private fun setupFaqItem(itemBinding: ItemFaqCardBinding, questionRes: Int, answerRes: Int) {
        itemBinding.tvQuestion.setText(questionRes)
        itemBinding.tvAnswer.setText(answerRes)

        itemBinding.layoutHeader.setOnClickListener {
            val cardView = itemBinding.root
            TransitionManager.beginDelayedTransition(cardView.parent as ViewGroup, AutoTransition())

            if (itemBinding.layoutAnswer.isVisible) {
                itemBinding.layoutAnswer.visibility = View.GONE
                itemBinding.ivArrow.animate().rotation(0f).setDuration(200).start()
            } else {
                itemBinding.layoutAnswer.visibility = View.VISIBLE
                itemBinding.ivArrow.animate().rotation(180f).setDuration(200).start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}