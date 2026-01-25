/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.ondevice

import com.google.common.truth.Truth.assertThat
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceUnknownException
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

abstract class GenerativeModelImplTest {

  private val mlkitModel = mock(com.google.mlkit.genai.prompt.GenerativeModel::class.java)
  private val model = GenerativeModelImpl(mlkitModel)

  @Test
  fun `isAvailable returns true when MLKit status is AVAILABLE`() = runTest {
    doReturn(FeatureStatus.AVAILABLE).`when`(mlkitModel).checkStatus()
    assertThat(model.isAvailable()).isTrue()
  }

  @Test
  fun `isAvailable returns false when MLKit status is not AVAILABLE`() = runTest {
    doReturn(FeatureStatus.DOWNLOADING).`when`(mlkitModel).checkStatus()
    assertThat(model.isAvailable()).isFalse()
  }

  @Test
  fun `getBaseModelName returns name from MLKit`() = runTest {
    doReturn("gemini-pro").`when`(mlkitModel).getBaseModelName()
    assertThat(model.getBaseModelName()).isEqualTo("gemini-pro")
  }

  @Test
  fun `getTokenLimit returns limit from MLKit`() = runTest {
    doReturn(4096).`when`(mlkitModel).getTokenLimit()
    assertThat(model.getTokenLimit()).isEqualTo(4096)
  }

  @Test
  fun `warmup wraps GenAiException`() = runTest {
    val genAiException = GenAiException("MLKit error", null, ANY_ERROR_CODE)
    doAnswer { throw genAiException }.`when`(mlkitModel).warmup()

    val exception = shouldThrow<FirebaseAIOnDeviceException> { model.warmup() }

    assertThat(exception).isInstanceOf(FirebaseAIOnDeviceUnknownException::class.java)
    assertThat(exception.cause).isSameInstanceAs(genAiException)
  }

  private companion object {
    const val ANY_ERROR_CODE = 1
  }
}
