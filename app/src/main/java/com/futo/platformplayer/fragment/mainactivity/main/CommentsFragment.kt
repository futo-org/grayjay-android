package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.PolycentricHomeActivity
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.adapters.CommentWithReferenceViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.overlays.RepliesOverlay
import com.futo.polycentric.core.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class CommentsFragment : MainFragment() {
    override val isMainView : Boolean = true
    override val isTab: Boolean = true
    override val hasBottomBar: Boolean get() = true

    private var _view: CommentsView? = null

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack)
        _view?.onShown()
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = CommentsView(this, inflater)
        _view = view
        return view
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView()
        _view = null
    }

    override fun onBackPressed(): Boolean {
        return _view?.onBackPressed() ?: false
    }

    override fun onResume() {
        super.onResume()
        _view?.onShown()
    }

    companion object {
        fun newInstance() = CommentsFragment().apply {}
        private const val TAG = "CommentsFragment"
    }

    class CommentsView : FrameLayout {
        private val _fragment: CommentsFragment
        private val _recyclerComments: RecyclerView;
        private val _adapterComments: InsertedViewAdapterWithLoader<CommentWithReferenceViewHolder>;
        private val _textCommentCount: TextView
        private val _comments: ArrayList<IPlatformComment> = arrayListOf();
        private val _llmReplies: LinearLayoutManager;
        private val _spinnerSortBy: Spinner;
        private val _layoutNotLoggedIn: LinearLayout;
        private val _buttonLogin: LinearLayout;
        private var _loading = false;
        private val _repliesOverlay: RepliesOverlay;
        private var _repliesAnimator: ViewPropertyAnimator? = null;

        private val _taskLoadComments = if(!isInEditMode) TaskHandler<PublicKey, List<IPlatformComment>>(
            StateApp.instance.scopeGetter, { StatePolycentric.instance.getSystemComments(context, it) })
            .success { pager -> onCommentsLoaded(pager); }
            .exception<UnknownHostException> {
                UIDialogs.toast("Failed to load comments");
                setLoading(false);
            }
            .exception<Throwable> {
                Logger.e(TAG, "Failed to load comments.", it);
                UIDialogs.toast(context, context.getString(R.string.failed_to_load_comments) + "\n" + (it.message ?: ""));
                setLoading(false);
            } else TaskHandler(IPlatformVideoDetails::class.java, StateApp.instance.scopeGetter);

        constructor(fragment: CommentsFragment, inflater: LayoutInflater) : super(inflater.context) {
            _fragment = fragment
            inflater.inflate(R.layout.fragment_comments, this)

            val commentHeader = findViewById<LinearLayout>(R.id.layout_header)
            (commentHeader.parent as ViewGroup).removeView(commentHeader)
            _textCommentCount = commentHeader.findViewById(R.id.text_comment_count)

            _recyclerComments = findViewById(R.id.recycler_comments)
            _adapterComments = InsertedViewAdapterWithLoader(context, arrayListOf(commentHeader), arrayListOf(),
                childCountGetter = { _comments.size },
                childViewHolderBinder = { viewHolder, position -> viewHolder.bind(_comments[position]); },
                childViewHolderFactory = { viewGroup, _ ->
                    val holder = CommentWithReferenceViewHolder(viewGroup);
                    holder.onDelete.subscribe(::onDelete);
                    holder.onRepliesClick.subscribe(::onRepliesClick);
                    return@InsertedViewAdapterWithLoader holder;
                }
            );

            _spinnerSortBy = commentHeader.findViewById(R.id.spinner_sortby);
            _spinnerSortBy.adapter = ArrayAdapter(context, R.layout.spinner_item_simple, resources.getStringArray(R.array.comments_sortby_array)).also {
                it.setDropDownViewResource(R.layout.spinner_dropdownitem_simple);
            };
            _spinnerSortBy.setSelection(0);
            _spinnerSortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (_spinnerSortBy.selectedItemPosition == 0) {
                        _comments.sortByDescending { it.date!! }
                    } else if (_spinnerSortBy.selectedItemPosition == 1) {
                        _comments.sortBy { it.date!! }
                    }

                    _adapterComments.notifyDataSetChanged()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            _llmReplies = LinearLayoutManager(context);
            _recyclerComments.layoutManager = _llmReplies;
            _recyclerComments.adapter = _adapterComments;
            updateCommentCountString();

            _layoutNotLoggedIn = findViewById(R.id.layout_not_logged_in)
            _layoutNotLoggedIn.visibility = View.GONE

            _buttonLogin = findViewById(R.id.button_login)
            _buttonLogin.setOnClickListener {
                context.startActivity(Intent(context, PolycentricHomeActivity::class.java));
            }

            _repliesOverlay = findViewById(R.id.replies_overlay);
            _repliesOverlay.onClose.subscribe { setRepliesOverlayVisible(isVisible = false, animate = true); };
        }

        private fun onDelete(comment: IPlatformComment) {
            val processHandle = StatePolycentric.instance.processHandle ?: return
            if (comment !is PolycentricPlatformComment) {
                return
            }

            val index = _comments.indexOf(comment)
            if (index != -1) {
                _comments.removeAt(index)
                _adapterComments.notifyItemRemoved(_adapterComments.childToParentPosition(index))

                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        processHandle.delete(comment.eventPointer.process, comment.eventPointer.logicalClock)
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to delete event.", e);
                        return@launch;
                    }

                    try {
                        Logger.i(TAG, "Started backfill");
                        processHandle.fullyBackfillServersAnnounceExceptions();
                        Logger.i(TAG, "Finished backfill");
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to fully backfill servers.", e);
                    }
                }
            }
        }

        fun onBackPressed(): Boolean {
            if (_repliesOverlay.visibility == View.VISIBLE) {
                setRepliesOverlayVisible(isVisible = false, animate = true);
                return true
            }

            return false
        }

        private fun onRepliesClick(c: IPlatformComment) {
            val replyCount = c.replyCount ?: 0;
            var metadata = "";
            if (replyCount > 0) {
                metadata += "$replyCount " + context.getString(R.string.replies);
            }

            if (c is PolycentricPlatformComment) {
                var parentComment: PolycentricPlatformComment = c;
                _repliesOverlay.load(false, metadata, c.contextUrl, c.reference, c,
                    { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) },
                    {
                        val newComment = parentComment.cloneWithUpdatedReplyCount((parentComment.replyCount ?: 0) + 1);
                        val index = _comments.indexOf(c);
                        _comments[index] = newComment;
                        _adapterComments.notifyItemChanged(_adapterComments.childToParentPosition(index));
                        parentComment = newComment;
                    });
            } else {
                _repliesOverlay.load(true, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) });
            }

            setRepliesOverlayVisible(isVisible = true, animate = true);
        }

        private fun setRepliesOverlayVisible(isVisible: Boolean, animate: Boolean) {
            val desiredVisibility = if (isVisible) View.VISIBLE else View.GONE
            if (_repliesOverlay.visibility == desiredVisibility) {
                return;
            }

            _repliesAnimator?.cancel();

            if (isVisible) {
                _repliesOverlay.visibility = View.VISIBLE;

                if (animate) {
                    _repliesOverlay.translationY = _repliesOverlay.height.toFloat();

                    _repliesAnimator = _repliesOverlay.animate()
                        .setDuration(300)
                        .translationY(0f)
                        .withEndAction {
                            _repliesAnimator = null;
                        }.apply { start() };
                }
            } else {
                if (animate) {
                    _repliesOverlay.translationY = 0f;

                    _repliesAnimator = _repliesOverlay.animate()
                        .setDuration(300)
                        .translationY(_repliesOverlay.height.toFloat())
                        .withEndAction {
                            _repliesOverlay.visibility = GONE;
                            _repliesAnimator = null;
                        }.apply { start(); }
                } else {
                    _repliesOverlay.visibility = View.GONE;
                    _repliesOverlay.translationY = _repliesOverlay.height.toFloat();
                }
            }
        }

        private fun updateCommentCountString() {
            _textCommentCount.text = context.getString(R.string.these_are_all_commentcount_comments_you_have_made_in_grayjay).replace("{commentCount}", _comments.size.toString())
        }

        private fun setLoading(loading: Boolean) {
            if (_loading == loading) {
                return;
            }

            _loading = loading;
            _adapterComments.setLoading(loading);
        }

        private fun fetchComments() {
            val system = StatePolycentric.instance.processHandle?.system ?: return
            _comments.clear()
            _adapterComments.notifyDataSetChanged()
            setLoading(true)
            _taskLoadComments.run(system)
        }

        private fun onCommentsLoaded(comments: List<IPlatformComment>) {
            setLoading(false)
            _comments.addAll(comments)

            if (_spinnerSortBy.selectedItemPosition == 0) {
                _comments.sortByDescending { it.date!! }
            } else if (_spinnerSortBy.selectedItemPosition == 1) {
                _comments.sortBy { it.date!! }
            }

            _adapterComments.notifyDataSetChanged()
            updateCommentCountString()
        }

        fun onShown() {
            val processHandle = StatePolycentric.instance.processHandle
            if (processHandle != null) {
                _layoutNotLoggedIn.visibility = View.GONE
                _recyclerComments.visibility = View.VISIBLE
                fetchComments()
            } else {
                _layoutNotLoggedIn.visibility = View.VISIBLE
                _recyclerComments.visibility=  View.GONE
            }
        }
    }
}