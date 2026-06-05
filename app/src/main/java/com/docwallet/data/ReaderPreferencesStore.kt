package com.docwallet.data

import android.content.Context

data class ReaderPreferences(
    val fontSize: Float = 1.3f,
    val fontFamilyName: String = FontFamilyName.SANS_SERIF.name,
)

enum class FontFamilyName(val label: String) {
    SERIF("Serif"),
    SANS_SERIF("Sans Serif"),
    OPEN_DYSLEXIC("OpenDyslexic"),
}

object ReaderPreferencesStore {
    private const val PREFS_NAME = "reader_preferences"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_FONT_FAMILY = "font_family"

    fun load(context: Context): ReaderPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fontSize = prefs.getFloat(KEY_FONT_SIZE, 1.3f)
        val fontFamilyName = prefs.getString(KEY_FONT_FAMILY, FontFamilyName.SANS_SERIF.name)
            ?: FontFamilyName.SANS_SERIF.name
        return ReaderPreferences(fontSize = fontSize, fontFamilyName = fontFamilyName)
    }

    fun save(context: Context, preferences: ReaderPreferences) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SIZE, preferences.fontSize)
            .putString(KEY_FONT_FAMILY, preferences.fontFamilyName)
            .apply()
    }
}
