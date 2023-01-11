package com.google.firebase.authexchange.interop

/**
 * A functional interface that listens for changes to the Auth Exchange token. This listener is
 * intended for use only by other Firebase SDKs.
 */
fun interface InternalAuthExchangeListener {
  /**
   * This method is triggered when there are changes to the Auth Exchange token, for example a new
   * token is received, or the Auth Exchange state is cleared.
   */
  fun onTokenChanged(token: String)
}
