/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.annotations

import com.google.firebase.ai.type.JsonSchema

/**
 * This annotation is used with the `firebase-ai-ksp-processor` plugin to generate [JsonSchema] that
 * match an existing Kotlin class structure. For more info see:
 * [Firebase KSP Processor Readme](https://github.com/firebase/firebase-android-sdk/blob/main/firebase-ai-ksp-processor/README.md)
 *
 * @property description a description of the class to be forwarded to the model. This will override
 * a kDoc description.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Generable(public val description: String = "")
