package com.futo.platformplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.dp
import com.futo.platformplayer.polycentric.CommentLabel
import com.futo.platformplayer.polycentric.ModerationLabels
import com.futo.platformplayer.polycentric.ModerationsManager
import com.futo.platformplayer.views.pills.PillButton

/**
 * Covers a comment with a content warning (for moderation labels). Tapping "show" reveals the
 * comment.
 */
class ModerationWarnView : LinearLayout {
    private val _textWarning: TextView
    private val _textAppliedBy: TextView
    private val _buttonShow: PillButton
    private var _comment: PolycentricPlatformComment? = null

    val onShow = Event0()

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_moderation_warn, this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.background_moderation_warn)
        val padding = 10.dp(resources)
        setPadding(padding, padding / 2, padding / 2, padding / 2)

        _textWarning = findViewById(R.id.text_warning)
        _textAppliedBy = findViewById(R.id.text_applied_by)
        _buttonShow = findViewById(R.id.button_show)

        _buttonShow.onClick.subscribe {
            _comment?.moderationRevealed = true
            onShow.emit()
        }
    }

    /**
     * Create the overlay for [comment] if appropriate for its labels, and it has not been revealed.
     * Returns whether the comment's content is covered.
     */
    fun bind(comment: IPlatformComment): Boolean {
        val c = comment as? PolycentricPlatformComment
        _comment = c
        val warnings = if (c == null || c.moderationRevealed) emptyList() else ModerationsManager.getInstance().warnings(c.labels)
        if (c == null || warnings.isEmpty()) {
            visibility = View.GONE
            return false
        }

        setText(warnings, c.key?.identity)
        visibility = View.VISIBLE
        return true
    }

    /** Fill in the warning for [labels] on a comment written by [authorIdentity]. */
    private fun setText(labels: List<CommentLabel>, authorIdentity: String?) {
        val names = labels.map { it.value }.distinct()
            .sortedBy { ModerationLabels.values.indexOf(it) }
            .joinToString(", ") { ModerationLabels.displayName(context, it) }
        _textWarning.text = context.getString(R.string.content_warning_labels, names)

        val labelers = labels.distinctBy { it.labeledBy }.joinToString(", ") { label ->
            when {
                label.labeledBy == authorIdentity -> context.getString(R.string.the_author)
                !label.labeledByName.isNullOrBlank() -> label.labeledByName
                else -> shortIdentity(label.labeledBy)
            }
        }
        _textAppliedBy.text = context.getString(R.string.applied_by, labelers)
    }

    private fun shortIdentity(identity: String): String =
        if (identity.length <= 10) identity else identity.take(8) + "…"
}
