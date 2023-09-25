package com.futo.platformplayer.views.livechat

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.live.LiveEventDonation
import com.futo.platformplayer.isHexColor

class LiveChatDonationPill: LinearLayout {
    private val _imageAuthor: ImageView;
    private val _textAmount: TextView;

    private val _expireBar: View;

    constructor(context: Context, donation: LiveEventDonation) : super(context) {
        inflate(context, R.layout.list_donation, this)
        _imageAuthor = findViewById(R.id.donation_author_image);
        _textAmount = findViewById(R.id.donation_amount)

        _textAmount.text = donation.amount;
        _expireBar = findViewById(R.id.expire_bar);
        _textAmount.text = donation.amount;

        val root = findViewById<LinearLayout>(R.id.root);


        if(donation.colorDonation != null && donation.colorDonation.isHexColor()) {
            val color = Color.parseColor(donation.colorDonation);
            root.background.setTint(color);

            if((color.green > 140 || color.red > 140 || color.blue > 140) && (color.red + color.green + color.blue) > 400)
                _textAmount.setTextColor(Color.BLACK);
            else
                _textAmount.setTextColor(Color.WHITE);
        }
        else {
            root.background.setTint(Color.parseColor("#2A2A2A"));
            _textAmount.setTextColor(Color.WHITE);
        }

        if(donation.thumbnail.isNullOrEmpty())
            _imageAuthor.visibility = View.GONE;
        else
            Glide.with(_imageAuthor)
                .load(donation.thumbnail)
                .circleCrop()
                .into(_imageAuthor);
    }

    fun animateExpire(ms: Int) {
        _expireBar.scaleX = 1f;
        _expireBar.animate()
            .scaleX(0f)
            .translationXBy(-1f)
            .setDuration(ms.toLong() + 500)
            .start();
    }
}