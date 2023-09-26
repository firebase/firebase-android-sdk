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
