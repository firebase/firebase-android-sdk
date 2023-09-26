package com.google.firebase.sessions

import com.google.firebase.sessions.api.SessionSubscriber
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Interface for the class that maintains all aspects of the App Quality Session for this process
 * that includes data synchronizing across processes, sending events to our backend, and
 * broadcasting AQS related events to listeners
 */
interface SessionMaintainer {

  /** Register a listener for updates to the session being maintained by this class */
  fun register(subscriber: SessionSubscriber)

  /** Start maintaining the session */
  fun start(backgroundDispatcher: CoroutineDispatcher)
}
