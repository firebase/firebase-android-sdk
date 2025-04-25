/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.ai

import androidx.annotation.GuardedBy
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Provider
import kotlin.coroutines.CoroutineContext

/**
 * Multi-resource container for Firebase AI.
 *
 * @hide
 */
internal class FirebaseAIMultiResourceComponent(
  private val app: FirebaseApp,
  @Blocking val blockingDispatcher: CoroutineContext,
  private val appCheckProvider: Provider<InteropAppCheckTokenProvider>,
  private val internalAuthProvider: Provider<InternalAuthProvider>,
) {

  @GuardedBy("this") private val instances: MutableMap<String, FirebaseAI> = mutableMapOf()

  fun get(backend: GenerativeBackend): FirebaseAI =
    synchronized(this) {
      instances[backend.location]
        ?: FirebaseAI(
            app,
            backend,
            blockingDispatcher,
            appCheckProvider,
            internalAuthProvider,
          )
          .also { instances[backend.location] = it }
    }
}
