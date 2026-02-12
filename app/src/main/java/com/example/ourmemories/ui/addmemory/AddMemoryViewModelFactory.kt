package com.example.ourmemories.ui.addmemory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.data.repositories.AddMemoryRepository
import com.example.ourmemories.utils.ImageHandler

class AddMemoryViewModelFactory(
    private val application: Application,
    private val repository: AddMemoryRepository,
    private val imageHandler: ImageHandler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddMemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return AddMemoryViewModel(
                application, repository, imageHandler
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}