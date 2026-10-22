package com.futo.platformplayer.polycentric

import android.content.Context
import android.content.SharedPreferences

/**
 * User moderation preferences for Harbor comments. Levels are "hide", "warn", and "show", with the
 * default being "warn" (show the comment after dismissing an overlay).
 */
class ModerationsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("polycentric_moderation", Context.MODE_PRIVATE)

    fun getLevel(value: String): ModerationLevel =
        when (prefs.getString(prefKey(value), null)) {
            "hide" -> ModerationLevel.HIDE
            "show" -> ModerationLevel.SHOW
            else -> ModerationLevel.WARN
        }

    fun setLevel(value: String, level: ModerationLevel) {
        prefs.edit().putString(prefKey(value), level.name.lowercase()).apply()
    }

    /** Label values set to "hide", for the `omitLabels` field in feed queries. */
    fun omitLabels(): List<String> =
        ModerationLabels.values.filter { getLevel(it) == ModerationLevel.HIDE }

    /**
     * The moderation labels in [labels] that the user wants a content warning for. "Hide" labels
     * also count as warnings.
     */
    fun warnings(labels: List<CommentLabel>): List<CommentLabel> =
        labels.filter { ModerationLabels.isModerationLabel(it.value) && getLevel(it.value) != ModerationLevel.SHOW }

    companion object {
        private fun prefKey(value: String) = "label_$value"

        @Volatile
        private var instance: ModerationsManager? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = ModerationsManager(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): ModerationsManager {
            return instance ?: throw IllegalStateException("ModerationsManager not initialized")
        }
    }
}
