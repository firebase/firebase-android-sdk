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

package com.google.firebase.ai

import com.google.firebase.ai.generativemodel.GenerativeModelProvider
import com.google.firebase.ai.ondevice.interop.DownloadStatusInterop
import com.google.firebase.ai.ondevice.interop.GenerativeModel as InteropGenerativeModel
import com.google.firebase.ai.ondevice.interop.OnDeviceModelStatusInterop
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestOptions
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class OnDeviceExtensionTests {

  private val interopModel = mockk<InteropGenerativeModel>()
  private val actualModel = mockk<GenerativeModelProvider>()

  @Test
  fun `checkStatus returns correctly mapped status`() {
    runBlocking {
      coEvery { interopModel.checkStatus() } returns OnDeviceModelStatusInterop.AVAILABLE

      val extension = OnDeviceExtension(interopModel)
      val status = extension.checkStatus()

      status shouldBe OnDeviceModelStatus.AVAILABLE
    }
  }

  @Test
  fun `download returns correctly mapped flow`() {
    runBlocking {
      val interopFlow =
        flowOf(
          DownloadStatusInterop.DownloadStarted(100),
          DownloadStatusInterop.DownloadInProgress(50),
          DownloadStatusInterop.DownloadCompleted()
        )
      every { interopModel.download() } returns interopFlow

      val extension = OnDeviceExtension(interopModel)
      val flow = extension.download()

      val results = flow.toList()
      results.size shouldBe 3
      (results[0] as DownloadStatus.DownloadStarted).bytesToDownload shouldBe 100
      (results[1] as DownloadStatus.DownloadInProgress).totalBytesDownloaded shouldBe 50
      results[2] shouldBe DownloadStatus.DownloadCompleted()
    }
  }

  @Test
  fun `warmUp calls interopModel warmup`() {
    runBlocking {
      coEvery { interopModel.warmup() } returns Unit

      val extension = OnDeviceExtension(interopModel)
      extension.warmUp()

      coVerify(exactly = 1) { interopModel.warmup() }
    }
  }

  @Test
  fun `getOnDeviceModelName calls interopModel getBaseModelName`() {
    runBlocking {
      coEvery { interopModel.getBaseModelName() } returns "gemini-2.0-flash"

      val extension = OnDeviceExtension(interopModel)
      val modelName = extension.getOnDeviceModelName()

      modelName shouldBe "gemini-2.0-flash"
      coVerify(exactly = 1) { interopModel.getBaseModelName() }
    }
  }

  @Test
  fun `onDeviceExtension is null when passed null in constructor`() {
    val model =
      GenerativeModel(
        actualModel = actualModel,
        requestOptions = RequestOptions(),
        onDeviceExtension = null
      )
    model.onDeviceExtension shouldBe null
  }

  @Test
  fun `onDeviceExtension is not null when passed in constructor`() {
    val extension = OnDeviceExtension(interopModel)
    val model =
      GenerativeModel(
        actualModel = actualModel,
        requestOptions = RequestOptions(),
        onDeviceExtension = extension
      )
    model.onDeviceExtension shouldBe extension
  }
}
