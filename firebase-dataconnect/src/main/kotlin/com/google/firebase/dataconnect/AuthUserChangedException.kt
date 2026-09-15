package com.google.firebase.dataconnect

/**
 * Thrown when a realtime streaming connection fails because the Firebase Auth user changed.
 *
 * The SDK will throw this exception if the active Firebase Auth user is changed, either by a new
 * user logging in or a logged-in user logging out while a realtime subscription flow is active.
 *
 * To automatically reconnect under the new auth credentials, the `retryWhen` operator can be used:
 *
 * ```
 * import kotlinx.coroutines.flow.retryWhen
 *
 * querySubscription
 *  .flow
 *  .retryWhen { cause, _ -> cause is AuthUserChangedException }
 *  .collect { println(it) }
 * ```
 */
public open class AuthUserChangedException(
  message: String,
  cause: Throwable? = null,
) : DataConnectException(message, cause)
