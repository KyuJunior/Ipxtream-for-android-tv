package com.ipxtream.tv.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    /** Arabic keeps Western digits so counts, ratings and times match posters and channel numbers. */
    fun localeFor(languageCode: String): Locale =
        if (languageCode == "ar") Locale.forLanguageTag("ar-u-nu-latn") else Locale(languageCode)

    /**
     * A context whose resources follow the in-app language. Use it for text built outside
     * Compose (ViewModels, services): the application context keeps the system language.
     */
    fun localized(context: Context): Context {
        val code = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(context).settings.value.appLanguage.code
        val config = Configuration(context.resources.configuration)
        val locale = localeFor(code)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun wrap(context: Context, languageCode: String): Context {
        val locale = localeFor(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun applyLocale(activity: Activity, languageCode: String) {
        val locale = localeFor(languageCode)
        Locale.setDefault(locale)

        val resources = activity.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        configuration.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        activity.recreate()
    }
}
