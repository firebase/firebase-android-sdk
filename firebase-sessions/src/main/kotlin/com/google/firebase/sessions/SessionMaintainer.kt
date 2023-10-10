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
import com.google.firebase.FirebaseApp
import com.google.firebase.app

/** Maintainer for Sessions. */
internal class SessionMaintainer {

  internal companion object {
    @JvmStatic
    val instance: SessionMaintainer
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): SessionMaintainer = app.get(SessionMaintainer::class.java)
  }
}
