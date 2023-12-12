package com.futo.platformplayer.api.media.models.modifier


interface IRequestModifier {
    var allowByteSkip: Boolean;
    fun modifyRequest(url: String, headers: Map<String, String>): IRequest
}