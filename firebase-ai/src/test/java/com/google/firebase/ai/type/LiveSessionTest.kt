package com.google.firebase.ai.type

import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.util.doBlocking
import io.kotest.assertions.throwables.shouldThrow
import io.ktor.client.plugins.websocket.testing.*
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.mockito.kotlin.*

class LiveSessionTest {

    @Test
    fun `startAudioConversation without permission throws exception`() = doBlocking {
        val mockContext = mock<Context> {
            on { checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) } doReturn PackageManager.PERMISSION_DENIED
        }
        val mockFirebaseApp = mock<FirebaseApp> {
            on { applicationContext } doReturn mockContext
        }
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
