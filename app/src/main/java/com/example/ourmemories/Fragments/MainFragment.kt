package com.example.ourmemories.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.ourmemories.EnterActivity
import com.example.ourmemories.R

class MainFragment : Fragment(R.layout.main_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageButton = view.findViewById<View>(R.id.btnSettings)

        imageButton.setOnClickListener {
            val intent = Intent(requireActivity(), EnterActivity::class.java)
            startActivity(intent)
        }

    }
}