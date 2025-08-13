package com.futo.platformplayer.fragment.mainactivity.special

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.FrameLayout
import android.widget.FrameLayout.GONE
import android.widget.FrameLayout.VISIBLE
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorMembershipLink
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.dp
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.fragment.mainactivity.main.BrowserFragment
import com.futo.platformplayer.fragment.mainactivity.main.ChannelFragment
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.MonetizationView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.overlays.DescriptionOverlay
import com.futo.platformplayer.views.overlays.RepliesOverlay
import com.futo.platformplayer.views.overlays.SupportOverlay
import com.futo.platformplayer.views.platform.PlatformIndicator
import com.futo.platformplayer.views.segments.CommentsList
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.Models
import com.futo.polycentric.core.PolycentricProfile
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class CommentsModalBottomSheet : BottomSheetDialogFragment() {
    var mainFragment: MainFragment? = null

    private lateinit var containerContent: FrameLayout
    private lateinit var containerContentMain: LinearLayout
    private lateinit var containerContentReplies: RepliesOverlay
    private lateinit var containerContentDescription: DescriptionOverlay
    private lateinit var containerContentSupport: SupportOverlay

    private lateinit var title: TextView
    private lateinit var subTitle: TextView
    private lateinit var channelName: TextView
    private lateinit var channelMeta: TextView
    private lateinit var creatorThumbnail: CreatorThumbnail
    private lateinit var channelButton: LinearLayout
    private lateinit var monetization: MonetizationView
    private lateinit var platform: PlatformIndicator
    private lateinit var textLikes: TextView
    private lateinit var textDislikes: TextView
    private lateinit var layoutRating: LinearLayout
    private lateinit var imageDislikeIcon: ImageView
    private lateinit var imageLikeIcon: ImageView

    private lateinit var description: TextView
    private lateinit var descriptionContainer: LinearLayout
    private lateinit var descriptionViewMore: TextView

    private lateinit var commentsList: CommentsList
    private lateinit var addCommentView: AddCommentView

    private var polycentricProfile: PolycentricProfile? = null

    private lateinit var buttonPolycentric: Button
    private lateinit var buttonPlatform: Button

    private var tabIndex: Int? = null

    private var contentOverlayView: View? = null

    lateinit var video: IPlatformVideoDetails

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>

    private val _taskLoadPolycentricProfile =
        TaskHandler<PlatformID, PolycentricProfile?>(StateApp.instance.scopeGetter, { ApiMethods.getPolycentricProfileByClaim(
            ApiMethods.SERVER, ApiMethods.FUTO_TRUST_ROOT, it.claimFieldType.toLong(), it.claimType.toLong(), it.value!!) }).success { setPolycentricProfile(it, animate = true) }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load claims.", it)
            }

    override fun onCreateDialog(
        savedInstanceState: Bundle?,
    ): Dialog {
        val bottomSheetDialog =
            BottomSheetDialog(requireContext(), R.style.Custom_BottomSheetDialog_Theme)
        bottomSheetDialog.setContentView(R.layout.modal_comments)

        behavior = bottomSheetDialog.behavior

        // TODO figure out how to not need all of these non null assertions
        containerContent = bottomSheetDialog.findViewById(R.id.content_container)!!
        containerContentMain = bottomSheetDialog.findViewById(R.id.videodetail_container_main)!!
        containerContentReplies =
            bottomSheetDialog.findViewById(R.id.videodetail_container_replies)!!
        containerContentDescription =
            bottomSheetDialog.findViewById(R.id.videodetail_container_description)!!
        containerContentSupport =
            bottomSheetDialog.findViewById(R.id.videodetail_container_support)!!

        title = bottomSheetDialog.findViewById(R.id.videodetail_title)!!
        subTitle = bottomSheetDialog.findViewById(R.id.videodetail_meta)!!
        channelName = bottomSheetDialog.findViewById(R.id.videodetail_channel_name)!!
        channelMeta = bottomSheetDialog.findViewById(R.id.videodetail_channel_meta)!!
        creatorThumbnail = bottomSheetDialog.findViewById(R.id.creator_thumbnail)!!
        channelButton = bottomSheetDialog.findViewById(R.id.videodetail_channel_button)!!
        monetization = bottomSheetDialog.findViewById(R.id.monetization)!!
        platform = bottomSheetDialog.findViewById(R.id.videodetail_platform)!!
        layoutRating = bottomSheetDialog.findViewById(R.id.layout_rating)!!
        textDislikes = bottomSheetDialog.findViewById(R.id.text_dislikes)!!
        textLikes = bottomSheetDialog.findViewById(R.id.text_likes)!!
        imageLikeIcon = bottomSheetDialog.findViewById(R.id.image_like_icon)!!
        imageDislikeIcon = bottomSheetDialog.findViewById(R.id.image_dislike_icon)!!

        description = bottomSheetDialog.findViewById(R.id.videodetail_description)!!
        descriptionContainer =
            bottomSheetDialog.findViewById(R.id.videodetail_description_container)!!
        descriptionViewMore =
            bottomSheetDialog.findViewById(R.id.videodetail_description_view_more)!!

        addCommentView = bottomSheetDialog.findViewById(R.id.add_comment_view)!!
        commentsList = bottomSheetDialog.findViewById(R.id.comments_list)!!
        buttonPolycentric = bottomSheetDialog.findViewById(R.id.button_polycentric)!!
        buttonPlatform = bottomSheetDialog.findViewById(R.id.button_platform)!!

        commentsList.onAuthorClick.subscribe { c ->
            if (c !is PolycentricPlatformComment) {
                return@subscribe
            }
            val id = c.author.id.value

            Logger.i(TAG, "onAuthorClick: $id")
            if (id != null && id.startsWith("polycentric://")) {
                val navUrl = "https://harbor.social/" + id.substring("polycentric://".length)
                mainFragment!!.startActivity(Intent(Intent.ACTION_VIEW, navUrl.toUri()))
            }
        }
        commentsList.onRepliesClick.subscribe { c ->
            val replyCount = c.replyCount ?: 0
            var metadata = ""
            if (replyCount > 0) {
                metadata += "$replyCount " + requireContext().getString(R.string.replies)
            }

            if (c is PolycentricPlatformComment) {
                var parentComment: PolycentricPlatformComment = c
                containerContentReplies.load(tabIndex!! != 0, metadata, c.contextUrl, c.reference, c, { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) }, {
                    val newComment = parentComment.cloneWithUpdatedReplyCount(
                        (parentComment.replyCount ?: 0) + 1
                    )
                    commentsList.replaceComment(parentComment, newComment)
                    parentComment = newComment
                })
            } else {
                containerContentReplies.load(tabIndex!! != 0, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) })
            }
            animateOpenOverlayView(containerContentReplies)
        }

        if (StatePolycentric.instance.enabled) {
            buttonPolycentric.setOnClickListener {
                setTabIndex(0)
                StateMeta.instance.setLastCommentSection(0)
            }
        } else {
            buttonPolycentric.visibility = GONE
        }

        buttonPlatform.setOnClickListener {
            setTabIndex(1)
            StateMeta.instance.setLastCommentSection(1)
        }

        val ref = Models.referenceFromBuffer(video.url.toByteArray())
        addCommentView.setContext(video.url, ref)

        if (Settings.instance.comments.recommendationsDefault && !Settings.instance.comments.hideRecommendations) {
            setTabIndex(2, true)
        } else {
            when (Settings.instance.comments.defaultCommentSection) {
                0 -> if (Settings.instance.other.polycentricEnabled) setTabIndex(0, true) else setTabIndex(1, true)
                1 -> setTabIndex(1, true)
                2 -> setTabIndex(StateMeta.instance.getLastCommentSection(), true)
            }
        }

        containerContentDescription.onClose.subscribe { animateCloseOverlayView() }
        containerContentReplies.onClose.subscribe { animateCloseOverlayView() }

        descriptionViewMore.setOnClickListener {
            animateOpenOverlayView(containerContentDescription)
        }

        updateDescriptionUI(video.description.fixHtmlLinks())

        val dp5 =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, resources.displayMetrics)
        val dp2 =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)

        //UI
        title.text = video.name
        channelName.text = video.author.name
        if (video.author.subscribers != null) {
            channelMeta.text = if ((video.author.subscribers
                    ?: 0) > 0
            ) video.author.subscribers!!.toHumanNumber() + " " + requireContext().getString(R.string.subscribers) else ""
            (channelName.layoutParams as MarginLayoutParams).setMargins(
                0, (dp5 * -1).toInt(), 0, 0
            )
        } else {
            channelMeta.text = ""
            (channelName.layoutParams as MarginLayoutParams).setMargins(0, (dp2).toInt(), 0, 0)
        }

        video.author.let {
            if (it is PlatformAuthorMembershipLink && !it.membershipUrl.isNullOrEmpty()) monetization.setPlatformMembership(video.id.pluginId, it.membershipUrl)
            else monetization.setPlatformMembership(null, null)
        }

        val subTitleSegments: ArrayList<String> = ArrayList()
        if (video.viewCount > 0) subTitleSegments.add("${video.viewCount.toHumanNumber()} ${if (video.isLive) requireContext().getString(
            R.string.watching_now) else requireContext().getString(R.string.views)}")
        if (video.datetime != null) {
            val diff = video.datetime?.getNowDiffSeconds() ?: 0
            val ago = video.datetime?.toHumanNowDiffString(true)
            if (diff >= 0) subTitleSegments.add("$ago ago")
            else subTitleSegments.add("available in $ago")
        }

        platform.setPlatformFromClientID(video.id.pluginId)
        subTitle.text = subTitleSegments.joinToString(" • ")
        creatorThumbnail.setThumbnail(video.author.thumbnail, false)

        setPolycentricProfile(null, animate = false)
        _taskLoadPolycentricProfile.run(video.author.id)

        when (video.rating) {
            is RatingLikeDislikes -> {
                val r = video.rating as RatingLikeDislikes
                layoutRating.visibility = VISIBLE

                textLikes.visibility = VISIBLE
                imageLikeIcon.visibility = VISIBLE
                textLikes.text = r.likes.toHumanNumber()

                imageDislikeIcon.visibility = VISIBLE
                textDislikes.visibility = VISIBLE
                textDislikes.text = r.dislikes.toHumanNumber()
            }

            is RatingLikes -> {
                val r = video.rating as RatingLikes
                layoutRating.visibility = VISIBLE

                textLikes.visibility = VISIBLE
                imageLikeIcon.visibility = VISIBLE
                textLikes.text = r.likes.toHumanNumber()

                imageDislikeIcon.visibility = GONE
                textDislikes.visibility = GONE
            }

            else -> {
                layoutRating.visibility = GONE
            }
        }

        monetization.onSupportTap.subscribe {
            containerContentSupport.setPolycentricProfile(polycentricProfile)
            animateOpenOverlayView(containerContentSupport)
        }

        monetization.onStoreTap.subscribe {
            polycentricProfile?.systemState?.store?.let {
                try {
                    val uri = it.toUri()
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = uri
                    requireContext().startActivity(intent)
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to open URI: '${it}'.", e)
                }
            }
        }
        monetization.onUrlTap.subscribe {
            mainFragment!!.navigate<BrowserFragment>(it)
        }

        addCommentView.onCommentAdded.subscribe {
            commentsList.addComment(it)
        }

        channelButton.setOnClickListener {
            mainFragment!!.navigate<ChannelFragment>(video.author)
        }

        return bottomSheetDialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        animateCloseOverlayView()
    }

    private fun setPolycentricProfile(profile: PolycentricProfile?, animate: Boolean) {
        polycentricProfile = profile

        val dp35 = 35.dp(requireContext().resources)
        val avatar = profile?.systemState?.avatar?.selectBestImage(dp35 * dp35)
            ?.let { it.toURLInfoSystemLinkUrl(profile.system.toProto(), it.process, profile.systemState.servers.toList()) }

        if (avatar != null) {
            creatorThumbnail.setThumbnail(avatar, animate)
        } else {
            creatorThumbnail.setThumbnail(video.author.thumbnail, animate)
            creatorThumbnail.setHarborAvailable(profile != null, animate, profile?.system?.toProto())
        }

        val username = profile?.systemState?.username
        if (username != null) {
            channelName.text = username
        }

        monetization.setPolycentricProfile(profile)
    }

    private fun setTabIndex(index: Int?, forceReload: Boolean = false) {
        Logger.i(TAG, "setTabIndex (index: ${index}, forceReload: ${forceReload})")
        val changed = tabIndex != index || forceReload
        if (!changed) {
            return
        }

        tabIndex = index
        buttonPlatform.setTextColor(resources.getColor(if (index == 1) R.color.white else R.color.gray_ac, null))
        buttonPolycentric.setTextColor(resources.getColor(if (index == 0) R.color.white else R.color.gray_ac, null))

        when (index) {
            null -> {
                addCommentView.visibility = GONE
                commentsList.clear()
            }

            0 -> {
                addCommentView.visibility = VISIBLE
                fetchPolycentricComments()
            }

            1 -> {
                addCommentView.visibility = GONE
                fetchComments()
            }
        }
    }

    private fun fetchComments() {
        Logger.i(TAG, "fetchComments")
        video.let {
            commentsList.load(true) { StatePlatform.instance.getComments(it) }
        }
    }

    private fun fetchPolycentricComments() {
        Logger.i(TAG, "fetchPolycentricComments")
        val video = video
        val idValue = video.id.value
        if (video.url.isEmpty()) {
            Logger.w(TAG, "Failed to fetch polycentric comments because url was null")
            commentsList.clear()
            return
        }

        val ref = Models.referenceFromBuffer(video.url.toByteArray())
        val extraBytesRef = idValue?.let { if (it.isNotEmpty()) it.toByteArray() else null }
        commentsList.load(false) { StatePolycentric.instance.getCommentPager(video.url, ref, listOfNotNull(extraBytesRef)); }
    }

    private fun updateDescriptionUI(text: Spanned) {
        containerContentDescription.load(text)
        description.text = text

        if (description.text.isNotEmpty()) descriptionContainer.visibility = VISIBLE
        else descriptionContainer.visibility = GONE
    }

    private fun animateOpenOverlayView(view: View) {
        if (contentOverlayView != null) {
            Logger.e(TAG, "Content overlay already open")
            return
        }

        behavior.isDraggable = false
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val animHeight = containerContentMain.height

        view.translationY = animHeight.toFloat()
        view.visibility = VISIBLE

        view.animate().setDuration(300).translationY(0f).withEndAction {
            contentOverlayView = view
        }.start()
    }

    private fun animateCloseOverlayView() {
        val curView = contentOverlayView
        if (curView == null) {
            Logger.e(TAG, "No content overlay open")
            return
        }

        behavior.isDraggable = true

        val animHeight = contentOverlayView!!.height

        curView.animate().setDuration(300).translationY(animHeight.toFloat()).withEndAction {
            curView.visibility = GONE
            contentOverlayView = null
        }.start()
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }
}