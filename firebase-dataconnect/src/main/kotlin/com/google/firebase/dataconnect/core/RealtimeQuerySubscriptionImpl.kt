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

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.QuerySubscriptionResult
import java.util.Objects
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * A temporary implementation of [QuerySubscription] that supports "realtime subscription updates".
 *
 * "Realtime subscription updates" is currently a work-in-progress; however, when it is completed,
 * this class will be renamed to [QuerySubscriptionImpl] and the existing [QuerySubscriptionImpl]
 * will be deleted.
 */
@ExperimentalRealtimeQueries
internal class RealtimeQuerySubscriptionImpl<Data, Variables>(
  override val query: RealtimeQueryRefImpl<Data, Variables>,
  private val random: Random,
) : QuerySubscription<Data, Variables> {

  override val flow: Flow<QuerySubscriptionResult<Data, Variables>> = flow {
    emitAll(
      query.run {
        val serialization = dataConnect.serialization
        val requestId = random.nextRequestId()

        val connectionFlow =
          dataConnect.grpcClient.connect(
            requestId = requestId,
            operationName = operationName,
            variables =
              serialization.encodeVariables(
                variables,
                variablesSerializer,
                variablesSerializersModule
              ),
            callerSdkType,
          )

        connectionFlow.map { operationResult: DataConnectGrpcClient.OperationResult ->
          operationResult.run {
            val queryResult =
              serialization.runCatching {
                val decodedData = decodeData(data, errors, dataDeserializer, dataSerializersModule)
                RealtimeQueryResultImpl(decodedData, DataSource.SERVER)
              }

            if (queryResult.exceptionOrNull() is CancellationException) {
              currentCoroutineContext().ensureActive()
            }

            RealtimeQuerySubscriptionResultImpl(query, queryResult)
          }
        }
      }
    )
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String = "RealtimeQuerySubscriptionImpl(query=$query)"

  private inner class RealtimeQuerySubscriptionResultImpl(
    override val query: RealtimeQueryRefImpl<Data, Variables>,
    override val result: Result<RealtimeQueryRefImpl<Data, Variables>.RealtimeQueryResultImpl>,
  ) : QuerySubscriptionResult<Data, Variables> {

    override fun equals(other: Any?) =
      other is RealtimeQuerySubscriptionImpl<*, *>.RealtimeQuerySubscriptionResultImpl &&
        other.query == query &&
        other.result == result

    override fun hashCode() =
      Objects.hash(RealtimeQuerySubscriptionResultImpl::class, query, result)

    override fun toString() = "RealtimeQuerySubscriptionResultImpl(query=$query, result=$result)"
  }
}

private val nextRequestIdSequenceNumber = AtomicLong(0)

private const val MIN_REQUEST_ID_LENGTH = 11

private fun Random.nextRequestId(): String =
  buildString(capacity = MIN_REQUEST_ID_LENGTH) {
    append("rid")
    append(nextRequestIdSequenceNumber.incrementAndGet())
    while (length < MIN_REQUEST_ID_LENGTH) {
      append(nextAlphabeticChar())
    }
  }

private fun Random.nextAlphabeticChar(): Char = ALPHABETIC_ALPHABET.random(this)

// The set of characters comprising the 26 lowercase letters of the English alphabet with some
// characters removed that can look similar in different fonts, 'l', and 'i'.
@Suppress("SpellCheckingInspection")
private const val ALPHABETIC_ALPHABET = "abcdefghjkmnpqrstvwxyz"
