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

import com.google.firebase.Firebase
import com.google.firebase.app
import kotlin.coroutines.CoroutineContext

/** Container for injecting dispatchers. */
internal data class Dispatchers
constructor(
  val blockingDispatcher: CoroutineContext,
  val backgroundDispatcher: CoroutineContext,
) {

  companion object {
    val instance: Dispatchers
      get() = Firebase.app.get(Dispatchers::class.java)
  }
}
