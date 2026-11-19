package mg.baiboly.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * Interface language chosen inside the app: "" follows the phone, otherwise
 * "mg", "fr" or "en". Few phones offer Malagasy as a system language, so the
 * choice cannot rely on the system setting alone.
 */
object UiLanguage {
    const val KEY = "ui_lang"

    /** Malagasy unless the user chooses otherwise ("" = same as the phone). */
    const val DEFAULT = "mg"
    val choices = listOf("mg", "fr", "en")

    fun current(context: Context): String =
        context.getSharedPreferences("app_state", Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    /** A context whose resources use the chosen language. */
    fun wrap(context: Context): Context = wrap(context, current(context))

    /**
     * A context whose resources use [lang] ("" = the phone's own language).
     * It wraps [context] rather than replacing it, so the activity stays
     * reachable through it (the system file picker, back handling and other
     * components look the activity up through the context chain).
     */
    fun wrap(context: Context, lang: String): Context {
        if (lang.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale(lang))
        val localizedResources = context.createConfigurationContext(config).resources
        return object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedResources
        }
    }
}
