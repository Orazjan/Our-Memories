package com.example.ourmemories.ui.setupprofile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.data.repositories.SetupProfileRepository
import com.example.ourmemories.utils.ImageHandler

class SetupProfileFactory(
    private val application: Application,
    private val repository: SetupProfileRepository,
    private val imageHandler: ImageHandler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SetupProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SetupProfileViewModel(
                application, repository, imageHandler
            ) as T
        }
        return super.create(modelClass)
    }


}