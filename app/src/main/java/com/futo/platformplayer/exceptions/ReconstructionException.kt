package com.futo.platformplayer.exceptions

class ReconstructionException(val name: String? = null, message: String, innerException: Throwable): Exception(message, innerException) {

}