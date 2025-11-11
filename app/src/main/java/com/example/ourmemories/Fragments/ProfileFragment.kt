package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.ourmemories.R

/**
 * Фрагмент профиля.
 */
class ProfileFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.profile_fragment, container, false)
        return view;
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val version: TextView = view.findViewById<TextView>(R.id.textVersion)
        var versionofApp = "0.0.1"

        version.setOnClickListener {
            Toast.makeText(
                requireContext(), "Версия приложения: $versionofApp ", Toast.LENGTH_LONG
            ).show()
        }
    }
}
