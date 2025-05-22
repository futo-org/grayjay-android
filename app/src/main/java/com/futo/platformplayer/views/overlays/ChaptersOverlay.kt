package com.futo.platformplayer.views.overlays

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.behavior.NonScrollingTextView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.segments.ChaptersList
import com.futo.platformplayer.views.segments.CommentsList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import userpackage.Protocol

class ChaptersOverlay : LinearLayout {
    val onClose = Event0();
    val onClick = Event1<IChapter>();

    private val _topbar: OverlayTopbar;
    private val _chaptersList: ChaptersList;
    private var _onChapterClicked: ((chapter: IChapter) -> Unit)? = null;
    private val _layoutItems: LinearLayout

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_chapters, this)
        _layoutItems = findViewById(R.id.layout_items)
        _topbar = findViewById(R.id.topbar);
        _chaptersList = findViewById(R.id.chapters_list);
        _chaptersList.onChapterClick.subscribe(onClick::emit);
        _topbar.onClose.subscribe(this, onClose::emit);
        _topbar.setInfo(context.getString(R.string.chapters), "");
    }

    fun setChapters(chapters: List<IChapter>?) {
        _chaptersList?.setChapters(chapters ?: listOf());
    }


    fun cleanup() {
        _topbar.onClose.remove(this);
        _onChapterClicked = null;
    }

    companion object {
        private const val TAG = "ChaptersOverlay"
    }
}