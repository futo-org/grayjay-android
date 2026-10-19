package com.futo.platformplayer.polycentric

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import com.futo.platformplayer.R

/**
 * A label attached to a Harbor comment, applied by the identity [labeledBy] (and the identity's
 * name [labeledByName], if available)
 */
data class CommentLabel(val value: String, val labeledBy: String, val labeledByName: String? = null)

/** How the user wants comments with a given moderation label to appear. */
enum class ModerationLevel { HIDE, WARN, SHOW }

/**
 * Harbor's moderation label vocabulary. The values and their order come from the SDK
 * (`org.futo.polycentric.ffi.moderationLabels()`); display strings are app resources.
 */
object ModerationLabels {
    data class Entry(val value: String, @StringRes val name: Int, @StringRes val description: Int)

    /** Localization strings for each label. */
    private val RESOURCES: Map<String, Pair<Int, Int>> = mapOf(
        "hate" to (R.string.moderation_label_hate to R.string.moderation_label_hate_description),
        "self-harm" to (R.string.moderation_label_self_harm to R.string.moderation_label_self_harm_description),
        "sexually-suggestive" to (R.string.moderation_label_sexually_suggestive to R.string.moderation_label_sexually_suggestive_description),
        "sexually-explicit" to (R.string.moderation_label_sexually_explicit to R.string.moderation_label_sexually_explicit_description),
        "violence" to (R.string.moderation_label_violence to R.string.moderation_label_violence_description),
    )

    @Volatile
    private var _values: List<String>? = null

    val values: List<String>
        get() = _values ?: org.futo.polycentric.ffi.moderationLabels().also { _values = it }

    /** Each label and it's name/description */
    val ENTRIES: List<Entry>
        get() = values.mapNotNull { v -> RESOURCES[v]?.let { (name, desc) -> Entry(v, name, desc) } }

    fun isModerationLabel(value: String): Boolean = value in values

    fun displayName(ctx: Context, value: String): String =
        RESOURCES[value]?.let { ctx.getString(it.first) } ?: value

    /** JVM unit tests cannot load the SDK's native library, so they supply the vocabulary. */
    @VisibleForTesting
    internal fun overrideValuesForTest(values: List<String>?) {
        _values = values
    }
}
