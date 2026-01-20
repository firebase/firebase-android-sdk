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

package com.google.firebase.ai.ondevice.interop

/** Parent class for any errors that occur from the Firebase AI OnDevice SDK. */
public abstract class FirebaseAIOnDeviceException
internal constructor(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause) {}

/**
 * An operation has been requested, but device doesn't support local models, or they are not
 * available.
 *
 * Prefer using the corresponding `isAvailable()` method on the model to check the status before
 * trying to use it.
 */
public class FirebaseAIOnDeviceNotAvailable
internal constructor(message: String, cause: Throwable? = null) :
  FirebaseAIOnDeviceException(message, cause)
