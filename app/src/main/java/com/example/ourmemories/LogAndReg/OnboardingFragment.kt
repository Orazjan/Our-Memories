package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnGetStarted = view.findViewById<Button>(R.id.btnGetStarted)

        btnGetStarted.setOnClickListener {
            (requireActivity() as? EnterActivity)?.showOnboardingStep2()
        }
    }
}