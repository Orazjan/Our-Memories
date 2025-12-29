package com.example.ourmemories.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PhotoViewerViewModel : ViewModel() {

    private val _images = MutableLiveData<List<String>>()
    val images: LiveData<List<String>> = _images

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    // Инициализация данных (вызывается из Fragment один раз)
    fun initData(imageList: ArrayList<String>?, startPos: Int) {
        if (imageList.isNullOrEmpty()) return
        
        // Загружаем данные только если они еще не загружены (например, при первом запуске)
        if (_images.value == null) {
            _images.value = imageList
            _currentPosition.value = startPos
        }
    }

    // Обновление позиции при свайпе
    fun onPageChanged(position: Int) {
        if (_currentPosition.value != position) {
            _currentPosition.value = position
        }
    }
}
