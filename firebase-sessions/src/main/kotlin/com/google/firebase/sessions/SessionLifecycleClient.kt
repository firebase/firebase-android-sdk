/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.firebase.sessions.api.FirebaseSessionsDependencies
import com.google.firebase.sessions.api.SessionSubscriber
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Client for binding to the [SessionLifecycleService]. This client will receive updated sessions
 * through a callback whenever a new session is generated by the service, or after the initial
 * binding.
 *
 * Note: this client will be connected in every application process that uses Firebase, and is
 * intended to maintain that connection for the lifetime of the process.
 */
internal class SessionLifecycleClient(private val backgroundDispatcher: CoroutineContext) {

  private var service: Messenger? = null
  private var serviceBound: Boolean = false
  private val queuedMessages = LinkedBlockingDeque<Message>(MAX_QUEUED_MESSAGES)

  /**
   * The callback class that will be used to receive updated session events from the
   * [SessionLifecycleService].
   */
  internal class ClientUpdateHandler(private val backgroundDispatcher: CoroutineContext) :
    Handler(Looper.getMainLooper()) {

    override fun handleMessage(msg: Message) {
      when (msg.what) {
        SessionLifecycleService.SESSION_UPDATED ->
          handleSessionUpdate(
            msg.data?.getString(SessionLifecycleService.SESSION_UPDATE_EXTRA) ?: ""
          )
        else -> {
          Log.w(TAG, "Received unexpected event from the SessionLifecycleService: $msg")
          super.handleMessage(msg)
        }
      }
    }

    private fun handleSessionUpdate(sessionId: String) {
      Log.d(TAG, "Session update received.")

      CoroutineScope(backgroundDispatcher).launch {
        FirebaseSessionsDependencies.getRegisteredSubscribers().values.forEach { subscriber ->
          // Notify subscribers, regardless of sampling and data collection state.
          subscriber.onSessionChanged(SessionSubscriber.SessionDetails(sessionId))
          Log.d(TAG, "Notified ${subscriber.sessionSubscriberName} of new session $sessionId")
        }
      }
    }
  }

  /** The connection object to the [SessionLifecycleService]. */
  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(className: ComponentName?, serviceBinder: IBinder?) {
        Log.d(TAG, "Connected to SessionLifecycleService. Queue size ${queuedMessages.size}")
        service = Messenger(serviceBinder)
        serviceBound = true
        sendLifecycleEvents(drainQueue())
      }

      override fun onServiceDisconnected(className: ComponentName?) {
        Log.d(TAG, "Disconnected from SessionLifecycleService")
        service = null
        serviceBound = false
      }
    }

  /**
   * Binds to the [SessionLifecycleService] and passes a callback [Messenger] that will be used to
   * relay session updates to this client.
   */
  fun bindToService(sessionLifecycleServiceBinder: SessionLifecycleServiceBinder) {
    sessionLifecycleServiceBinder.bindToService(
      Messenger(ClientUpdateHandler(backgroundDispatcher)),
      serviceConnection,
    )
  }

  /**
   * Should be called when any activity in this application process goes to the foreground. This
   * will relay the event to the [SessionLifecycleService] where it can make the determination of
   * whether or not this foregrounding event should result in a new session being generated.
   */
  fun foregrounded() {
    sendLifecycleEvent(SessionLifecycleService.FOREGROUNDED)
  }

  /**
   * Should be called when any activity in this application process goes from the foreground to the
   * background. This will relay the event to the [SessionLifecycleService] where it will be used to
   * determine when a new session should be generated.
   */
  fun backgrounded() {
    sendLifecycleEvent(SessionLifecycleService.BACKGROUNDED)
  }

  /**
   * Sends a message to the [SessionLifecycleService] with the given event code. This will
   * potentially also send any messages that have been queued up but not successfully delivered to
   * this service since the previous send.
   */
  private fun sendLifecycleEvent(messageCode: Int) {
    val allMessages = drainQueue()
    allMessages.add(Message.obtain(null, messageCode, 0, 0))
    sendLifecycleEvents(allMessages)
  }

  /**
   * Sends lifecycle events to the [SessionLifecycleService]. This will only send the latest
   * FOREGROUND and BACKGROUND events to the service that are included in the given list. Running
   * through the full backlog of messages is not useful since the service only cares about the
   * current state and transitions from background -> foreground.
   *
   * Does not send events unless data collection is enabled for at least one subscriber.
   */
  @CanIgnoreReturnValue
  private fun sendLifecycleEvents(messages: List<Message>) =
    CoroutineScope(backgroundDispatcher).launch {
      val subscribers = FirebaseSessionsDependencies.getRegisteredSubscribers()
      if (subscribers.isEmpty()) {
        Log.d(
          TAG,
          "Sessions SDK did not have any dependent SDKs register as dependencies. Events will not be sent.",
        )
      } else if (subscribers.values.none { it.isDataCollectionEnabled }) {
        Log.d(TAG, "Data Collection is disabled for all subscribers. Skipping this Event")
      } else {
        mutableListOf(
            getLatestByCode(messages, SessionLifecycleService.BACKGROUNDED),
            getLatestByCode(messages, SessionLifecycleService.FOREGROUNDED),
          )
          .filterNotNull()
          .sortedBy { it.getWhen() }
          .forEach { sendMessageToServer(it) }
      }
    }

  /** Sends the given [Message] to the [SessionLifecycleService]. */
  private fun sendMessageToServer(msg: Message) {
    if (service != null) {
      try {
        Log.d(TAG, "Sending lifecycle ${msg.what} to service")
        service?.send(msg)
      } catch (e: RemoteException) {
        Log.w(TAG, "Unable to deliver message: ${msg.what}", e)
        queueMessage(msg)
      }
    } else {
      queueMessage(msg)
    }
  }

  /**
   * Queues the given [Message] up for delivery to the [SessionLifecycleService] once the connection
   * is established.
   */
  private fun queueMessage(msg: Message) {
    if (queuedMessages.offer(msg)) {
      Log.d(TAG, "Queued message ${msg.what}. Queue size ${queuedMessages.size}")
    } else {
      Log.d(TAG, "Failed to enqueue message ${msg.what}. Dropping.")
    }
  }

  /** Drains the queue of messages into a new list in a thread-safe manner. */
  private fun drainQueue(): MutableList<Message> {
    val messages = mutableListOf<Message>()
    queuedMessages.drainTo(messages)
    return messages
  }

  /** Gets the message in the given list with the given code that has the latest timestamp. */
  private fun getLatestByCode(messages: List<Message>, msgCode: Int): Message? =
    messages.filter { it.what == msgCode }.maxByOrNull { it.getWhen() }

  companion object {
    const val TAG = "SessionLifecycleClient"

    /**
     * The maximum number of messages that we should queue up for delivery to the
     * [SessionLifecycleService] in the event that we have lost the connection.
     */
    private const val MAX_QUEUED_MESSAGES = 20
  }
}
