package com.example.ourmemories.Utils

import android.content.Context
import java.util.Locale

object LocaleHelper {

    private val SUPPORTED_LANGUAGES = setOf("ru", "en", "ky", "tk")

    fun applyLanguage(context: Context) {
        val systemLocale = Context.MODE_PRIVATE
        val config = context.resources.configuration

        val sysLang = config.locales[0].language

        if (sysLang !in SUPPORTED_LANGUAGES) {
            setLocale(context, "ru")
        }
    }

    @Suppress("DEPRECATION")
    private fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}