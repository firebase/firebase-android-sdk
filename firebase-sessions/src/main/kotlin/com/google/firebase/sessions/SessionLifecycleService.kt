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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.app
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Service for monitoring application lifecycle events and determining when/if a new session should
 * be generated. When this happens, the service will broadcast the updated session id to all
 * connected clients.
 */
internal class SessionLifecycleService(
  private var datastore: SessionDatastore = SessionDatastore(Firebase.app.applicationContext)
) : Service() {

  /**
   * Queue of connected clients.
   *
   * Note that this needs to be a [LinkedBlockingQueue] since it is modified in the service [onBind]
   * method and read in the message handler. Although the [IncomingHandler] is guaranteed to execute
   * sequentially on a single thread, the [onBind] method is not guaranteed to run on that same
   * thread.
   */
  private val boundClients = LinkedBlockingQueue<Messenger>()

  /**
   * Flag indicating whether or not the app has ever come into the foreground during the lifetime of
   * the service. If it has not, we can infer that the first foreground event is a cold-start
   */
  private var hasForegrounded: Boolean = false

  /**
   * The timestamp of the last activity lifecycle message we've received from a client. Used to
   * determine when the app has been idle for long enough to require a new session.
   */
  private var lastMsgTimeMs: Long = 0

  /** Most recent session from datastore is updated asynchronously whenever it changes */
  private val currentSessionFromDatastore: AtomicReference<FirebaseSessionsData> = AtomicReference()

  init {
    CoroutineScope(FirebaseSessions.instance.backgroundDispatcher).launch {
      datastore.firebaseSessionDataFlow.collect() { currentSessionFromDatastore.set(it) }
    }
  }

  /**
   * Handler of incoming activity lifecycle events being received from [SessionLifecycleClient]s.
   * All incoming communication from connected clients comes through this class and will be used to
   * determine when new sessions should be created.
   */
  // TODO(rothbutter) there's a warning that this needs to be static and leaks may occur. Need to
  // look in to this
  internal inner class IncomingHandler(
    context: Context,
    private val appContext: Context = context.applicationContext,
  ) : Handler(Looper.getMainLooper()) { // TODO(rothbutter) probably want to use our own executor

    override fun handleMessage(msg: Message) {
      if (lastMsgTimeMs > msg.getWhen()) {
        Log.i(TAG, "Received old message $msg. Ignoring")
        return
      }
      when (msg.what) {
        FOREGROUNDED -> handleForegrounding(msg)
        BACKGROUNDED -> handleBackgrounding(msg)
        else -> {
          Log.w(TAG, "Received unexpected event from the SessionLifecycleClient: $msg")
          super.handleMessage(msg)
        }
      }
      lastMsgTimeMs = msg.getWhen()
      updateSessionStorage(lastMsgTimeMs) // TODO(rothbutter) don't think we need this, eh?
    }
  }

  /** Called when a new [SessionLifecycleClient] binds to this service. */
  override fun onBind(intent: Intent): IBinder? {
    Log.i(TAG, "Service bound")
    val messenger = Messenger(IncomingHandler(this))
    val callbackMessenger = getClientCallback(intent)
    if (callbackMessenger != null) {
      boundClients.add(callbackMessenger)
      maybeSendSessionToClient(callbackMessenger)
      Log.i(TAG, "Stored callback to $callbackMessenger. Size: ${boundClients.size}")
    }
    return messenger.binder
  }

  /**
   * Handles a foregrounding event by any activity owned by the aplication as specified by the given
   * [Message]. This will determine if the foregrounding should result in the creation of a new
   * session.
   */
  private fun handleForegrounding(msg: Message) {
    Log.i(TAG, "Activity foregrounding at ${msg.getWhen()}")
    if (!hasForegrounded) {
      Log.i(TAG, "Cold start detected.")
      hasForegrounded = true
      broadcastSession()
      updateSessionStorage(SessionGenerator.instance.currentSession.sessionId, msg.getWhen())
    } else if (msg.getWhen() - lastMsgTimeMs > MAX_BACKGROUND_MS) {
      Log.i(TAG, "Session too long in background. Creating new session.")
      SessionGenerator.instance.generateNewSession()
      broadcastSession()
      updateSessionStorage(SessionGenerator.instance.currentSession.sessionId, msg.getWhen())
    }
  }

  private fun updateSessionStorage(timeMs: Long) {
    updateSessionStorage(null, timeMs)
  }

  private fun updateSessionStorage(sessionId: String?, timeMs: Long) {
    CoroutineScope(FirebaseSessions.instance.backgroundDispatcher).launch {
      datastore.updateTimestamp(timeMs)
      sessionId?.let { datastore.updateSessionId(it) }
    }
  }

  /**
   * Handles a backgrounding event by any activity owned by the application as specified by the
   * given [Message]. This will keep track of the backgrounding and be used to determine if future
   * foregrounding events should result in the creation of a new session.
   */
  private fun handleBackgrounding(msg: Message) {
    Log.i(TAG, "Activity backgrounding at ${msg.getWhen()}")
  }

  /**
   * Broadcasts the current session to by uploading to Firelog and all sending a message to all
   * connected clients.
   */
  private fun broadcastSession() {
    Log.i(TAG, "Broadcasting new session: ${SessionGenerator.instance.currentSession}")
    boundClients.forEach { maybeSendSessionToClient(it) }
  }

  private fun maybeSendSessionToClient(client: Messenger) {
    if (hasForegrounded) {
      sendSessionToClient(client, SessionGenerator.instance.currentSession.sessionId)
    } else {
      // Send the value from the datastore before the first foregrounding it exists
      val sessionData = currentSessionFromDatastore.get()
      if (sessionData != null) {
        sessionData.sessionId?.let { sendSessionToClient(client, it) }
      }
    }
  }

  /** Sends the current session id to the client connected through the given [Messenger]. */
  private fun sendSessionToClient(client: Messenger, sessionId: String) {
    try {
      val msgData = Bundle().also { it.putString(SESSION_UPDATE_EXTRA, sessionId) }
      client.send(Message.obtain(null, SESSION_UPDATED, 0, 0).also { it.setData(msgData) })
    } catch (e: DeadObjectException) {
      Log.i(TAG, "Removing dead client from list: $client")
      boundClients.remove(client)
    } catch (e: Exception) {
      Log.e(TAG, "Unable to push new session to $client.", e)
    }
  }

  /**
   * Extracts the callback [Messenger] from the given [Intent] which will be used to push session
   * updates back to the [SessionLifecycleClient] that created this [Intent].
   */
  private fun getClientCallback(intent: Intent): Messenger? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(CLIENT_CALLBACK_MESSENGER, Messenger::class.java)
    } else {
      intent.getParcelableExtra<Messenger>(CLIENT_CALLBACK_MESSENGER)
    }

  internal companion object {
    const val TAG = "SessionLifecycleService"

    /**
     * Key for the [Messenger] callback extra included in the [Intent] used by the
     * [SessionLifecycleClient] to bind to this service.
     */
    const val CLIENT_CALLBACK_MESSENGER = "ClientCallbackMessenger"

    /**
     * Key for the extra String included in the [SESSION_UPDATED] message, sent to all connected
     * clients, containing an updated session id.
     */
    const val SESSION_UPDATE_EXTRA = "SessionUpdateExtra"

    // TODO(bryanatkinson): Swap this out for the value coming from settings
    const val MAX_BACKGROUND_MS = 30000

    /** [Message] code indicating that an application activity has gone to the foreground */
    const val FOREGROUNDED = 1
    /** [Message] code indicating that an application activity has gone to the background */
    const val BACKGROUNDED = 2
    /**
     * [Message] code indicating that a new session has been started, and containing the new session
     * id in the [SESSION_UPDATE_EXTRA] extra field.
     */
    const val SESSION_UPDATED = 3
  }
}
