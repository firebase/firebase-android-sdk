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

import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.GET_TEST_RETRIEVAL_FAILURE
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.GET_TEST_TIMEOUT
import com.google.firebase.appdistribution.gradle.models.DeviceExecution
import com.google.firebase.appdistribution.gradle.models.ReleaseTest
import java.io.IOException
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class TestLookupTest {
  private val mockApiService: ApiService = mock()
  private val mockThreadSleeper: ThreadSleeper = mock()

  private val testLookup = TestLookup(mockThreadSleeper)

  @Test
  fun pollForReleaseTests_whenExecutionEventuallySucceeds() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, passed)))
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, passed)))

    pollForReleaseTest()

    verify(mockApiService, times(2)).getReleaseTest(eq(RELEASE_TEST_1_NAME))
    verify(mockApiService, times(3)).getReleaseTest(eq(RELEASE_TEST_2_NAME))
  }

  @Test
  fun pollForReleaseTests_whenItSurpassesMaxNumberOfRetries_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(AppDistributionException::class) { pollForReleaseTest() }
    assertExceptionMessage(e, GET_TEST_TIMEOUT.message)
  }

  @Test
  fun pollForReleaseTests_whenExecutionEventuallyFails_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, failed)))
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(AppDistributionException::class) { pollForReleaseTest() }

    assertExceptionMessage(e, "Automated test failed")
  }

  @Test
  fun pollForReleaseTests_whenExecutionEventuallyIsInconclusive_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inconclusive)))
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(AppDistributionException::class) { pollForReleaseTest() }

    assertExceptionMessage(e, "Automated test inconclusive ")
  }

  @Test
  fun pollForReleaseTests_whenExecutionEventuallyIsUnknownState_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, unknown)))
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(AppDistributionException::class) { pollForReleaseTest() }

    assertExceptionMessage(e, "Unsupported automated test state")
  }

  @Test
  fun pollForReleaseTests_whenApiCallEventuallyThrowsIOException_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .doAnswer { throw IOException() }
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(AppDistributionException::class) { pollForReleaseTest() }

    assertExceptionMessage(e, GET_TEST_RETRIEVAL_FAILURE.message)
  }

  @Test
  fun pollForReleaseTests_whenApiCallEventuallyThrowsInterruptedException_fails() {
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_1_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(passed, inProgress)))
      .doAnswer { throw InterruptedException() }
    whenever(mockApiService.getReleaseTest(eq(RELEASE_TEST_2_NAME)))
      .thenReturn(ReleaseTest(deviceExecutions = listOf(inProgress, inProgress)))

    val e = assertFailsWith(RuntimeException::class) { pollForReleaseTest() }
    assertExceptionMessage(e, "There was an error while looking up the test results")
  }

  private fun pollForReleaseTest() =
    testLookup.pollForReleaseTests(mockApiService, listOf(RELEASE_TEST_1_NAME, RELEASE_TEST_2_NAME))

  private fun assertExceptionMessage(e: RuntimeException, expectedMessage: String) {
    val message = e.message
    assertNotNull(message)
    assertContains(message, expectedMessage)
  }

  companion object {
    const val RELEASE_TEST_1_NAME = "projects/123/apps/1:123:android:abc/releases/1/releaseTests/1"
    const val RELEASE_TEST_2_NAME = "projects/123/apps/1:123:android:abc/releases/1/releaseTests/2"
    val passed = DeviceExecution(state = "PASSED")
    val inProgress = DeviceExecution(state = "IN_PROGRESS")
    val inconclusive = DeviceExecution(state = "INCONCLUSIVE")
    val unknown = DeviceExecution(state = "UNKNOWN")
    val failed = DeviceExecution(state = "FAILED")
  }
}
