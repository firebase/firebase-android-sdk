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

package com.google.firebase.dataconnect.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A queue that conflates jobs based on sequence numbers.
 *
 * This class ensures that at most one job is running at any given time. If multiple requests
 * arrive, they are conflated such that only the most recent request (highest sequence number)
 * eventually triggers a new job if the current one is insufficient.
 *
 * @param Params The type of the parameters passed to the job.
 * @param Output The type of the output produced by the job.
 * @property coroutineScope The [CoroutineScope] used to launch asynchronous jobs.
 * @property block The suspending function that performs the actual work.
 */
internal class SequenceNumberConflatedJobQueue<Params, Output>(
  private val coroutineScope: CoroutineScope,
  private val block: suspend (Params) -> Output,
) {
  /**
   * Mutex used to synchronize access to [jobSequencedReference] and [maxEnqueuedSequenceNumber].
   */
  private val mutex = Mutex()

  /** The currently active or most recently completed job, paired with its sequence number. */
  private var jobSequencedReference: SequencedReference<Deferred<Output>>? = null

  /** The highest sequence number that has been passed to [execute] so far. */
  private var maxEnqueuedSequenceNumber: Long? = null

  /**
   * Executes a job with the given [sequenceNumber] and [params], or waits for an existing one.
   *
   * If an existing job is already running and its sequence number is at least [sequenceNumber],
   * this method will wait for its completion and return its result.
   *
   * If an existing job is running but its sequence number is *less than* [sequenceNumber], this
   * method will wait for that job to complete and then return [ExecuteResult.Retry], signaling that
   * the caller should call [execute] again to trigger a newer job.
   *
   * @param sequenceNumber A strictly increasing number representing the version of the request.
   * @param params The parameters to pass to [block] if a new job is started.
   * @return An [ExecuteResult] containing either the successful result or a retry signal.
   */
  suspend fun execute(sequenceNumber: Long, params: Params): ExecuteResult<Output> {
    val jobSequencedReference = getOrStartExecuteJob(sequenceNumber, params)

    return if (jobSequencedReference.sequenceNumber < sequenceNumber) {
      jobSequencedReference.ref.join()
      ExecuteResult.Retry
    } else {
      val data = jobSequencedReference.ref.await()
      ExecuteResult.Success(SequencedReference(jobSequencedReference.sequenceNumber, data))
    }
  }

  /**
   * Internal method to retrieve an existing suitable job or start a new one.
   *
   * This method ensures that [maxEnqueuedSequenceNumber] is updated and that the returned job is
   * the most appropriate one to wait on.
   *
   * @param sequenceNumber The sequence number of the current request.
   * @param params The parameters to use if a new job must be started.
   * @return A [SequencedReference] containing a [Deferred] job.
   */
  private suspend fun getOrStartExecuteJob(
    sequenceNumber: Long,
    params: Params
  ): SequencedReference<Deferred<Output>> =
    mutex.withLock {
      maxEnqueuedSequenceNumber =
        when (val currentValue = maxEnqueuedSequenceNumber) {
          null -> sequenceNumber
          else -> currentValue.coerceAtLeast(sequenceNumber)
        }

      val jobSequencedReference = this.jobSequencedReference

      if (
        jobSequencedReference !== null &&
          (jobSequencedReference.sequenceNumber >= sequenceNumber ||
            !jobSequencedReference.ref.isCompleted)
      ) {
        jobSequencedReference
      } else {
        val job: Deferred<Output> = coroutineScope.async { block(params) }

        val jobSequenceNumber =
          checkNotNull(maxEnqueuedSequenceNumber) {
            "internal error gjy6gjyth4: maxEnqueuedSequenceNumber is null, " +
              "but a precondition of this method is that the caller ensures that it is not null"
          }
        check(jobSequenceNumber >= sequenceNumber) {
          "internal error sawv9wj8y4: jobSequenceNumber is $jobSequenceNumber, " +
            "but a precondition of this method is that the caller ensures that it is " +
            "at least the specified sequenceNumber, $sequenceNumber"
        }
        val newJobSequencedReference = SequencedReference(jobSequenceNumber, job)
        this.jobSequencedReference = newJobSequencedReference
        newJobSequencedReference
      }
    }

  /**
   * Represents the result of an [execute] call.
   *
   * @param T The type of the output data.
   */
  sealed interface ExecuteResult<out T> {
    /**
     * Indicates that the job completed successfully.
     *
     * @property output The result data wrapped in a [SequencedReference].
     */
    class Success<T>(val output: SequencedReference<T>) : ExecuteResult<T>

    /**
     * Indicates that the caller should retry the [execute] call.
     *
     * This typically happens when an older job finished, and a newer job (incorporating the
     * caller's `sequenceNumber`) needs to be started.
     */
    data object Retry : ExecuteResult<Nothing>
  }
}
