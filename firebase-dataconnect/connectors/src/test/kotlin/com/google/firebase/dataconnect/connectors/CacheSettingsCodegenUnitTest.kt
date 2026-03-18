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

@file:OptIn(ExperimentalKotest::class, ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect.connectors

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.CacheSettings
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
import com.google.firebase.dataconnect.connectors.caching_maxage.CachingMaxageConnector
import com.google.firebase.dataconnect.connectors.caching_maxage.getInstance
import com.google.firebase.dataconnect.connectors.caching_maxage.instance
import com.google.firebase.dataconnect.core.FirebaseDataConnectFactory
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class CacheSettingsCodegenUnitTest {

  @get:Rule
  val cleanups = CleanupsRule()

  @Before
  fun prepareFirebaseAppMocking() {
    mockkStatic(FirebaseApp::class)
    every { FirebaseApp.getInstance() } returns mockFirebaseApp()
  }

  @After
  fun undoFirebaseAppMocking() {
    unmockkStatic(FirebaseApp::class)
  }

  @Test
  fun `maxAge is specified in connector yaml but not storage`() {
    CachingMaxageConnector.defaultCacheSettings shouldBe CacheSettings(maxAge = 1.hours)
    CachingMaxageConnector.defaultDataConnectSettings shouldBe DataConnectSettings(cacheSettings = CacheSettings(maxAge = 1.hours))

    CachingMaxageConnector.instance.registerCleanup().dataConnect.settings shouldBe CachingMaxageConnector.defaultDataConnectSettings
    CachingMaxageConnector.getInstance().registerCleanup().dataConnect.settings shouldBe CachingMaxageConnector.defaultDataConnectSettings
    CachingMaxageConnector.getInstance(mockFirebaseApp()).registerCleanup().dataConnect.settings shouldBe CachingMaxageConnector.defaultDataConnectSettings
  }

  private fun <T : GeneratedConnector<T>> GeneratedConnector<T>.registerCleanup(): GeneratedConnector<T> {
    cleanups.register(cleanup = dataConnect::suspendingClose)
    return this
  }

}

private fun mockFirebaseApp(): FirebaseApp {
  val app: FirebaseApp = mockk(relaxed = true)

  every {
    app(FirebaseDataConnectFactory::class.java)
  }

  return app
}