package com.google.firebase.ai.type

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.ai.common.PermissionMissingException
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class LiveSessionTest {

  @Mock private lateinit var mockContext: Context
  @Mock private lateinit var mockPackageManager: PackageManager
  @Mock private lateinit var mockSession: ClientWebSocketSession
  @Mock private lateinit var mockAudioHelper: AudioHelper

  private lateinit var testDispatcher: CoroutineContext
  private lateinit var liveSession: LiveSession

  @Before
  fun setUp() {
    testDispatcher = UnconfinedTestDispatcher()
    `when`(mockContext.packageManager).thenReturn(mockPackageManager)

    // Mock AudioHelper.build() to return our mockAudioHelper
    // Need to use mockStatic for static methods
    mockStatic(AudioHelper::class.java).use { mockedAudioHelper ->
      mockedAudioHelper.`when`<AudioHelper> { AudioHelper.build() }.thenReturn(mockAudioHelper)
      liveSession = LiveSession(mockContext, mockSession, testDispatcher, null)
    }
  }

  @Test
  fun `startAudioConversation with RECORD_AUDIO permission proceeds normally`() = runTest {
    // Arrange
    `when`(
        mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
      )
      .thenReturn(PackageManager.PERMISSION_GRANTED)

    // Act & Assert
    // No exception should be thrown
    liveSession.startAudioConversation()
  }

  @Test
  fun `startAudioConversation without RECORD_AUDIO permission throws PermissionMissingException`() =
    runTest {
      // Arrange
      `when`(
          mockContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        )
        .thenReturn(PackageManager.PERMISSION_DENIED)

      // Act & Assert
      val exception =
        assertThrows(PermissionMissingException::class.java) {
          runTest { liveSession.startAudioConversation() }
        }
      assertEquals("Missing RECORD_AUDIO", exception.message)
    }
}
