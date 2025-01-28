package com.google.firebase.vertexai.internal.util

import android.util.Log
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.vertexai.common.HeaderProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.tasks.await

internal class AppCheckHeaderProvider(
  val logTag: String,
  val appCheckTokenProvider: InteropAppCheckTokenProvider? = null,
  val internalAuthProvider: InternalAuthProvider? = null,
) : HeaderProvider {
  override val timeout: Duration
    get() = 10.seconds

  override suspend fun generateHeaders(): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    if (appCheckTokenProvider == null) {
      Log.w(logTag, "AppCheck not registered, skipping")
    } else {
      val token = appCheckTokenProvider.getToken(false).await()

      if (token.error != null) {
        Log.w(logTag, "Error obtaining AppCheck token", token.error)
      }
      // The Firebase App Check backend can differentiate between apps without App Check, and
      // wrongly configured apps by verifying the value of the token, so it always needs to be
      // included.
      headers["X-Firebase-AppCheck"] = token.token
    }

    if (internalAuthProvider == null) {
      Log.w(logTag, "Auth not registered, skipping")
    } else {
      try {
        val token = internalAuthProvider.getAccessToken(false).await()

        headers["Authorization"] = "Firebase ${token.token!!}"
      } catch (e: Exception) {
        Log.w(logTag, "Error getting Auth token ", e)
      }
    }

    return headers
  }
}
