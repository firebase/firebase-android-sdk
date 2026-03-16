/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.appdistribution.gradle

import com.google.firebase.appdistribution.gradle.NameUtils.extractResourceId
import com.google.firebase.appdistribution.gradle.models.DeviceExecution
import java.io.IOException
import org.gradle.api.logging.Logging

internal class TestLookup(private val threadSleeper: ThreadSleeper = ThreadSleeper()) {

  private val logger = Logging.getLogger(this::class.java)

  @Throws(AppDistributionException::class)
  fun pollForReleaseTests(apiService: ApiService, releaseTestNames: Collection<String>) {
    val pendingReleaseTestNames = releaseTestNames.toMutableSet()
    for (numRetries in 0..MAX_POLLING_RETRIES) {
      // Wait in the beginning since it's unlikely any tests will be finished on the first poll
      threadSleeper.sleep(POLLING_INTERVAL_MS)

      pendingReleaseTestNames.removeIf { releaseTestName ->
        hasReleaseTestPassed(apiService, releaseTestName)
      }
      if (pendingReleaseTestNames.isEmpty()) {
        logger.info("Automated test(s) passed!")
        return
      }

      logger.lifecycle("{} automated test(s) results are pending...", pendingReleaseTestNames.size)
    }
    throw AppDistributionException(AppDistributionException.Reason.GET_TEST_TIMEOUT)
  }

  private fun hasReleaseTestPassed(apiService: ApiService, releaseTestName: String): Boolean {
    val response =
      try {
        apiService.getReleaseTest(releaseTestName)
      } catch (e: IOException) {
        throw AppDistributionException(
          AppDistributionException.Reason.GET_TEST_RETRIEVAL_FAILURE,
          e
        )
      } catch (ie: InterruptedException) {
        throw RuntimeException("There was an error while looking up the test results.", ie)
      }

    if (response?.deviceExecutions != null) {
      if (
        response.deviceExecutions.all { deviceExecution: DeviceExecution ->
          deviceExecution.state == "PASSED"
        }
      ) {
        return true
      }

      val testLabel =
        if (response.testCase != null)
          "test with test case ID ${extractResourceId(response.testCase)}"
        else "test"
      for (deviceExecution in response.deviceExecutions) {
        when (deviceExecution.state) {
          "PASSED",
          "IN_PROGRESS" -> continue
          "FAILED" ->
            throw AppDistributionException(
              AppDistributionException.Reason.GET_TEST_FAILED,
              extraInformation =
                "Automated $testLabel failed for ${deviceExecution.device}: ${deviceExecution.failedReason}"
            )
          "INCONCLUSIVE" ->
            throw AppDistributionException(
              AppDistributionException.Reason.GET_TEST_FAILED,
              extraInformation =
                "Automated $testLabel inconclusive for ${deviceExecution.device}: ${deviceExecution.inconclusiveReason}"
            )
          else ->
            throw AppDistributionException(
              AppDistributionException.Reason.GET_TEST_FAILED,
              extraInformation =
                "Unsupported automated $testLabel state for ${deviceExecution.device}: ${deviceExecution.state}"
            )
        }
      }
    }

    return false
  }

  companion object {
    const val POLLING_INTERVAL_MS: Long = 30_000
    const val MAX_POLLING_RETRIES = 40
  }
}
