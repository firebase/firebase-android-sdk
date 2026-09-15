package com.google.firebase.dataconnect

public open class AuthUserChangedException(
    message: String,
    cause: Throwable? = null,
) : DataConnectException(message, cause)
