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

package com.google.firebase.dataconnect.testutil

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.interop.AppCheckTokenListener
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.internal.InternalTokenResult
import io.grpc.Metadata
import io.kotest.assertions.print.print
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// This is a copy of the function of the same name in DataConnectCredentialsTokenManager.kt.
fun String.toScrubbedAccessToken(): String =
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

const val PLACEHOLDER_APP_CHECK_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="

val authTokenGrpcMetadataKey: Metadata.Key<String> =
  Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)

val appCheckTokenGrpcMetadataKey: Metadata.Key<String> =
  Metadata.Key.of("x-firebase-appcheck", Metadata.ASCII_STRING_MARSHALLER)

class TestAppCheckTokenResultImpl(
  private val token: String,
  private val error: Exception? = null,
) : com.google.firebase.appcheck.AppCheckTokenResult() {
  override fun getToken() = token
  override fun getError() = error

  override fun toString() = "TestAppCheckTokenResultImpl(token=$token, error=$error)"
}

fun authTokenResultFor(token: String, uid: String): com.google.firebase.auth.GetTokenResult =
  com.google.firebase.auth.GetTokenResult(token, mapOf("sub" to uid))

fun authTokenResultFor(
  @Suppress("unused") token: Nothing?,
  @Suppress("unused") uid: Nothing?,
): com.google.firebase.auth.GetTokenResult =
  com.google.firebase.auth.GetTokenResult(null, emptyMap())

abstract class BaseInternalAuthProvider : InternalAuthProvider {

  override fun getAccessToken(
    forceRefresh: Boolean
  ): Task<com.google.firebase.auth.GetTokenResult> {
    val result = getAccessTokenImpl(forceRefresh)
    return Tasks.forResult(result)
  }

  protected abstract fun getAccessTokenImpl(
    forceRefresh: Boolean
  ): com.google.firebase.auth.GetTokenResult

  private val _idTokenListener = AtomicReference<IdTokenListener>(null)
  val idTokenListener: IdTokenListener?
    get() = _idTokenListener.get()

  override fun addIdTokenListener(listener: IdTokenListener) {
    _idTokenListener.compareAndSet(null, listener).let {
      check(it) { "addIdTokenListener() has already set a listener [q9zs84tavg]" }
    }
  }

  override fun removeIdTokenListener(listener: IdTokenListener) {
    _idTokenListener.compareAndSet(listener, null).let {
      check(it) {
        "removeIdTokenListener() is trying to remove a listener that was not set [z37yytx6ck]"
      }
    }
  }
}

object NotLoggedInInternalAuthProvider : BaseInternalAuthProvider() {
  override fun getAccessTokenImpl(forceRefresh: Boolean) = authTokenResultFor(null, null)

  override fun getUid() = null

  override fun toString() = "NotLoggedInInternalAuthProvider"
}

class LoggedInInternalAuthProvider(val token: String, uid: String) : BaseInternalAuthProvider() {

  private val _uid = uid

  override fun getAccessTokenImpl(forceRefresh: Boolean) =
    authTokenResultFor(token = token, uid = uid)

  override fun getUid() = _uid

  override fun toString() = "LoggedInInternalAuthProvider(token=$token, uid=$_uid)"
}

class LoggedInMultiTokenInternalAuthProvider(tokens: List<String>, uid: String) :
  BaseInternalAuthProvider() {

  val tokens = tokens.toList()
  private val lock = ReentrantLock()
  private val iterator = this.tokens.iterator()

  private val _uid = uid

  override fun getAccessTokenImpl(forceRefresh: Boolean) =
    authTokenResultFor(token = nextToken(), uid = uid)

  private fun nextToken(): String =
    lock.withLock {
      check(iterator.hasNext()) { "internal error p37nr2p9dk: no more Auth tokens to produce" }
      iterator.next()
    }

  override fun getUid() = _uid

  override fun toString() =
    "LoggedInInternalAuthProvider(tokens=${tokens.print().value}, uid=$_uid)"
}

class LoggedInMultiTokenAndUidAuthProvider(tokenUidPairs: List<TokenUidPair?>) :
  BaseInternalAuthProvider() {

  init {
    require(tokenUidPairs.isNotEmpty()) { "tokenUidPairs must not be empty [jg5879zfs5]" }
  }

  val tokenUidPairs = tokenUidPairs.toList()
  private val index = AtomicInteger(0)

  override fun getAccessTokenImpl(forceRefresh: Boolean) =
    when (val pair = nextTokenUidPair()) {
      null -> authTokenResultFor(null, null)
      else -> authTokenResultFor(token = pair.token, uid = pair.uid)
    }

  private fun nextTokenUidPair(): TokenUidPair? {
    while (true) {
      val currentIndex = index.get()
      check(currentIndex + 1 <= tokenUidPairs.size) {
        "internal error k7j9d4dzbk: no more Auth token/uid pairs to produce"
      }
      if (index.compareAndSet(currentIndex, currentIndex + 1)) {
        return tokenUidPairs[currentIndex]
      }
    }
  }

  override fun getUid() = tokenUidPairs[index.get().coerceAtLeast(1) - 1]?.uid

  override fun toString() =
    "LoggedInMultiTokenAndUidAuthProvider(tokenUidPairs=${tokenUidPairs.print().value})"
}

data class TokenUidPair(val token: String, val uid: String)

fun TokenUidPair?.toAuthTokenResult(): com.google.firebase.auth.GetTokenResult =
  when (this) {
    null -> authTokenResultFor(null, null)
    else -> authTokenResultFor(token = token, uid = uid)
  }

fun TokenUidPair?.toInternalTokenResult(): InternalTokenResult = InternalTokenResult(this?.token)

fun tokenUidPairOrNullIfUidNull(token: String, uid: String?): TokenUidPair? =
  if (uid == null) null else TokenUidPair(token = token, uid = uid)

abstract class BaseInteropAppCheckTokenProvider : InteropAppCheckTokenProvider {

  private val _appCheckTokenListener = AtomicReference<AppCheckTokenListener>(null)
  val appCheckTokenListener: AppCheckTokenListener?
    get() = _appCheckTokenListener.get()

  override fun addAppCheckTokenListener(listener: AppCheckTokenListener) {
    _appCheckTokenListener.compareAndSet(null, listener).let {
      check(it) { "addAppCheckTokenListener() has already set a listener [dg9chc7wbd]" }
    }
  }

  override fun removeAppCheckTokenListener(listener: AppCheckTokenListener) {
    _appCheckTokenListener.compareAndSet(listener, null).let {
      check(it) {
        "removeAppCheckTokenListener() is trying to remove a listener that was not set [hydewbz35a]"
      }
    }
  }
}

class TestInteropAppCheckTokenProvider(val token: String) : BaseInteropAppCheckTokenProvider() {

  override fun getToken(forceRefresh: Boolean) =
    Tasks.forResult<com.google.firebase.appcheck.AppCheckTokenResult>(
      TestAppCheckTokenResultImpl(token)
    )

  override fun getLimitedUseToken() = TODO("not implemented")

  override fun toString() = "TestInteropAppCheckTokenProvider(token=$token)"
}

class TestMultiTokenInteropAppCheckTokenProvider(tokens: List<String>) :
  BaseInteropAppCheckTokenProvider() {

  val tokens = tokens.toList()
  private val lock = ReentrantLock()
  private val iterator = this.tokens.iterator()

  override fun getToken(forceRefresh: Boolean) =
    Tasks.forResult<com.google.firebase.appcheck.AppCheckTokenResult>(
      TestAppCheckTokenResultImpl(nextToken())
    )

  private fun nextToken(): String =
    lock.withLock {
      check(iterator.hasNext()) { "internal error jkgvfsmktg: no more App Check tokens to produce" }
      iterator.next()
    }

  override fun getLimitedUseToken() = TODO("not implemented")

  override fun toString() =
    "TestMultiTokenInteropAppCheckTokenProvider(tokens=${tokens.print().value})"
}
