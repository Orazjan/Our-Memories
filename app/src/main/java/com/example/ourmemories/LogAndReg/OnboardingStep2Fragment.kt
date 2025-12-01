package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R

class OnboardingStep2Fragment : Fragment(R.layout.fragment_onboarding_step2) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnFinish).setOnClickListener {
            (requireActivity() as? EnterActivity)?.finishOnboarding()
        }
    }
}