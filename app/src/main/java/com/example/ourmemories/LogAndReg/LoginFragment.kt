package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import android.widget.Button // Или AppCompatButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class LoginFragment : Fragment(R.layout.login_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLogin = view.findViewById<View>(R.id.btn_login)
        val tvGoToRegister = view.findViewById<View>(R.id.tv_sign_up)
        val tvForgotPassword = view.findViewById<View>(R.id.btn_forgot_password)

        btnLogin.setOnClickListener {
            (requireActivity() as EnterActivity).onAuthSuccess()
        }

        tvGoToRegister.setOnClickListener {
            (requireActivity() as EnterActivity).showRegistration()
        }

        tvForgotPassword.setOnClickListener {
            (requireActivity() as EnterActivity).showForgotPassword()
        }
    }
}