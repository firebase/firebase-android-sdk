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

/**
 * Represents a structured object generation response from the on-device model interop layer.
 *
 * @property instance The generated object instance returned directly by the model engine.
 * @property rawJson The raw JSON string representation of the object, if available.
 */
public class GenerateObjectResponseInterop<T>(public val instance: T, public val rawJson: String) {}
