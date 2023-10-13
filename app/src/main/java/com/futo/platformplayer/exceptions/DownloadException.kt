package com.futo.platformplayer.exceptions

class DownloadException : Throwable {
    val isRetryable: Boolean;

    constructor(innerException: Throwable, retryable: Boolean = true): super(innerException) {
        isRetryable = retryable;
    }
    constructor(msg: String, retryable: Boolean = true): super(msg) {
        isRetryable = retryable;
    }
}