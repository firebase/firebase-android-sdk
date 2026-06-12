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

import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.interop.AppCheckTokenListener
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import io.grpc.Metadata
import java.util.concurrent.atomic.AtomicReference

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

abstract class BaseInternalAuthProvider : InternalAuthProvider {

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
  override fun getAccessToken(forceRefresh: Boolean) =
    Tasks.forResult(com.google.firebase.auth.GetTokenResult(null, emptyMap()))

  override fun getUid() = null

  override fun toString() = "NotLoggedInInternalAuthProvider"
}

class LoggedInInternalAuthProvider(val token: String, uid: String) : BaseInternalAuthProvider() {

  private val _uid = uid

  override fun getAccessToken(forceRefresh: Boolean) =
    Tasks.forResult(com.google.firebase.auth.GetTokenResult(token, mapOf("sub" to uid)))

  override fun getUid() = _uid

  override fun toString() = "LoggedInInternalAuthProvider(token=$token, uid=$_uid)"
}

class TestInteropAppCheckTokenProvider(val token: String) : InteropAppCheckTokenProvider {

  private val _appCheckTokenListener = AtomicReference<AppCheckTokenListener>(null)
  val appCheckTokenListener: AppCheckTokenListener?
    get() = _appCheckTokenListener.get()

  override fun getToken(forceRefresh: Boolean) =
    Tasks.forResult<com.google.firebase.appcheck.AppCheckTokenResult>(
      TestAppCheckTokenResultImpl(token)
    )

  override fun getLimitedUseToken() = TODO("not implemented")

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

  override fun toString() = "TestInteropAppCheckTokenProvider(token=$token)"
}
