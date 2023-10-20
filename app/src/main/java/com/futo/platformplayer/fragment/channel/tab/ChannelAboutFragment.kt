package com.futo.platformplayer.fragment.channel.tab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.views.platform.PlatformLinkView
import com.futo.polycentric.core.toName
import com.futo.polycentric.core.toURLInfoSystemLinkUrl

class ChannelAboutFragment : Fragment, IChannelTabFragment {
    private var _textName: TextView? = null;
    private var _textMetadata: TextView? = null;
    private var _textFindOn: TextView? = null;
    private var _textDescription: TextView? = null;
    private var _imageThumbnail: ImageView? = null;
    private var _linksContainer: LinearLayout? = null;

    private var _lastChannel: IPlatformChannel? = null;
    private var _lastPolycentricProfile: PolycentricProfile? = null;

    constructor() : super() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_channel_about, container, false);

        _textName = view.findViewById(R.id.text_channel_name);
        _textMetadata = view.findViewById(R.id.text_channel_metadata);
        _textDescription = view.findViewById(R.id.text_description);
        _textDescription!!.setPlatformPlayerLinkMovementMethod(view.context);
        _textFindOn = view.findViewById(R.id.text_find_on);
        _imageThumbnail = view.findViewById(R.id.image_channel_thumbnail);
        _linksContainer = view.findViewById(R.id.links_container);
        _imageThumbnail?.clipToOutline = true;
        _lastChannel?.also {
            setChannel(it);
        };
        _lastPolycentricProfile?.also {
            setPolycentricProfile(it, animate = false);
        }

        return view;
    }

    override fun onDestroyView() {
        super.onDestroyView();

        _textName = null;
        _textMetadata = null;
        _textDescription = null;
        _textFindOn = null;
        _imageThumbnail = null;
        _linksContainer = null;
    }

    override fun setChannel(channel: IPlatformChannel) {
        if(channel.description != null)
            _textDescription?.text = channel.description!!.fixHtmlLinks();

        _imageThumbnail?.let {
            Glide.with(it)
                .load(channel.thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(it);
        };
        _textName?.text = channel.name;

        val metadata = if(channel.subscribers > 0) "${channel.subscribers.toHumanNumber()} subscribers" else "";
        _textMetadata?.text = metadata;
        _lastChannel = channel;
        setLinks(channel.links, channel.name);
    }

    private fun setLinks(links: Map<String, String>, name: String) {
        val c = context;
        val l = _linksContainer;

        if (c != null && l != null) {
            l.removeAllViews();

            if (links.isNotEmpty()) {
                _textFindOn?.text = "Find $name on";
                _textFindOn?.visibility = View.VISIBLE;

                for (pair in links) {
                    val platformLinkView = PlatformLinkView(c);
                    platformLinkView.setPlatform(pair.key, pair.value);
                    l.addView(platformLinkView);
                }
            } else {
                _textFindOn?.visibility = View.GONE;
            }
        } else {
            _textFindOn?.visibility = View.GONE;
        }

    }

    fun setPolycentricProfile(polycentricProfile: PolycentricProfile?, animate: Boolean) {
        _lastPolycentricProfile = polycentricProfile;

        if (polycentricProfile == null) {
            return;
        }

        val map = hashMapOf<String, String>();
        for (c in polycentricProfile.ownedClaims) {
            try {
                val url = c.claim.resolveChannelUrl();
                val name = c.claim.toName();
                if (url != null && name != null) {
                    map[name] = url;
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to parse claim=$c", e)
            }
        }

        if (map.isNotEmpty())
            setLinks(map, if (polycentricProfile.systemState.username.isNotBlank()) polycentricProfile.systemState.username else _lastChannel?.name ?: "")

        if(polycentricProfile.systemState.description.isNotBlank())
            _textDescription?.text = polycentricProfile.systemState.description.fixHtmlLinks();

        if (polycentricProfile.systemState.username.isNotBlank())
            _textName?.text = polycentricProfile.systemState.username;

        val dp_80 = 80.dp(StateApp.instance.context.resources)
        val avatar = polycentricProfile.systemState.avatar?.selectBestImage(dp_80 * dp_80)?.let {
            it.toURLInfoSystemLinkUrl(polycentricProfile.system.toProto(), it.process, polycentricProfile.systemState.servers.toList())
        };

        if (avatar != null && _imageThumbnail != null)
            Glide.with(_imageThumbnail!!)
                .load(avatar)
                .into(_imageThumbnail!!);
    }

    companion object {
        val TAG = "AboutFragment";
        fun newInstance() = ChannelAboutFragment().apply { }
    }
}