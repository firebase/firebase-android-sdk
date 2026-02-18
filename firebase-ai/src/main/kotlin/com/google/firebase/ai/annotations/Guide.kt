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

/**
 * This annotation is used with the `firebase-ai-ksp-processor` plugin to provide extra information
 * on generated classes and fields.
 * @property description a description of the field
 * @property minimum the minimum value (inclusive) which the numeric field may contain
 * @property maximum the maximum value (inclusive) which the numeric field may contain
 * @property minItems the minimum number of items in a list
 * @property maxItems the maximum number of items in a list
 * @property format the format that a field must conform to
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class Guide(
  public val description: String = "",
  public val minimum: Double = -1.0,
  public val maximum: Double = -1.0,
  public val minItems: Int = -1,
  public val maxItems: Int = -1,
  public val format: String = "",
)
