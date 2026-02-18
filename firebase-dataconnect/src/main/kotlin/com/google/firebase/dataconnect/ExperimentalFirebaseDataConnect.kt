/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect

/**
 * Marks declarations in the Firebase Data Connect SDK that are **experimental**.
 *
 * A declaration annotated with [ExperimentalFirebaseDataConnect] is "experimental": its signature
 * and/or semantics may change in backwards-incompatible ways at any time without notice, up to and
 * including complete removal. If you have a use case that relies on such a declaration please open
 * a "feature request" issue at
 * [https://github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk)
 * requesting the declaration's promotion from "experimental" to "fully-supported".
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message =
    "This declaration is \"experimental\": its signature and/or semantics " +
      "may change in backwards-incompatible ways at any time without notice, " +
      "up to and including complete removal. " +
      "If you have a use case that relies on this declaration please open a " +
      "\"feature request\" issue at https://github.com/firebase/firebase-android-sdk " +
      "requesting this declaration's promotion from \"experimental\" to \"fully-supported\"."
)
public annotation class ExperimentalFirebaseDataConnect
