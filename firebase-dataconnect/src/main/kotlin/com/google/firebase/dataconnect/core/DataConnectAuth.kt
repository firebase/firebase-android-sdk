/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.core

import com.google.firebase.annotations.DeferredApi
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.nextSequenceNumber
import com.google.firebase.inject.Deferred.DeferredHandler
import com.google.firebase.inject.Provider
import com.google.firebase.internal.InternalTokenResult
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import com.google.firebase.util.nextAlphanumericString
import java.lang.ref.WeakReference
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private typealias DeferredInternalAuthProvider =
  com.google.firebase.inject.Deferred<InternalAuthProvider>

internal class DataConnectAuth(
  deferredAuthProvider: DeferredInternalAuthProvider,
  parentCoroutineScope: CoroutineScope,
  private val blockingDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) {
  val instanceId: String
    get() = logger.nameWithId
  private val weakThis = WeakReference(this)

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob(parentCoroutineScope.coroutineContext[Job]) +
        blockingDispatcher +
        CoroutineName(instanceId) +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]}: $throwable"
          }
        }
    )

  private val idTokenListener = IdTokenListenerImpl(logger)

  private val mutex = Mutex()
  private var closed = false
  private var authProvider: InternalAuthProvider? = null
  private var getAccessTokenJob: Deferred<SequencedReference<GetAccessTokenResult>>? = null
  private var forceAccessTokenRefreshSequenceNumber: Long? = null

  init {
    // Call `whenAvailable()` on a non-main thread because it accesses SharedPreferences, which
    // performs disk i/o, violating the StrictMode policy android.os.strictmode.DiskReadViolation.
    val coroutineName = CoroutineName("$instanceId k6rwgqg9gh deferredAuthProvider.whenAvailable")
    coroutineScope.launch(blockingDispatcher + coroutineName) {
      deferredAuthProvider.whenAvailable(DeferredInternalAuthProviderHandlerImpl(weakThis))
    }
  }

  suspend fun close() {
    logger.debug { "close()" }

    weakThis.clear()
    coroutineScope.cancel()

    withContext(NonCancellable) {
      mutex.withLock {
        closed = true
        authProvider?.runIgnoringFirebaseAppDeleted { removeIdTokenListener(idTokenListener) }
        authProvider = null
      }
    }
  }

  suspend fun forceRefresh() {
    val sequenceNumber = nextSequenceNumber()
    logger.debug { "forceRefresh() sequenceNumber=$sequenceNumber" }
    mutex.withLock { forceAccessTokenRefreshSequenceNumber = sequenceNumber }
  }

  private sealed interface GetAccessTokenResult {
    object FirebaseAuthNotAvailable : GetAccessTokenResult
    object NoSignedInUser : GetAccessTokenResult
    object ForceRefreshRequested : GetAccessTokenResult
    data class Error(val exception: Throwable) : GetAccessTokenResult
    data class Success(val token: String?) : GetAccessTokenResult
  }

  suspend fun getAccessToken(requestId: String): String? {
    val invocationId = "gat" + Random.nextAlphanumericString(length = 8)
    logger.debug { "$invocationId getAccessToken(requestId=$requestId)" }
    while (true) {
      val attemptSequenceNumber = nextSequenceNumber()
      val oldJob =
        mutex.withLock {
          if (closed) {
            logger.debug {
              "$invocationId getAccessToken() throws DataConnectAuthClosedException" +
                " because the DataConnectAuth instance has been closed"
            }
            throw DataConnectAuthClosedException()
          }
          getAccessTokenJob
        }

      val job =
        if (oldJob !== null && oldJob.isActive) {
          oldJob
        } else {
          val coroutineName =
            CoroutineName(
              "$instanceId 535gmcvv5a" +
                "invocationId=$invocationId getAccessTokenFromAuthProvider()"
            )
          val newJob =
            coroutineScope.async(coroutineName, CoroutineStart.LAZY) {
              val sequenceNumber = nextSequenceNumber()
              SequencedReference(sequenceNumber, getAccessTokenFromAuthProvider())
            }
          val activeJob =
            mutex.withLock {
              val currentJob = getAccessTokenJob
              if (currentJob !== null && currentJob !== oldJob) {
                currentJob
              } else {
                newJob.also {
                  getAccessTokenJob = it
                  it.start()
                }
              }
            }
          activeJob
        }

      val jobResult = job.runCatching { await() }
      jobResult.onFailure { exception ->
        if (exception is CancellationException) {
          logger.debug {
            "$invocationId getAccessToken() throws DataConnectAuthClosedException" +
              " (DataConnectAuth was closed during token fetch)"
          }
          throw DataConnectAuthClosedException()
        }
      }

      val sequencedResult = jobResult.getOrThrow()

      if (sequencedResult.sequenceNumber < attemptSequenceNumber) {
        logger.debug { "$invocationId getAccessToken() got an old result; retrying" }
        continue
      }

      return when (val getAccessTokenResult = sequencedResult.ref) {
        is GetAccessTokenResult.Success -> {
          val token = getAccessTokenResult.token
          logger.debug {
            "$invocationId getAccessToken() returns ${token?.toScrubbedAccessToken()}"
          }
          token
        }
        is GetAccessTokenResult.Error -> {
          val exception = getAccessTokenResult.exception
          logger.warn(exception) {
            "$invocationId getAccessToken() re-throws exception from FirebaseAuth"
          }
          throw exception
        }
        is GetAccessTokenResult.NoSignedInUser -> {
          logger.debug {
            "$invocationId getAccessToken() returns null" +
              " (FirebaseAuth reports no signed-in user)"
          }
          null
        }
        is GetAccessTokenResult.FirebaseAuthNotAvailable -> {
          logger.debug {
            "$invocationId getAccessToken() returns null" +
              " (FirebaseAuth is not (yet?) available)"
          }
          null
        }
        is GetAccessTokenResult.ForceRefreshRequested -> {
          logger.debug { "$invocationId getAccessToken() force refresh requested; retrying" }
          continue
        }
      }
    }
  }

  private suspend fun getAccessTokenFromAuthProvider(): GetAccessTokenResult {
    val (capturedAuthProvider, capturedForceRefreshSequenceNumber) =
      mutex.withLock { Pair(authProvider, forceAccessTokenRefreshSequenceNumber) }
    if (capturedAuthProvider === null) {
      return GetAccessTokenResult.FirebaseAuthNotAvailable
    }

    val forceRefresh = capturedForceRefreshSequenceNumber !== null
    logger.debug { "Calling capturedAuthProvider.getAccessToken(forceRefresh=$forceRefresh)" }
    val result = capturedAuthProvider.runCatching { getAccessToken(forceRefresh).await() }
    result.onFailure { if (it is CancellationException) throw it }

    mutex.withLock {
      if (forceAccessTokenRefreshSequenceNumber != capturedForceRefreshSequenceNumber) {
        return GetAccessTokenResult.ForceRefreshRequested
      }
      forceAccessTokenRefreshSequenceNumber = null
    }

    result.onFailure {
      if (it is FirebaseNoSignedInUserException) return GetAccessTokenResult.NoSignedInUser
    }

    val token =
      result.fold(
        onSuccess = { it.token },
        onFailure = {
          return GetAccessTokenResult.Error(it)
        },
      )

    return GetAccessTokenResult.Success(token)
  }

  @DeferredApi
  private fun onInternalAuthProviderAvailable(newAuthProvider: InternalAuthProvider) {
    // Call addIdTokenListener() synchronously, even though this object _may_ already be closed
    // because addIdTokenListener() _must_ be called from a whenAvailable() callback. We _could_
    // have acquired the lock on `mutex` but that would potentially block the calling thread, which
    // is likely undesirable. Instead, just add the listener and then immediately launch a task
    // to remove it if we were closed.
    val invocationId = "oiapa" + Random.nextAlphanumericString(length = 8)
    logger.debug {
      "$invocationId onInternalAuthProviderAvailable(newAuthProvider=$newAuthProvider)"
    }
    newAuthProvider.runIgnoringFirebaseAppDeleted { addIdTokenListener(idTokenListener) }

    // Launch a coroutine to remove the listener that was just added in the case that this object
    // is closed. Use `GlobalScope` instead of `this.coroutineScope` because `this.coroutineScope`
    // will get cancelled if this object is closed.
    val coroutineName = CoroutineName("$instanceId trmgdd3n65 onInternalAuthProviderAvailable()")
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(blockingDispatcher + coroutineName) {
      val shouldRemoveIdTokenListener =
        mutex.withLock {
          if (closed) {
            true
          } else {
            authProvider = newAuthProvider
            false
          }
        }

      if (shouldRemoveIdTokenListener) {
        logger.debug {
          "$invocationId unregistering IdTokenListener that was just added" +
            " because the DataConnectAuth instance was closed asynchronously"
        }
        newAuthProvider.runIgnoringFirebaseAppDeleted { removeIdTokenListener(idTokenListener) }
      }
    }
  }

  private class IdTokenListenerImpl(private val logger: Logger) : IdTokenListener {
    private val idToken = MutableStateFlow(SequencedReference<String?>(nextSequenceNumber(), null))

    override fun onIdTokenChanged(tokenResult: InternalTokenResult) {
      val invocationId = "oitc" + Random.nextAlphanumericString(length = 8)
      val newIdToken = SequencedReference(nextSequenceNumber(), tokenResult.token)
      logger.debug {
        "$invocationId onIdTokenChanged(tokenResult=${newIdToken.ref?.toScrubbedAccessToken()})" +
          " sequenceNumber=${newIdToken.sequenceNumber}"
      }

      while (true) {
        val oldIdToken = idToken.value
        if (oldIdToken.sequenceNumber > newIdToken.sequenceNumber) {
          logger.debug {
            "$invocationId onIdTokenChanged() token dropped because its sequenceNumber" +
              " (${newIdToken.sequenceNumber}) is less than the latest" +
              " (${oldIdToken.sequenceNumber})"
          }
          break
        }
        if (idToken.compareAndSet(oldIdToken, newIdToken)) {
          break
        }
      }
    }
  }

  /**
   * An implementation of [DeferredHandler] to be registered with the [DeferredInternalAuthProvider]
   * that will call back into the [DataConnectAuth].
   *
   * This separate class is used (as opposed to using a more-convenient lambda) to avoid holding a
   * strong reference to the [DataConnectAuth] instance indefinitely, in the case that the callback
   * never occurs.
   */
  private class DeferredInternalAuthProviderHandlerImpl(
    private val weakDataConnectAuthRef: WeakReference<DataConnectAuth>
  ) : DeferredHandler<InternalAuthProvider> {
    override fun handle(provider: Provider<InternalAuthProvider>) {
      weakDataConnectAuthRef.get()?.onInternalAuthProviderAvailable(provider.get())
    }
  }

  private class DataConnectAuthClosedException : DataConnectException("DataConnectAuth was closed")

  // Work around a race condition where addIdTokenListener() and removeIdTokenListener() throw if
  // the FirebaseApp is deleted during or before its invocation.
  private fun InternalAuthProvider.runIgnoringFirebaseAppDeleted(
    block: InternalAuthProvider.() -> Unit
  ) {
    try {
      block()
    } catch (e: IllegalStateException) {
      if (e.message == "FirebaseApp was deleted") {
        logger.warn(e) { "ignoring exception: $e" }
      } else {
        throw e
      }
    }
  }
}

/**
 * Returns a new string that is equal to this string but only includes a chunk from the beginning
 * and the end.
 *
 * This method assumes that the contents of this string are an access token. The returned string
 * will have enough information to reason about the access token in logs without giving its value
 * away.
 */
internal fun String.toScrubbedAccessToken(): String =
  if (length < 30) {
    "<redacted>"
  } else {
    buildString {
      append(this@toScrubbedAccessToken, 0, 6)
      append("<redacted>")
      append(
        this@toScrubbedAccessToken,
        this@toScrubbedAccessToken.length - 6,
        this@toScrubbedAccessToken.length
      )
    }
  }
