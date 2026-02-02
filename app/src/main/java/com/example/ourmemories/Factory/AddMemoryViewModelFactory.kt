package com.example.ourmemories.Factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.Repositories.AddMemoryRepository
import com.example.ourmemories.Utils.ImageHandler
import com.example.ourmemories.ViewModels.AddMemoryViewModel

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