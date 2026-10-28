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
 * Defines a function that the model can use as a tool for on-device inference.
 *
 * @property name The name of the function.
 * @property description The description of what the function does.
 * @property parameters The schema describing the parameters the function accepts.
 */
public class FunctionDeclaration
@JvmOverloads
constructor(
  public val name: String,
  public val description: String,
  public val parameters: FunctionSchema? = null
)

/**
 * Schema defining the structure of function parameters for on-device function calling.
 *
 * @property type The data type of the schema (for example, OBJECT, STRING, INTEGER, BOOLEAN,
 * ARRAY).
 * @property description An optional description of what the value represents.
 * @property properties The map of property names to schemas when type is OBJECT.
 * @property required The list of required property names when type is OBJECT.
 * @property enum The list of allowed string values when applicable.
 * @property nullable Indicates whether the value can be null.
 * @property items The schema for array elements when type is ARRAY.
 */
public class FunctionSchema
@JvmOverloads
constructor(
  public val type: String,
  public val description: String? = null,
  public val properties: Map<String, FunctionSchema>? = null,
  public val required: List<String>? = null,
  public val enum: List<String>? = null,
  public val nullable: Boolean? = null,
  public val items: FunctionSchema? = null
)
