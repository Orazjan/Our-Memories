package com.example.ourmemories.Factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.Repositories.SetupProfileRepository
import com.example.ourmemories.Utils.ImageHandler
import com.example.ourmemories.ViewModels.SetupProfileViewModel

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