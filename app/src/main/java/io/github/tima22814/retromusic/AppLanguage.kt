package io.github.tima22814.retromusic

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The interface language picked inside the app. Android 13+ keeps it as the system's
 * per-app language; older versions store it here and apply it to every context.
 */
object AppLanguage {
    /** Language tag to its own name; "" follows the system. */
    val choices = listOf(
        "en" to "English",
        "ru" to "Русский",
        "uk" to "Українська",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "pt" to "Português",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "cs" to "Čeština",
        "tr" to "Türkçe",
        "sv" to "Svenska",
        "ja" to "日本語",
        "ko" to "한국어",
        "hi" to "हिन्दी",
        "id" to "Bahasa Indonesia",
        "vi" to "Tiếng Việt",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
    )

    private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            prefs(context).getString("tag", "").orEmpty()
        }

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            // The system restarts the activity in the new language by itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().putString("tag", tag).apply()
            apply(activity.applicationContext)
            activity.recreate()
        }
    }

    /** Before Android 13: [context] in the stored language, or as is when following the system. */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = prefs(context).getString("tag", "").orEmpty()
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(config)
    }

    /** Before Android 13: the library labels are read through the application context, so switch it too. */
    @Suppress("DEPRECATION")
    fun apply(app: Context) {
        if (Build.VERSION.SDK_INT >= 33) return
        val tag = prefs(app).getString("tag", "").orEmpty()
        val locale = if (tag.isEmpty()) LocaleList.getDefault()[0] else Locale.forLanguageTag(tag)
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
    }
}
