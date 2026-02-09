package com.example.ourmemories.ViewModels

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.ourmemories.Utils.VersionLogUtils

class VersionInfoViewModel(application: Application) : AndroidViewModel(application) {

    private var versionMap: Map<String, String> = emptyMap()

    private val _versionNames = MutableLiveData<List<String>>()
    val versionNames: LiveData<List<String>> = _versionNames

    private val _selectedDescription = MutableLiveData<String>()
    val selectedDescription: LiveData<String> = _selectedDescription

    private val _currentAppVersion = MutableLiveData<String>()
    val currentAppVersion: LiveData<String> = _currentAppVersion

    init {
        loadData()
        fetchCurrentAppVersion()
    }

    private fun loadData() {
        versionMap = VersionLogUtils.getChangelog()

        val names = ArrayList(versionMap.keys)
        _versionNames.value = names

        if (names.isNotEmpty()) {
            selectVersion(names[0])
        }
    }

    /**
     * Выбор версии.
     */
    fun selectVersion(versionName: String) {
        _selectedDescription.value = versionMap[versionName] ?: ""
    }

    /**
     * Получение текущей версии приложения.
     */
    private fun fetchCurrentAppVersion() {
        try {
            val context = getApplication<Application>()
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            _currentAppVersion.value = "Текущая версия: ${pInfo.versionName}"
        } catch (e: Exception) {
            _currentAppVersion.value = "Версия не определена"
        }
    }
}
