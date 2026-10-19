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
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.platform.PlatformLinkView

class ChannelAboutFragment : Fragment, IChannelTabFragment {
    private var _textName: TextView? = null;
    private var _textMetadata: TextView? = null;
    private var _textFindOn: TextView? = null;
    private var _textDescription: TextView? = null;
    private var _imageThumbnail: ImageView? = null;
    private var _linksContainer: LinearLayout? = null;

    private var _lastChannel: IPlatformChannel? = null;

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

        val metadata = if(channel.subscribers > 0) "${channel.subscribers.toHumanNumber()} " + (context?.getString(R.string.subscribers)?.lowercase() ?: "") else "";
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
                _textFindOn?.text = getString(R.string.find_name_on).replace("{name}", name);
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

    companion object {
        val TAG = "AboutFragment";
        fun newInstance() = ChannelAboutFragment().apply { }
    }
}