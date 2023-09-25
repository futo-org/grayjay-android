package com.futo.platformplayer.views.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StatePlatform
import com.futo.polycentric.core.ClaimType


class PlatformLinkView : LinearLayout {
    private var _imagePlatform: ImageView;
    private var _textName: TextView;
    private var _url: String? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_platform_link, this, true);

        _imagePlatform = findViewById(R.id.image_platform);
        _textName = findViewById(R.id.text_name);

        val root: LinearLayout = findViewById(R.id.root);
        root.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW);
            val uri = Uri.parse(_url);

            //TODO: Check if it is OK that this is commented
            /*if (uri.host != null && uri.host!!.endsWith("youtube.com") && uri.path != null && uri.path!!.startsWith("/redirect")) {
                val redirectUrl = uri.getQueryParameter("q");
                i.data = Uri.parse(redirectUrl);
            } else {*/
                i.data = uri;
            //}
            startActivity(context, i, null);
        };
    }

    fun setPlatform(name: String, url: String) {
        val icon = StatePlatform.instance.getClientOrNullByUrl(url)?.icon;
        if (icon != null) {
            icon.setImageView(_imagePlatform, R.drawable.ic_web_white);
        } else {
            _imagePlatform.setImageResource(R.drawable.ic_web_white);
        }

        _textName.text = name;
        _url = url;
    }
}