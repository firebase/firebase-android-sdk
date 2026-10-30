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

package com.google.firebase.ai.type

import android.Manifest
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.util.doBlocking
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Tests that an error during an audio conversation stops the conversation instead of failing the
 * coroutine that started it, which crashes the app when that coroutine runs in a scope like
 * `viewModelScope`.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(PublicPreviewAPI::class)
class LiveSessionAudioConversationErrorTest {
  private val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
  private val sentFrames = Channel<String>(Channel.UNLIMITED)
  private val mockSession = mockk<DefaultWebSocketSession>()
  private val audioHelper = mockk<AudioHelper>(relaxed = true)
  private val networkDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

  private val callerExceptions = CopyOnWriteArrayList<Throwable>()
  private val uncaughtExceptions = CopyOnWriteArrayList<Throwable>()

  /**
   * Like `viewModelScope`: a [SupervisorJob] without an exception handler, where an exception that
   * fails a coroutine crashes the app. Here, the handler records the exception instead.
   */
  private val callerScope =
    CoroutineScope(
      SupervisorJob() +
        Dispatchers.Unconfined +
        CoroutineExceptionHandler { _, exception -> callerExceptions.add(exception) }
    )

  private var previousUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
  private lateinit var liveSession: LiveSession

  @Before
  fun setUp() {
    // Exceptions that no coroutine handles go to the uncaught exception handler of the thread.
    previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, exception -> uncaughtExceptions.add(exception) }

    val application = RuntimeEnvironment.getApplication()
    shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
    val firebaseApp = mockk<FirebaseApp>()
    every { firebaseApp.applicationContext } returns application

    every { mockSession.coroutineContext } returns EmptyCoroutineContext + Job()
    every { mockSession.closeReason } returns CompletableDeferred<CloseReason?>()
    coEvery { mockSession.send(any()) } coAnswers
      {
        sentFrames.send(firstArg<Frame.Text>().readText())
      }

    // Replace the microphone and the speaker. The recording stays open without producing audio
    // until the conversation stops.
    every { audioHelper.listenToRecording() } returns flow { awaitCancellation() }
    mockkObject(AudioHelper.Companion)
    every { AudioHelper.build(any()) } returns audioHelper

    liveSession =
      LiveSession(
        session =
          DefaultClientWebSocketSession(
            call = mockk(),
            delegate = PolledWebSocketSession(incomingFrames, mockSession)
          ),
        blockingDispatcher = networkDispatcher,
        firebaseApp = firebaseApp
      )
  }

  @After
  fun tearDown() {
    liveSession.stopAudioConversation()
    callerScope.cancel()
    networkDispatcher.close()
    unmockkObject(AudioHelper.Companion)
    Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler)
  }

  @Test(timeout = 10000)
  fun malformedMessage_stopsConversationWithoutCrashing() = doBlocking {
    val callerJob = startAudioConversation()

    incomingFrames.send(Frame.Text("""{"serverContent": {"turnComplete": """))

    assertConversationStoppedWithoutCrashing(callerJob)
  }

  @Test(timeout = 10000)
  fun goAwayWithInvalidTimeLeft_stopsConversationWithoutCrashing() = doBlocking {
    val callerJob = startAudioConversation()

    incomingFrames.send(Frame.Text("""{"goAway": {"timeLeft": "NaNs"}}"""))

    assertConversationStoppedWithoutCrashing(callerJob)
  }

  @Test(timeout = 10000)
  fun functionCallHandlerThrows_stopsConversationWithoutCrashing() = doBlocking {
    val callerJob = startAudioConversation { throw IllegalStateException("Function call failed") }

    incomingFrames.send(Frame.Text(TOOL_CALL_MESSAGE))

    assertConversationStoppedWithoutCrashing(callerJob)
  }

  @Test(timeout = 10000)
  fun sendingAudioFails_stopsConversationWithoutCrashing() = doBlocking {
    coEvery { mockSession.send(any()) } throws ClosedSendChannelException("Session closed")
    // Like a real microphone, produce audio only after the conversation has started.
    val conversationStarted = CompletableDeferred<Unit>()
    every { audioHelper.listenToRecording() } returns
      flow {
        conversationStarted.await()
        emit(ByteArray(AUDIO_CHUNK_SIZE))
        awaitCancellation()
      }
    val callerJob = startAudioConversation()

    conversationStarted.complete(Unit)

    assertConversationStoppedWithoutCrashing(callerJob)
  }

  @Test(timeout = 10000)
  fun playingAudioFails_stopsConversationWithoutCrashing() = doBlocking {
    every { audioHelper.playAudio(any()) } throws IllegalStateException("Playback failed")
    val callerJob = startAudioConversation()

    incomingFrames.send(Frame.Text(AUDIO_MESSAGE))

    assertConversationStoppedWithoutCrashing(callerJob)
  }

  @Test(timeout = 10000)
  fun validToolCall_isAnsweredAndConversationKeepsRunning() = doBlocking {
    val callerJob = startAudioConversation { call ->
      FunctionResponsePart(
        call.name,
        JsonObject(mapOf("temperature" to JsonPrimitive(20))),
        call.id
      )
    }

    incomingFrames.send(Frame.Text(TOOL_CALL_MESSAGE))

    val response = withTimeout(TIMEOUT_MS) { sentFrames.receive() }
    response shouldContain "\"toolResponse\""
    response shouldContain "\"id\":\"call-1\""
    liveSession.isAudioConversationActive() shouldBe true
    callerJob.isActive shouldBe true

    liveSession.stopAudioConversation()

    withTimeout(TIMEOUT_MS) { callerJob.join() }
    callerJob.isCancelled shouldBe false
    callerExceptions.shouldBeEmpty()
    uncaughtExceptions.shouldBeEmpty()
    liveSession.isAudioConversationActive() shouldBe false
  }

  /** Starts an audio conversation from a coroutine in [callerScope] and returns its job. */
  private fun startAudioConversation(
    functionCallHandler: ((FunctionCallPart) -> FunctionResponsePart)? = null
  ): Job =
    callerScope.launch {
      liveSession.startAudioConversation(
        liveAudioConversationConfig {
          this.functionCallHandler = functionCallHandler
          // With interruptions enabled, the playback loop doesn't pause or resume the recording on
          // every iteration, so it doesn't call the mocked AudioHelper in a tight loop.
          enableInterruptions = true
        }
      )
    }

  private suspend fun assertConversationStoppedWithoutCrashing(callerJob: Job) {
    // The caller's job completes once the coroutines of the conversation have finished.
    val callerJobCompleted = withTimeoutOrNull(TIMEOUT_MS) { callerJob.join() } != null

    callerExceptions.shouldBeEmpty()
    uncaughtExceptions.shouldBeEmpty()
    callerJob.isCancelled shouldBe false
    callerJobCompleted shouldBe true
    liveSession.isAudioConversationActive() shouldBe false
    verify { audioHelper.release() }
  }

  /**
   * A session that receives frames from [incoming] and delegates everything else to a mock.
   *
   * [LiveSession.receive] polls [incoming] in a loop, and MockK records every call to a mock, which
   * can use up the memory of the test.
   */
  private class PolledWebSocketSession(
    override val incoming: ReceiveChannel<Frame>,
    delegate: DefaultWebSocketSession
  ) : DefaultWebSocketSession by delegate

  private companion object {
    const val TIMEOUT_MS = 5_000L

    /** One second of 16 kHz, 16-bit mono audio, which is more than the minimum buffer size. */
    const val AUDIO_CHUNK_SIZE = 32_000

    val TOOL_CALL_MESSAGE =
      """
      {"toolCall": {"functionCalls": [{"name": "getWeather", "args": {"city": "Paris"}, "id": "call-1"}]}}
      """
        .trimIndent()

    val AUDIO_MESSAGE =
      """
      {"serverContent": {"modelTurn": {"role": "model", "parts": [{"inlineData": {"mimeType": "audio/pcm", "data": "AAAAAA=="}}]}}}
      """
        .trimIndent()
  }
}
