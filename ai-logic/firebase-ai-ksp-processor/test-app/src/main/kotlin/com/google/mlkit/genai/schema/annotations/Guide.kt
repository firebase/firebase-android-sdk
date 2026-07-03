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

package com.google.mlkit.genai.schema.annotations

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
public annotation class Guide(
  public val description: String = "",
  public val maxItems: Int = -1,
  public val minItems: Int = -1,
  public val maximum: Double = Double.NaN,
  public val minimum: Double = Double.NaN,
  public val enumValues: Array<String> = []
)
