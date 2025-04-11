package com.google.firebase.vertexai

import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.vertexai.common.APIController
import com.google.firebase.vertexai.common.JSON
import com.google.firebase.vertexai.type.BASE_64_FLAGS
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.asInlineDataPartOrNull
import com.google.firebase.vertexai.util.TEST_APP_ID
import com.google.firebase.vertexai.util.TEST_CLIENT_ID
import com.google.firebase.vertexai.util.TEST_VERSION
import io.kotest.matchers.equals.shouldBeEqual
import io.ktor.client.plugins.websocket.WebSockets as ClientWebsockets
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebsockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.ByteChannel
import io.ktor.websocket.Frame
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@OptIn(PublicPreviewAPI::class)
@RunWith(RobolectricTestRunner::class)
internal class LiveModelTests {
  @Test
  fun `(generateContent) generates a proper response`() = testApplication {
    val client = createClient { install(ClientWebsockets) }

    val testUrl = "/ws"

    install(ServerWebsockets)

    val bytes = "Hello World!".toByteArray()

    val testResponse =
      JSON.decodeFromString<LiveSession.LiveServerContentSetup.Internal>(
        """
                {
                  "serverContent": {
                    "modelTurn": {
                      "role": "server",
                      "parts": [{
                        "inlineData": {
                          "mimeType": "audio/pcm",
                          "data": ${Base64.encodeToString(bytes, BASE_64_FLAGS)}
                        }
                      }]
                    }
                  }
                }
                """
          .trimIndent()
      )

    routing {
      webSocket(path = testUrl) {
        send(Frame.Text("setupComplete"))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
        //                    send(Frame.Binary(true,
        // JSON.encodeToString(LiveSession.LiveServerContentSetup.Internal.serializer(),
        // testResponse).toByteArray()))
          send(
              Frame.Binary(
                  true,
                  JSON.encodeToString(
                      LiveSession.LiveServerContentSetup.Internal.serializer(),
                      testResponse
                  )
                      .toByteArray()
              )
          )

        for (frame in incoming) {

        }
      }
    }

    val channel = ByteChannel(autoFlush = true)
    val mockFirebaseApp = Mockito.mock<FirebaseApp>()
    Mockito.`when`(mockFirebaseApp.isDataCollectionDefaultEnabled).thenReturn(false)

    val apiController =
      Mockito.spy(
        APIController(
          "super_cool_test_key",
          "gemini-pro",
          RequestOptions(),
          client.engine,
          TEST_CLIENT_ID,
          mockFirebaseApp,
          TEST_VERSION,
          TEST_APP_ID,
          null,
        )
      )

    Mockito.doReturn(testUrl).`when`(apiController).getBidiEndpoint(Mockito.anyString())
    // Mockito.`when`(apiController.getBidiEndpoint(Mockito.anyString())).thenReturn(server.url("/").toString())

    // val scope = CoroutineScope(firebaseExecutors.blocking)
    val model =
      LiveGenerativeModel(
        "cool-model-name",
        location = "us-central1",
        backgroundDispatcher = Dispatchers.IO,
        controller = apiController
      )

    // setupComplete
    withTimeout(5_000) {
      // channel.send("setupComplete".toByteArray())
      val connection = model.connect()
      val value = AtomicInteger(0)
      val currDelay = 100L
      val scope = CoroutineScope(Dispatchers.IO)
      scope.launch {
        println("Launching 1")
          connection.receive().collect {
          println("""
            Got message:
            Status: ${it.status}
            Data: ${it.data != null}
            Parts: ${it.data?.parts?.size}
            InlineData Size: ${it.data?.parts?.first()?.asInlineDataPartOrNull()?.inlineData?.size}
            
          """.trimIndent())
          println("1 => ${it.data?.parts?.first()?.asInlineDataPartOrNull()?.inlineData?.size}")
          value.incrementAndGet()
        }
        println("Done with 1")
      }

      delay(currDelay)
      println("Sending")
      delay(currDelay)
      connection.send("")
      delay(currDelay)
      delay(currDelay)
      //connection.stopReceiving()
      connection.close()
      delay(currDelay)
      value.get().shouldBeEqual(1)
    }

    //            // setupComplete
    //            withTimeout(10_000) {
    //                //channel.send("setupComplete".toByteArray())
    //                val connection = model.connect()
    //                connection.startAudioConversation()
    //                //delay(5_000)
    //                runCurrent()
    //                connection.stopAudioConversation()
    //                connection.close()
    //            }
  }
}
