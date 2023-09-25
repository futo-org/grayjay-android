package com.futo.platformplayer.api.media.exceptions.search

class NoNextPageException(s: String? = null) : IllegalStateException("No next page available:$s") {

}