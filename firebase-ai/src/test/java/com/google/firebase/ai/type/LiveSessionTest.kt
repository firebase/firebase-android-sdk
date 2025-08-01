package com.google.firebase.ai.type

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.util.doBlocking
import io.kotest.assertions.throwables.shouldThrow
import io.ktor.client.plugins.websocket.testing.EmptyWebSockets
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.mockito.Mockito

class LiveSessionTest {

    @Test
    fun `startAudioConversation without permission throws exception`() = doBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)).thenReturn(PackageManager.PERMISSION_DENIED)
        val mockFirebaseApp = Mockito.mock(FirebaseApp::class.java)
        Mockito.`when`(mockFirebaseApp.applicationContext).thenReturn(mockContext)
        val session = LiveSession(
            session = EmptyWebSockets.client.session,
            blockingDispatcher = Dispatchers.IO,
            firebaseApp = mockFirebaseApp
        )

        shouldThrow<PermissionMissingException> {
            session.startAudioConversation()
        }
    }
}
