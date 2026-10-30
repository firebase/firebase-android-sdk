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

import com.google.firebase.FirebaseApp
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(PublicPreviewAPI::class)
class LiveSessionTest {

  @Test(timeout = 10000)
  fun testIsClosed_initiallyFalse() {
    val mockSession = mockk<DefaultClientWebSocketSession>()
    val mockFirebaseApp = mockk<FirebaseApp>()
    val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
    val closeReasonDeferred = CompletableDeferred<CloseReason?>()

    val job = Job() // Active job
    every { mockSession.coroutineContext } returns EmptyCoroutineContext + job
    every { mockSession.incoming } returns incomingChannel
    every { mockSession.closeReason } returns closeReasonDeferred

    val liveSession =
      LiveSession(
        session = mockSession,
        blockingDispatcher = Dispatchers.Unconfined,
        firebaseApp = mockFirebaseApp
      )

    liveSession.isClosed() shouldBe false
  }

  @Test(timeout = 10000)
  fun testIsClosed_afterClose_returnsTrue() {
    runBlocking {
      val mockSession = mockk<DefaultClientWebSocketSession>()
      val mockFirebaseApp = mockk<FirebaseApp>()
      val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
      val outgoingChannel = Channel<Frame>(Channel.UNLIMITED)
      val closeReasonDeferred = CompletableDeferred<CloseReason?>()

      val job = Job() // Active job
      every { mockSession.coroutineContext } returns EmptyCoroutineContext + job
      every { mockSession.incoming } returns incomingChannel
      every { mockSession.outgoing } returns outgoingChannel
      every { mockSession.closeReason } returns closeReasonDeferred
      coEvery { mockSession.flush() } just runs

      // Mock send member function to delegate to outgoingChannel
      coEvery { mockSession.send(any()) } coAnswers
        {
          val frame = firstArg<Frame>()
          outgoingChannel.send(frame)
        }

      // Simulate Ktor behavior: sending close frame completes closeReason and cancels job
      val monitorJob = launch {
        for (frame in outgoingChannel) {
          if (frame is Frame.Close) {
            closeReasonDeferred.complete(CloseReason(CloseReason.Codes.NORMAL, ""))
            job.cancel()
            break
          }
        }
      }

      val liveSession =
        LiveSession(
          session = mockSession,
          blockingDispatcher = Dispatchers.Unconfined,
          firebaseApp = mockFirebaseApp
        )

      liveSession.close()
      monitorJob.join()

      liveSession.isClosed() shouldBe true
    }
  }

  @Test(timeout = 10000)
  fun testIsClosed_serverClosedWithUnconsumedFrames_returnsTrue() {
    runBlocking {
      val mockSession = mockk<DefaultClientWebSocketSession>()
      val mockFirebaseApp = mockk<FirebaseApp>()
      val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
      val closeReasonDeferred = CompletableDeferred<CloseReason?>()

      val job = Job() // Active job
      every { mockSession.coroutineContext } returns EmptyCoroutineContext + job
      every { mockSession.incoming } returns incomingChannel
      every { mockSession.closeReason } returns closeReasonDeferred

      val liveSession =
        LiveSession(
          session = mockSession,
          blockingDispatcher = Dispatchers.Unconfined,
          firebaseApp = mockFirebaseApp
        )

      // Add some unconsumed frames to incoming channel
      incomingChannel.send(Frame.Text("hello"))

      // Simulate server close: complete closeReason, cancel job, and close channel
      closeReasonDeferred.complete(CloseReason(CloseReason.Codes.NORMAL, ""))
      job.cancel()
      incomingChannel.close()

      // The channel still has "hello" frame unconsumed, so it is not fully closed for receive yet
      incomingChannel.isClosedForReceive shouldBe false

      // But the session should be considered closed because closeReason is completed
      liveSession.isClosed() shouldBe true
    }
  }

  @Test(timeout = 10000)
  fun testReceive_whileIdle_canBeCancelled() {
    runBlocking {
      val liveSession = createLiveSession(Channel(Channel.UNLIMITED))
      // Dispatchers.Unconfined runs receive() synchronously up to its idle poll suspension point.
      val receiveJob = launch(Dispatchers.Unconfined) { liveSession.receive().collect {} }

      // receive() must suspend while it waits for frames, so that it can be cancelled.
      receiveJob.cancelAndJoin()

      receiveJob.isCancelled shouldBe true
    }
  }

  @Test(timeout = 10000)
  fun testReceive_afterIdle_emitsMessage() {
    runBlocking {
      val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
      val liveSession = createLiveSession(incomingChannel)
      val firstMessage = async(Dispatchers.Unconfined) { liveSession.receive().first() }

      incomingChannel.send(Frame.Text("""{"setupComplete": {}}"""))

      firstMessage.await() shouldBe LiveServerSetupComplete
    }
  }

  @Test(timeout = 10000)
  fun testStopReceiving_whileIdle_completesReceive() {
    runBlocking {
      val liveSession = createLiveSession(Channel(Channel.UNLIMITED))
      val receiveJob = launch(Dispatchers.Unconfined) { liveSession.receive().collect {} }

      liveSession.stopReceiving()

      receiveJob.join()
      receiveJob.isCancelled shouldBe false
    }
  }

  private fun createLiveSession(incomingChannel: Channel<Frame>): LiveSession {
    val mockSession = mockk<DefaultClientWebSocketSession>()
    every { mockSession.coroutineContext } returns EmptyCoroutineContext + Job()
    every { mockSession.incoming } returns incomingChannel
    every { mockSession.closeReason } returns CompletableDeferred()
    return LiveSession(
      session = mockSession,
      blockingDispatcher = Dispatchers.Unconfined,
      firebaseApp = mockk<FirebaseApp>()
    )
  }
}
