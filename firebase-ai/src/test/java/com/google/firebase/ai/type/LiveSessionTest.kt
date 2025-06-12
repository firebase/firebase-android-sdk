/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.type

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.ai.common.PermissionMissingException
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@OptIn(ExperimentalCoroutinesApi::class, PublicPreviewAPI::class)
@RunWith(MockitoJUnitRunner::class)
class LiveSessionTest {

  @Mock private lateinit var mockContext: Context
  @Mock private lateinit var mockPackageManager: PackageManager
  @Mock private lateinit var mockSession: ClientWebSocketSession
  @Mock private lateinit var mockAudioHelper: AudioHelper

  private lateinit var mockedBuildVersion: MockedStatic<Build.VERSION>
  private lateinit var testDispatcher: CoroutineContext
  private lateinit var liveSession: LiveSession

  @Before
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    `when`(mockContext.packageManager).thenReturn(mockPackageManager)
    mockedBuildVersion = mockStatic(Build.VERSION::class.java)

    // Mock AudioHelper.build() to return our mockAudioHelper
    // Need to use mockStatic for static methods
    // Note: It's generally better to manage static mocks with try-with-resources or @ExtendWith if
    // the runner supports it well, but for this structure, @Before/@After is common.
    // AudioHelper static mock is managed with try-with-resources where it's used for instance
    // creation.
    mockStatic(AudioHelper::class.java).use { mockedAudioHelperStatic ->
      mockedAudioHelperStatic
        .`when`<AudioHelper> { AudioHelper.build() }
        .thenReturn(mockAudioHelper)
      liveSession = LiveSession(mockContext, mockSession, testDispatcher, null)
    }
  }

  @After
  fun tearDown() {
    mockedBuildVersion.close()
  }

  @Test
  fun `startAudioConversation on API M+ with permission proceeds normally`() = runTest {
    // Arrange
    mockedBuildVersion.`when` { Build.VERSION.SDK_INT }.thenReturn(Build.VERSION_CODES.M)
    `when`(mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
      .thenReturn(PackageManager.PERMISSION_GRANTED)

    // Act & Assert
    // No exception should be thrown
    liveSession.startAudioConversation()
  }

  @Test
  fun `startAudioConversation on API M+ without permission throws PermissionMissingException`() =
    runTest {
      // Arrange
      mockedBuildVersion.`when` { Build.VERSION.SDK_INT }.thenReturn(Build.VERSION_CODES.M)
      `when`(mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        .thenReturn(PackageManager.PERMISSION_DENIED)

      // Act & Assert
      val exception =
        assertThrows(PermissionMissingException::class.java) {
          runTest { liveSession.startAudioConversation() }
        }
      assertEquals("Missing RECORD_AUDIO", exception.message)
    }

  @Test
  fun `startAudioConversation on API Pre-M with denied permission proceeds normally`() = runTest {
    // Arrange
    mockedBuildVersion.`when` { Build.VERSION.SDK_INT }.thenReturn(Build.VERSION_CODES.LOLLIPOP)
    `when`(mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
      .thenReturn(PackageManager.PERMISSION_DENIED) // This shouldn't be checked

    // Act & Assert
    // No exception should be thrown
    liveSession.startAudioConversation()
  }

  @Test
  fun `startAudioConversation on API Pre-M with granted permission proceeds normally`() = runTest {
    // Arrange
    mockedBuildVersion.`when` { Build.VERSION.SDK_INT }.thenReturn(Build.VERSION_CODES.LOLLIPOP)
    `when`(mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
      .thenReturn(PackageManager.PERMISSION_GRANTED) // This shouldn't be checked

    // Act & Assert
    // No exception should be thrown
    liveSession.startAudioConversation()
  }
}
