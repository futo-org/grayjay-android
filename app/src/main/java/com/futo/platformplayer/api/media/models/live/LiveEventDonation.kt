package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class LiveEventDonation: IPlatformLiveEvent, ILiveEventChatMessage {
    override val type: LiveEventType = LiveEventType.DONATION;

    private val _creationTimestamp = System.currentTimeMillis();
    private var _hasExpired = false;

    override val name: String;
    override val thumbnail: String?;
    override val message: String;
    val amount: String;
    val colorDonation: String?;

    var expire: Int = 6000;


    constructor(name: String, thumbnail: String?, message: String, amount: String, expire: Int = 6000, colorDonation: String? = null) {
        this.name = name;
        this.message = message;
        this.thumbnail = thumbnail;
        this.amount = amount;
        this.expire = expire;
        this.colorDonation = colorDonation;
    }

    fun hasExpired(): Boolean {
        _hasExpired = _hasExpired || (System.currentTimeMillis() - _creationTimestamp) > expire;
        return _hasExpired;
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventDonation {
            obj.ensureIsBusy();
            val contextName = "LiveEventDonation"
            return LiveEventDonation(
                obj.getOrThrow(config, "name", contextName),
                obj.getOrThrow(config, "thumbnail", contextName, true),
                obj.getOrThrow(config, "message", contextName),
                obj.getOrThrow(config, "amount", contextName),
                obj.getOrDefault(config, "expire", contextName, 6000) ?: 6000,
                obj.getOrDefault<String?>(config, "colorDonation", contextName, null));
        }
    }
}