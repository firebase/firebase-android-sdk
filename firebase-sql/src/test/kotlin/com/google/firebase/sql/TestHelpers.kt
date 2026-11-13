/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql

import com.google.android.gms.tasks.Task
import com.google.firebase.appcheck.AppCheckTokenResult
import com.google.firebase.appcheck.interop.AppCheckTokenListener
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Returns a [Deferred] that is already available, like an SDK that the app links. */
internal fun <T : Any> deferred(value: T): Deferred<T> = Deferred { it.handle { value } }

/** Returns an [InternalAuthProvider] whose `getAccessToken()` returns [token]. */
internal fun authProvider(token: Task<GetTokenResult>): InternalAuthProvider =
  object : InternalAuthProvider {
    override fun getAccessToken(forceRefresh: Boolean) = token
    override fun getUid(): String? = null
    override fun addIdTokenListener(listener: IdTokenListener) {}
    override fun removeIdTokenListener(listener: IdTokenListener) {}
  }

/** Returns an [InteropAppCheckTokenProvider] whose `getToken()` returns [result]. */
internal fun appCheckProvider(result: Task<AppCheckTokenResult>): InteropAppCheckTokenProvider =
  object : InteropAppCheckTokenProvider {
    override fun getToken(forceRefresh: Boolean) = result
    override fun getLimitedUseToken() = result
    override fun addAppCheckTokenListener(listener: AppCheckTokenListener) {}
    override fun removeAppCheckTokenListener(listener: AppCheckTokenListener) {}
  }

/** Returns an [AppCheckTokenResult] with [token] and [error]. */
internal fun appCheckResult(token: String, error: Exception?): AppCheckTokenResult =
  object : AppCheckTokenResult() {
    override fun getToken() = token
    override fun getError() = error
  }

/** Returns the values of all the headers named [name], ignoring the case of the names. */
internal fun Map<String, String>.valuesOf(name: String): List<String> =
  filterKeys { it.equals(name, ignoreCase = true) }.values.toList()

/** Returns the next request that the server received, and fails if none arrives in 5 seconds. */
internal fun MockWebServer.takeRequestOrFail(): RecordedRequest =
  checkNotNull(takeRequest(5, TimeUnit.SECONDS)) { "The server received no request." }

/**
 * Returns the constants of a class that keeps them in its companion object, such as [Count], by
 * name, in the order of their declaration.
 */
internal inline fun <reified T : Any> constantsOf(): Map<String, T> =
  T::class
    .java
    .declaredFields
    .filter { Modifier.isStatic(it.modifiers) && it.type == T::class.java }
    .associate { field ->
      field.isAccessible = true
      field.name to field.get(null) as T
    }
