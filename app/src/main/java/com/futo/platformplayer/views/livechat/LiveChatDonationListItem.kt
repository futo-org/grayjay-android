package com.futo.platformplayer.views.livechat

import CSSColor
import android.graphics.Color
import android.graphics.drawable.LevelListDrawable
import android.text.Spannable
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.live.ILiveEventChatMessage
import com.futo.platformplayer.api.media.models.live.LiveEventComment
import com.futo.platformplayer.api.media.models.live.LiveEventDonation
import com.futo.platformplayer.dp
import com.futo.platformplayer.isHexColor
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.views.overlays.LiveChatOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import toAndroidColor

class LiveChatDonationListItem(viewGroup: ViewGroup)
    : LiveChatListItem(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_chat_donation, viewGroup, false)) {
    private var _liveEvent: ILiveEventChatMessage? = null;

    private val _authorImage: ImageView = _view.findViewById(R.id.image_thumbnail);
    private val _authorName: TextView = _view.findViewById(R.id.text_author);
    private val _authorMessage: TextView = _view.findViewById(R.id.text_body);

    private val _amountContainer: LinearLayout = _view.findViewById(R.id.donation_amount_container);
    private val _amount: TextView = _view.findViewById(R.id.donation_amount);

    override fun bind(chat: LiveChatOverlay.ChatMessage) {
        val event = chat.event;

        _liveEvent = event;
        if(event.thumbnail.isNullOrEmpty())
            _authorImage.visibility = View.GONE;
        else {
            Glide.with(_authorImage)
                .load(event.thumbnail)
                .into(_authorImage);
            _authorImage.visibility = View.VISIBLE;
        }
        _authorName.text = event.name;

        if(event is LiveEventDonation) {
            _amountContainer.visibility = View.VISIBLE;
            _amount.text = event.amount.trim();

            if(event.colorDonation != null && event.colorDonation.isHexColor()) {
                val color = CSSColor.parseColor(event.colorDonation);
                _amountContainer.background.setTint(color.toAndroidColor());

                if(color.lightness > 0.5)
                    _amount.setTextColor(Color.BLACK);
                else
                    _amount.setTextColor(Color.WHITE);
            }
            else {
                _amountContainer.background.setTint(Color.parseColor("#2A2A2A"));
                _amount.setTextColor(Color.WHITE);
            }
        }
        else
            _amountContainer.visibility = View.GONE;

        //Injects emotes
        if(!chat.manager.let { liveChat ->
                val emojiMatches = REGEX_EMOJIS.findAll(event.message).toList();
                val span = _spanFactory.newSpannable(event.message);
                var injected = 0;

                for(emoji in emojiMatches
                        .filter { it.groupValues.size > 1 && liveChat.hasEmoji(it.groupValues[1]) }
                        .groupBy { it.groupValues[1] }) {
                    val emojiVal = emoji.key;
                    val drawable = LevelListDrawable();

                    for(match in emoji.value)
                        span.setSpan(ImageSpan(drawable), match.range.first, match.range.last + 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                    liveChat.getEmoji(emojiVal) { emojiDrawable ->
                        if(emojiDrawable != null) {
                            drawable.addLevel(1, 1, emojiDrawable);
                            val iconSize = 20.dp(_view.resources);
                            drawable.setBounds(0, 0, iconSize, iconSize);
                            drawable.setLevel(1);
                            if (_liveEvent == event)
                                chat.scope.launch(Dispatchers.Main) {
                                    _authorMessage.setText(span, TextView.BufferType.SPANNABLE);
                                }
                        }
                    };
                    injected++;
                }
                if(injected > 0) {
                    _authorMessage.setText(span, TextView.BufferType.SPANNABLE);
                    return@let true;
                } else
                    return@let false;
            })
            _authorMessage.text = event.message;
    }


    companion object {
        val REGEX_EMOJIS = Regex("__(.*?)__");
        private val _spanFactory = Spannable.Factory.getInstance();
    }
}