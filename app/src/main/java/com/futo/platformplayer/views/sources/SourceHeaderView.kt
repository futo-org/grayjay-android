package com.futo.platformplayer.views.sources

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.states.StatePlugins

class SourceHeaderView : LinearLayout {
    private val _sourceImage: ImageView;
    private val _sourceTitle: TextView;
    private val _sourceBy: TextView;
    private val _sourceAuthorID: TextView;
    private val _sourceDescription: TextView;

    private val _sourceVersion: TextView;
    private val _sourcePlatformUrl: TextView;
    private val _sourceRepositoryUrl: TextView;
    private val _sourceScriptUrl: TextView;
    private val _sourceSignature: TextView;

    private val _sourcePlatformUrlContainer: LinearLayout;

    private var _config : SourcePluginConfig? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.view_source_header, this);

        _sourceImage = findViewById(R.id.source_image);
        _sourceTitle = findViewById(R.id.source_title);
        _sourceBy = findViewById(R.id.source_by);
        _sourceAuthorID = findViewById(R.id.source_author_id);
        _sourceDescription = findViewById(R.id.source_description);

        _sourceVersion = findViewById(R.id.source_version);
        _sourceRepositoryUrl = findViewById(R.id.source_repo);
        _sourcePlatformUrl = findViewById(R.id.source_platform);
        _sourcePlatformUrlContainer = findViewById(R.id.source_platform_container);
        _sourceScriptUrl = findViewById(R.id.source_script);
        _sourceSignature = findViewById(R.id.source_signature);

        _sourceBy.setOnClickListener {
            if(!_config?.authorUrl.isNullOrEmpty())
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_config?.authorUrl)));
        }
        _sourceRepositoryUrl.setOnClickListener {
            if(!_config?.repositoryUrl.isNullOrEmpty())
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_config?.repositoryUrl)));
        };
        _sourceScriptUrl.setOnClickListener {
            if(!_config?.absoluteScriptUrl.isNullOrEmpty())
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_config?.absoluteScriptUrl)));
        };
        _sourcePlatformUrl.setOnClickListener {
            if(!_config?.platformUrl.isNullOrEmpty())
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_config?.platformUrl)));
        };
    }

    fun loadConfig(config: SourcePluginConfig, script: String?) {
        _config = config;

        val loadedIcon = StatePlugins.instance.getPluginIconOrNull(config.id);
        if(loadedIcon != null)
            loadedIcon.setImageView(_sourceImage);
        else
            Glide.with(_sourceImage)
                .load(config.absoluteIconUrl)
                .into(_sourceImage);

        _sourceTitle.text = config.name;
        _sourceBy.text = config.author
        _sourceDescription.text = config.description;
        _sourceVersion.text = config.version.toString();
        _sourceScriptUrl.text = config.absoluteScriptUrl;
        _sourceRepositoryUrl.text = config.repositoryUrl;
        _sourceAuthorID.text = "";

        _sourcePlatformUrl.text = config.platformUrl ?: "";
        if(!config.platformUrl.isNullOrEmpty())
            _sourcePlatformUrlContainer.visibility = VISIBLE;
        else
            _sourcePlatformUrlContainer.visibility = GONE;

        if(config.authorUrl.isNotEmpty())
            _sourceBy.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
        else
            _sourceBy.setTextColor(Color.WHITE);

        if (!config.scriptPublicKey.isNullOrEmpty() && !config.scriptSignature.isNullOrEmpty()) {
            if (script == null) {
                _sourceSignature.setTextColor(Color.rgb(0xAC, 0xAC, 0xAC));
                _sourceSignature.text = context.getString(R.string.script_is_not_available);
            } else  if (config.validate(script)) {
                _sourceSignature.setTextColor(Color.rgb(0, 255, 0));
                _sourceSignature.text = context.getString(R.string.signature_is_valid);
            } else {
                _sourceSignature.setTextColor(Color.rgb(255, 0, 0));
                _sourceSignature.text = context.getString(R.string.signature_is_invalid);
            }
        } else {
            _sourceSignature.setTextColor(Color.rgb(255, 0, 0));
            _sourceSignature.text = context.getString(R.string.no_signature_available);
        }
    }

    fun clear() {
        _config = null;
        _sourceTitle.text = "";
        _sourceBy.text = ""
        _sourceDescription.text = "";
        _sourceVersion.text = "";
        _sourceScriptUrl.text = "";
        _sourceRepositoryUrl.text = "";
        _sourceAuthorID.text = "";
        _sourcePlatformUrl.text = "";
        _sourcePlatformUrlContainer.visibility = GONE;
    }
}