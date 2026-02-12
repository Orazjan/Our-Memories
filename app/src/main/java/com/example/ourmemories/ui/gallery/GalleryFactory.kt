package com.example.ourmemories.ui.gallery

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.data.repositories.GalleryRepository
import com.example.ourmemories.data.repositories.MainRepository

class GalleryFactory(
    private val application: Application,
    private val repository: GalleryRepository,
    private val MainRepository: MainRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return GalleryViewModel(
                application, repository, MainRepository
            ) as T
        }
        return super.create(modelClass)
    }
}