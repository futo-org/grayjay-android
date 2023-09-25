package com.futo.platformplayer.api.media.exceptions

class UnknownPlatformException(s : String) : IllegalArgumentException("Unknown platform type:$s") {
}