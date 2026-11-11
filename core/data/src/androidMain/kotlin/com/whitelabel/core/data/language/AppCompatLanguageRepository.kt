package com.whitelabel.core.data.language

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.whitelabel.core.domain.LanguageRepository

/**
 * Per-app language. Android 13+ has it built in (`LocaleManager`, persisted by the system, also
 * shown in the system's app-language settings). Below that, AppCompat applies it to the shell's
 * `AppCompatActivity` and stores it (`autoStoreLocales` in the shell's manifest). Call on the main thread.
 */
internal class AppCompatLanguageRepository(
    private val context: Context,
) : LanguageRepository {

    override fun current(): String? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager().applicationLocales.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        }
        return tag
    }

    override fun set(languageTag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager().applicationLocales = LocaleList.forLanguageTags(languageTag.orEmpty())
        } else {
            AppCompatDelegate.setApplicationLocales(
                languageTag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
