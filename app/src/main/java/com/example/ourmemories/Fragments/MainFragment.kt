package com.example.ourmemories.Fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.ourmemories.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Фрагмент главного экрана.
 */
class MainFragment : Fragment(R.layout.main_fragment) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fabAddMemory = view.findViewById<FloatingActionButton>(R.id.fab_add_memory)

        fabAddMemory.setOnClickListener {
            val fragment = FabFragment()
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack(null)
            transaction.commit()

        }
    }
}