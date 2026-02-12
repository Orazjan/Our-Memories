package com.example.ourmemories.ui.memorydetail

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.data.repositories.MemoryDetailRepository

class MemoryDetailFactory(
    private val application: Application,
    private val repository: MemoryDetailRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return MemoryDetailViewModel(
                application, repository
            ) as T
        }
        return super.create(modelClass)
    }
}