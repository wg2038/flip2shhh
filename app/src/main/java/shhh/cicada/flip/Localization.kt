package shhh.cicada.flip

import android.content.Context

enum class AppLanguage { SIMPLIFIED, TRADITIONAL, ENGLISH }

/**
 * Single source of truth for resolving the app display language, shared by
 * MainActivity (AppStrings) and FlipToShhhService (notification texts).
 *
 * langMode: 0 = follow system, 1 = Simplified, 2 = Traditional, 3 = English.
 */
object Localization {
    fun resolve(context: Context, langMode: Int): AppLanguage {
        when (langMode) {
            1 -> return AppLanguage.SIMPLIFIED
            2 -> return AppLanguage.TRADITIONAL
            3 -> return AppLanguage.ENGLISH
        }

        val locale = context.resources.configuration.locales.get(0)
        if (!locale.language.startsWith("zh")) return AppLanguage.ENGLISH

        val isTrad = locale.country.equals("TW", ignoreCase = true) ||
                locale.country.equals("HK", ignoreCase = true) ||
                locale.country.equals("MO", ignoreCase = true) ||
                locale.script.equals("Hant", ignoreCase = true)
        return if (isTrad) AppLanguage.TRADITIONAL else AppLanguage.SIMPLIFIED
    }
}
