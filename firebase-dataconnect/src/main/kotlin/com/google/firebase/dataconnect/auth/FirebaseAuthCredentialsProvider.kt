package com.google.firebase.dataconnect.auth

import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.util.Listener
import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.tasks.await

public interface FirebaseAuthCredentialsProvider : CredentialsProvider<User>

/**
 * FirebaseAuthCredentialsProvider uses Firebase Auth via {@link FirebaseApp} to get an auth token.
 */
public fun FirebaseAuthCredentialsProvider(
  deferredProvider: Deferred<InternalAuthProvider>
): FirebaseAuthCredentialsProvider = FirebaseAuthCredentialsProviderImpl(deferredProvider)

internal class FirebaseAuthCredentialsProviderImpl(
  deferredProvider: Deferred<InternalAuthProvider>
) : FirebaseAuthCredentialsProvider {

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  private val singleThreadDispatcher =
    newSingleThreadContext("FirebaseAuthCredentialsProviderThread")

  /**
   * The {@link Provider} that gives access to the {@link InternalAuthProvider} instance; initially,
   * its {@link Provider#get} method returns {@code null}, but will be changed to a new {@link
   * Provider} once the "auth" module becomes available.
   */
  private lateinit var internalAuthProvider: InternalAuthProvider

  /**
   * The listener registered with FirebaseApp; used to stop receiving auth changes once
   * changeListener is removed.
   */
  private val idTokenListener = IdTokenListener { onIdTokenChanged() }

  /** The listener to be notified of credential changes (sign-in / sign-out, token changes). */
  private var changeListener: Listener<User>? = null

  private var forceRefresh = MutableStateFlow(false)

  /** Id used to detect if the token changed while a getToken request was outstanding. */
  private val tokenId = AtomicLong(0)

  /** Creates a new FirebaseAuthCredentialsProvider. */
  init {
    deferredProvider.whenAvailable { provider: Provider<InternalAuthProvider> ->
      runBlocking {
        withContext(singleThreadDispatcher) {
          internalAuthProvider = provider.get()
          internalAuthProvider.addIdTokenListener(idTokenListener)
        }
        onIdTokenChanged()
      }
    }
  }

  override suspend fun getToken(): String? {
    var isFinished = false
    var result: String? = null
    withContext(singleThreadDispatcher) {
      if (!this@FirebaseAuthCredentialsProviderImpl::internalAuthProvider.isInitialized) {
        // Auth is not available
        isFinished = true
        return@withContext
      }

      val currentForceRefresh = forceRefresh.getAndUpdate { false }
      val res = internalAuthProvider.getAccessToken(currentForceRefresh)

      val currentTokenId = tokenId.get()
      result =
        try {
          res.await().token
        } catch (_: Exception) {
          null
        }

      if (currentTokenId == tokenId.get()) {
        isFinished = true
      }
    }
    return if (isFinished) result else getToken()
  }

  override suspend fun invalidateToken() {
    forceRefresh.value = true
  }

  override suspend fun removeChangeListener() {
    withContext(singleThreadDispatcher) {
      changeListener = null
      if (this@FirebaseAuthCredentialsProviderImpl::internalAuthProvider.isInitialized) {
        internalAuthProvider.removeIdTokenListener(idTokenListener)
      }
    }
  }

  override suspend fun setChangeListener(changeListener: Listener<User>) {
    withContext(singleThreadDispatcher) {
      this@FirebaseAuthCredentialsProviderImpl.changeListener = changeListener
    }
    // Fire the initial event.
    changeListener.onValue(getUser())
  }

  /** Invoked when the auth token changes. */
  private fun onIdTokenChanged() {
    runBlocking {
      tokenId.incrementAndGet()
      if (changeListener != null) {
        changeListener!!.onValue(getUser())
      }
    }
  }

  /**
   * Returns the current {@link User} as obtained from the given InternalAuthProvider. This function
   * can only be called in functions which runs within singleThreadDispatcher.
   */
  private suspend fun getUser(): User {
    return withContext(singleThreadDispatcher) {
      val uid =
        if (!this@FirebaseAuthCredentialsProviderImpl::internalAuthProvider.isInitialized) {
          null
        } else {
          internalAuthProvider.uid
        }
      return@withContext if (uid != null) User(uid) else User.UNAUTHENTICATED
    }
  }
}
