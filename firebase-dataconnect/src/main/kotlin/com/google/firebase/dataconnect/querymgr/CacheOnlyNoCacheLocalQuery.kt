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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.CachedDataNotFoundException
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.Logger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encoding.Decoder

internal class CacheOnlyNoCacheLocalQuery(
  logger: Logger,
) : LocalQuery<Nothing>(ThrowingCoroutineDispatcher, ThrowingDeserializer, null, logger) {

  override suspend fun executeImpl(
    requestId: String,
    sequenceNumber: Long,
    authToken: GetAuthTokenResult?,
    appCheckToken: GetAppCheckTokenResult?,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
  ): Nothing {
    throw CachedDataNotFoundException(
      "CACHE_ONLY fetch policy is unsupported when cache settings is null [m35wype9dt]"
    )
  }
}

private object ThrowingCoroutineDispatcher : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    throw UnsupportedOperationException("ThrowingCoroutineDispatcher does not support any methods")
  }
}

private object ThrowingDeserializer : DeserializationStrategy<Nothing> {
  override val descriptor
    get() = unsupported()

  override fun deserialize(decoder: Decoder) = unsupported()

  private fun unsupported(): Nothing =
    throw UnsupportedOperationException("ThrowingDeserializer does not support any methods")
}
