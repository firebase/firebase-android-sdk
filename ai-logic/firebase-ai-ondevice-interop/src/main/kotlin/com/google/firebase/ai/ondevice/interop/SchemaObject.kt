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

import kotlin.reflect.KClass

/**
 * Interop representation of a schema object defining the expected structure of generated content.
 *
 * @property type The type of data (e.g. OBJECT, STRING, INTEGER, BOOLEAN, ARRAY, NUMBER).
 * @property clazz The Kotlin class represented by this schema.
 * @property description Optional description of what the data represents.
 * @property format Optional format pattern for the data.
 * @property nullable Whether the value can be null.
 * @property enum Optional list of valid enum values.
 * @property properties Map of property names to child schema objects for OBJECT types.
 * @property required List of required property names for OBJECT types.
 * @property items Schema of elements for ARRAY types.
 * @property title Optional title of the schema.
 * @property minItems Minimum number of items for ARRAY types.
 * @property maxItems Maximum number of items for ARRAY types.
 * @property minimum Minimum numeric value.
 * @property maximum Maximum numeric value.
 * @property anyOf List of alternative sub-schemas.
 */
public class SchemaObject<T : Any>
public constructor(
  public val type: String,
  public val clazz: KClass<T>,
  public val description: String? = null,
  public val format: String? = null,
  public val nullable: Boolean? = null,
  public val enum: List<String>? = null,
  public val properties: Map<String, SchemaObject<*>>? = null,
  public val required: List<String>? = null,
  public val items: SchemaObject<*>? = null,
  public val title: String? = null,
  public val minItems: Int? = null,
  public val maxItems: Int? = null,
  public val minimum: Double? = null,
  public val maximum: Double? = null,
  public val anyOf: List<SchemaObject<*>>? = null,
) {}
