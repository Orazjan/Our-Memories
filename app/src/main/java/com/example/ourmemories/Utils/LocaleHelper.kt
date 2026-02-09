package com.example.ourmemories.Utils

import android.content.Context
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = Constants.PREFS_NAME
    private const val KEY_LANGUAGE = "language_code"

    fun onAttach(context: Context) {
        val lang = getPersistedData(context, Locale.getDefault().language)
        setLocale(context, lang)
    }

    fun setLocale(context: Context, languageCode: String) {
        persist(context, languageCode)
        updateResources(context, languageCode)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persist(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}