/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.annotations

import kotlin.RequiresOptIn

/**
 * Marks interfaces and non-final classes that can be freely referenced in users' code but should
 * not be implemented or inherited. The Firebase SDK provides compatibility guarantees for existing
 * signatures of such classes and interfaces; however, new functions or properties can be added to
 * them at any time and without notice.
 */
@MustBeDocumented
@Target() // no direct targets, only argument to @SubclassOptInRequired
@RequiresOptIn(
  message =
    "This class or interface should not be inherited or implemented " +
      "except by the Firebase SDK itself; " +
      "instead, instances should be obtained " +
      "from the appropriate Firebase SDK.",
  level = RequiresOptIn.Level.ERROR,
)
public annotation class InternalForInheritanceFirebaseApi
