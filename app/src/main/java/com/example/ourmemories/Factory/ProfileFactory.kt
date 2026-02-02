package com.example.ourmemories.Factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ourmemories.Repositories.ProfileRepository
import com.example.ourmemories.ViewModels.ProfileViewModel

class ProfileFactory(
    private val application: Application, private val repository: ProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {

        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return ProfileViewModel(
                application, repository
            ) as T
        }
        return super.create(modelClass)
    }
}