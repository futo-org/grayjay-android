package com.futo.platformplayer.sabr

import java.io.IOException

open class SabrException(message: String, cause: Throwable? = null) : IOException(message, cause)

class SabrBlockedException(message: String) : SabrException(message)

class SabrReloadRequiredException(message: String) : SabrException(message)

class SabrFormatSubstitutedException(message: String) : SabrException(message)

class CastSupersededException(message: String) : Exception(message)
