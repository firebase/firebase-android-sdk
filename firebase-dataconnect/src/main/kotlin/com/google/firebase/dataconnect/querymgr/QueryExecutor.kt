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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.newerOfThisAnd
import com.google.firebase.dataconnect.util.nextSequenceNumber
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Struct
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

internal class QueryExecutor(
  val dataConnect: FirebaseDataConnectImpl,
  val operationName: String,
  val variables: Struct
) {
  private val mutex = Mutex()

  private val state = MutableStateFlow(State(lastResult = null, lastSuccessfulResult = null))
  val lastResult: SequencedReference<QueryExecutorResult>?
    get() = state.value.lastResult
  val lastSuccessfulResult: SequencedReference<QueryExecutorResult.Success>?
    get() = state.value.lastSuccessfulResult

  suspend fun execute(isFromGeneratedSdk: Boolean): SequencedReference<QueryExecutorResult> {
    val minSequenceNumber = nextSequenceNumber()
    return state
      .map { it.lastResult }
      .filter {
        if (it != null && it.sequenceNumber > minSequenceNumber) {
          true
        } else {
          if (mutex.tryLock()) {
            executeLocked(isFromGeneratedSdk = isFromGeneratedSdk)
          }
          false
        }
      }
      .filterNotNull()
      .first()
  }

  suspend fun subscribe(
    executeQuery: Boolean,
    isFromGeneratedSdk: Boolean,
    callback: suspend (SequencedReference<QueryExecutorResult>) -> Unit
  ): Nothing {
    var minSequenceNumber: Long = -1

    val initialState = state.value

    initialState.lastSuccessfulResult?.let {
      callback(it)
      minSequenceNumber = it.sequenceNumber
    }

    initialState.lastResult?.let {
      if (it.sequenceNumber > minSequenceNumber) {
        callback(it)
        minSequenceNumber = it.sequenceNumber
      }
    }

    if (executeQuery) {
      runCatching { execute(isFromGeneratedSdk = isFromGeneratedSdk) }
    }

    state.collect {
      it.lastResult?.let { lastResult ->
        if (lastResult.sequenceNumber > minSequenceNumber) {
          callback(lastResult)
          minSequenceNumber = lastResult.sequenceNumber
        }
      }
    }
  }

  private suspend fun executeLocked(
    isFromGeneratedSdk: Boolean
  ): SequencedReference<QueryExecutorResult> {
    val executeQueryResult =
      try {
        val requestId = "qry" + Random.nextAlphanumericString(length = 10)
        val sequenceNumber = nextSequenceNumber()
        dataConnect.lazyGrpcClient
          .get()
          .runCatching {
            executeQuery(
              requestId = requestId,
              operationName = operationName,
              variables = variables,
              isFromGeneratedSdk = isFromGeneratedSdk,
            )
          }
          .fold(
            onSuccess = { QueryExecutorResult.Success(this, requestId, it) },
            onFailure = {
              QueryExecutorResult.Failure(
                this,
                requestId,
                if (it is DataConnectException) it
                else DataConnectException("unknown error: $it", it)
              )
            },
          )
          .let { SequencedReference(sequenceNumber, it) }
      } finally {
        mutex.unlock()
      }

    coroutineContext.ensureActive()

    while (true) {
      val originalState = state.value
      val updatedState = originalState.updatedFrom(executeQueryResult)

      if (updatedState == originalState) {
        break
      }

      if (state.compareAndSet(originalState, updatedState)) {
        break
      }
    }

    return executeQueryResult
  }

  private data class State(
    val lastResult: SequencedReference<QueryExecutorResult>?,
    val lastSuccessfulResult: SequencedReference<QueryExecutorResult.Success>?
  ) {

    fun updatedFrom(result: SequencedReference<QueryExecutorResult>): State {
      val newLastResult = lastResult.newerOfThisAnd(result)
      val newLastSuccessfulResult = lastSuccessfulResult.newerOfThisAnd(result.successOrNull())

      return if (newLastResult === lastResult && newLastSuccessfulResult === lastSuccessfulResult) {
        this
      } else {
        copy(lastResult = newLastResult, lastSuccessfulResult = newLastSuccessfulResult)
      }
    }
  }
}
