package com.example.ourmemories.ui.editprofile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.data.repositories.EditProfileRepository
import com.example.ourmemories.utils.ImageHandler

class EditProfileViewModelFactory(
    private val application: Application,
    private val repository: EditProfileRepository,
    private val imageHandler: ImageHandler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditProfileViewModel(application, repository, imageHandler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}