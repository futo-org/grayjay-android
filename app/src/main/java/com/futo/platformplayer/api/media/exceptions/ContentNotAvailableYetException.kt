package com.futo.platformplayer.api.media.exceptions

class ContentNotAvailableYetException(message: String?, val availableWhen: String) : Exception(message)  {

}