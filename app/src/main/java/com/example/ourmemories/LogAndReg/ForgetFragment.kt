package com.example.ourmemories.LogAndReg

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

class ForgotPasswordFragment : Fragment(R.layout.forget_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val btnBackToLogin = view.findViewById<TextView>(R.id.btnBackToLogin)
        val btnSendReset = view.findViewById<Button>(R.id.btnSendReset)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)

        btnBack.setOnClickListener {
            (requireActivity() as EnterActivity).showLogin()
        }

        btnBackToLogin.setOnClickListener {
            (requireActivity() as EnterActivity).showLogin()
        }

        btnSendReset.setOnClickListener {
            val email = etEmail.text.toString()
            if (email.isNotEmpty()) {
                Toast.makeText(context, "Reset link sent to $email", Toast.LENGTH_SHORT).show()
                (requireActivity() as EnterActivity).showLogin()
            } else {
                Toast.makeText(context, "Please enter email", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
