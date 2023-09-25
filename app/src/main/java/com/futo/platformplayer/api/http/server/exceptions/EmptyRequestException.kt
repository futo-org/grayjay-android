package com.futo.platformplayer.api.http.server.exceptions

import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class EmptyRequestException(msg: String) : Exception(msg) {}