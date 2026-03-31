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
 * This class ensures that at most one job is running at any given time. If multiple requests arrive
 * while a job is already in progress, they are conflated such that only one new job is eventually
 * triggered to satisfy all pending requests. This new job will deterministically use the parameters
 * associated with the most recent request (highest sequence number) seen prior to starting the job.
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
  /** Mutex used to synchronize access to [jobSequencedReference] and [enqueuedJob]. */
  private val mutex = Mutex()

  /** The currently active or most recently completed job, paired with its sequence number. */
  private var jobSequencedReference: SequencedReference<Deferred<Output>>? = null

  /**
   * The pending job request with the highest sequence number seen so far, paired with its
   * corresponding parameters. This state is held while waiting for the current job to finish.
   */
  private var enqueuedJob: SequencedReference<Params>? = null

  /**
   * Executes a job with the given [sequenceNumber] and [params], or waits for an existing one.
   *
   * If an existing job is already running and its sequence number is at least [sequenceNumber],
   * this method will wait for its completion and return its result, ignoring the given [params].
   *
   * If an existing job is running but its sequence number is *less than* [sequenceNumber], this
   * method will wait for that job to complete and then return [ExecuteResult.Retry], signaling that
   * the caller should call [execute] again to trigger a newer job.
   *
   * When a new job is eventually started, it will use the [params] associated with the highest
   * [sequenceNumber] seen while waiting.
   *
   * @param sequenceNumber A strictly increasing number representing the version of the request.
   * @param params The parameters to stash. If this request triggers a new job, these parameters
   * will be passed to [block] unless a request with an even higher sequence number arrives first.
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
   * This method ensures that [enqueuedJob] safely tracks the highest requested sequence number and
   * its corresponding parameters. If a new job must be started, it consumes [enqueuedJob] to
   * guarantee the job runs with the most up-to-date parameters.
   *
   * @param sequenceNumber The sequence number of the current request.
   * @param params The parameters to use if this request ends up starting the new job.
   * @return A [SequencedReference] containing a [Deferred] job.
   */
  private suspend fun getOrStartExecuteJob(
    sequenceNumber: Long,
    params: Params
  ): SequencedReference<Deferred<Output>> =
    mutex.withLock {
      /**
       * Stashes the current request into [enqueuedJob] if the current request has a strictly higher
       * sequence number than the currently-stashed request.
       */
      fun enqueueJob() {
        this.enqueuedJob.let {
          if (it === null || it.sequenceNumber < sequenceNumber) {
            this.enqueuedJob = SequencedReference(sequenceNumber, params)
          }
        }
      }

      val jobSequencedReference = this.jobSequencedReference

      if (
        jobSequencedReference !== null &&
          (jobSequencedReference.sequenceNumber >= sequenceNumber ||
            !jobSequencedReference.ref.isCompleted)
      ) {
        if (jobSequencedReference.sequenceNumber < sequenceNumber) {
          enqueueJob()
        }
        return@withLock jobSequencedReference
      }

      enqueueJob()

      val (jobSequenceNumber, jobParams) =
        run {
          val enqueuedJob =
            checkNotNull(this.enqueuedJob) {
              "internal error w73qx9cwcp: this.enqueuedJob should have " +
                "been set to non-null by enqueueJob()"
            }
          this.enqueuedJob = null
          enqueuedJob
        }

      check(jobSequenceNumber >= sequenceNumber) {
        "internal error sawv9wj8y4: jobSequenceNumber=$jobSequenceNumber and " +
          "sequenceNumber=$sequenceNumber, but jobSequenceNumber should be " +
          "greater than or equal to sequenceNumber"
      }

      val newJob: Deferred<Output> = coroutineScope.async { block(jobParams) }

      val newJobSequencedReference = SequencedReference(jobSequenceNumber, newJob)
      this.jobSequencedReference = newJobSequencedReference
      newJobSequencedReference
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
