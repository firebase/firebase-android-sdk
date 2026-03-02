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

import android.content.Context
import android.net.ConnectivityManager
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.generativemodel.CloudGenerativeModelProvider
import com.google.firebase.ai.generativemodel.FallbackGenerativeModelProvider
import com.google.firebase.ai.generativemodel.GenerativeModelProvider
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class GenerativeModelBuilderTests {

  val firebaseApp = mockk<FirebaseApp>(relaxed = true)

  @Before
  fun setUp() {
    every { firebaseApp.options.applicationId } returns "1:12345:android:67890"
    val context = mockk<Context>(relaxed = true)
    val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    every { firebaseApp.applicationContext } returns context
    every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
  }

  @Test
  fun `getModelProvider uses hybrid suffix in PREFER_ON_DEVICE mode`() {
    val builder =
      GenerativeModel.Builder(
          modelName = "gemini-1.5-flash",
          apiKey = "apiKey",
          firebaseApp = firebaseApp,
          useLimitedUseAppCheckTokens = false,
          generativeBackend = GenerativeBackend.googleAI()
        )
        .apply { onDeviceConfig = OnDeviceConfig(InferenceMode.PREFER_ON_DEVICE) }

    val provider = builder.getModelProvider()

    provider.shouldBeInstanceOf<FallbackGenerativeModelProvider>()
    val fallbackModel = provider.getPrivateField<GenerativeModelProvider>("fallbackModel")

    fallbackModel.shouldBeInstanceOf<CloudGenerativeModelProvider>()
    val controller =
      fallbackModel.getPrivateField<com.google.firebase.ai.common.APIController>("controller")

    val apiClient = controller.getPrivateField<String>("apiClient")
    apiClient shouldContain " hybrid"
  }

  @Test
  fun `getModelProvider uses hybrid suffix in PREFER_IN_CLOUD mode`() {
    val builder =
      GenerativeModel.Builder(
          modelName = "gemini-1.5-flash",
          apiKey = "apiKey",
          firebaseApp = firebaseApp,
          useLimitedUseAppCheckTokens = false,
          generativeBackend = GenerativeBackend.googleAI()
        )
        .apply { onDeviceConfig = OnDeviceConfig(InferenceMode.PREFER_IN_CLOUD) }

    val provider = builder.getModelProvider()

    provider.shouldBeInstanceOf<FallbackGenerativeModelProvider>()
    val defaultModel = provider.getPrivateField<GenerativeModelProvider>("defaultModel")

    defaultModel.shouldBeInstanceOf<CloudGenerativeModelProvider>()
    val controller =
      defaultModel.getPrivateField<com.google.firebase.ai.common.APIController>("controller")

    val apiClient = controller.getPrivateField<String>("apiClient")
    apiClient shouldContain " hybrid"
  }

  @Test
  fun `getModelProvider does NOT use hybrid suffix in ONLY_IN_CLOUD mode`() {
    val builder =
      GenerativeModel.Builder(
          modelName = "gemini-1.5-flash",
          apiKey = "apiKey",
          firebaseApp = firebaseApp,
          useLimitedUseAppCheckTokens = false,
          generativeBackend = GenerativeBackend.googleAI()
        )
        .apply { onDeviceConfig = OnDeviceConfig.IN_CLOUD }

    val provider = builder.getModelProvider()

    provider.shouldBeInstanceOf<CloudGenerativeModelProvider>()
    val controller =
      provider.getPrivateField<com.google.firebase.ai.common.APIController>("controller")

    val apiClient = controller.getPrivateField<String>("apiClient")
    apiClient shouldNotContain " hybrid"
  }
}

private fun <T> Any.getPrivateField(name: String): T {
  val field = this.javaClass.getDeclaredField(name)
  field.isAccessible = true
  @Suppress("UNCHECKED_CAST") return field.get(this) as T
}
