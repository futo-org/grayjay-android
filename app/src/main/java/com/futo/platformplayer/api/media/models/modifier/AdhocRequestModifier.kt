package com.futo.platformplayer.api.media.models.modifier

class AdhocRequestModifier: IRequestModifier {
    val _handler: (String, Map<String,String>)->IRequest;
    override var allowByteSkip: Boolean = false;

    constructor(modifyReq: (String, Map<String,String>)->IRequest) {
        _handler = modifyReq;
    }

    override fun modifyRequest(url: String, headers: Map<String, String>): IRequest {
        return _handler(url, headers);
    }
}