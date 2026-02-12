package com.example.ourmemories.ui.photoviewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PhotoViewerViewModel : ViewModel() {

    private val _images = MutableLiveData<List<String>?>()
    val images: LiveData<List<String>> = _images as LiveData<List<String>>

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    /**
     * Инициализация данных.
     */
    fun initData(imageList: ArrayList<String>?, startPos: Int) {
        if (imageList.isNullOrEmpty()) return

        if (_images.value == null) {
            _images.value = imageList
            _currentPosition.value = startPos
        }
    }

    fun onPageChanged(position: Int) {
        if (_currentPosition.value != position) {
            _currentPosition.value = position
        }
    }
}