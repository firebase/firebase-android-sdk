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

package com.google.mlkit.genai.structuredoutput.annotations

/** Runtime annotation mirror for ML Kit Structured Output class generation. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Generable(public val description: String = "")

/** Runtime annotation mirror for ML Kit Structured Output property/field guidance. */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Guide(
  public val description: String = "",
  public val minimum: Double = -1.0,
  public val maximum: Double = -1.0,
  public val minItems: Int = -1,
  public val maxItems: Int = -1,
  public val format: String = "",
)
