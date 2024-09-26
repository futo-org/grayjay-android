package com.futo.platformplayer.views.sync

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.sync.LinkType

class SyncDeviceView : ConstraintLayout {
    val _imageLinkType: ImageView
    val _textLinkType: TextView
    val _imageClear: ImageView
    val _textName: TextView
    val _textStatus: TextView
    val _layoutLinkType: LinearLayout
    val onRemove: Event0 = Event0()

    constructor(context: Context, attributeSet: AttributeSet? = null) : super(context) {
        inflate(context, R.layout.view_sync, this)

        _imageLinkType = findViewById(R.id.image_link_type)
        _textLinkType = findViewById(R.id.text_link_type)
        _imageClear = findViewById(R.id.image_clear)
        _textName = findViewById(R.id.text_name)
        _textStatus = findViewById(R.id.text_status)
        _layoutLinkType = findViewById(R.id.layout_link_type)

        _imageClear.setOnClickListener {
            onRemove.emit()
        }
    }

    fun setLinkType(linkType: LinkType): SyncDeviceView {
        if (linkType == LinkType.None) {
            _layoutLinkType.visibility = View.GONE
            return this
        }

        _layoutLinkType.visibility = View.VISIBLE
        _imageLinkType.setImageResource(when (linkType) {
            LinkType.Proxied -> R.drawable.ic_internet
            LinkType.Local -> R.drawable.ic_lan
            else -> 0
        })
        _textLinkType.text = when(linkType) {
            LinkType.Proxied -> "Proxied"
            LinkType.Local -> "Local"
            else -> null
        }

        return this
    }

    fun setName(name: String): SyncDeviceView {
        _textName.text = name
        return this
    }

    fun setStatus(status: String): SyncDeviceView {
        _textStatus.text = status
        return this
    }
}