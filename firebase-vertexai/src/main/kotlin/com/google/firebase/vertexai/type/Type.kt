/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.vertexai.type

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.json.JSONObject

/**
 * Represents and passes the type information for an automated function call.
 *
 * @property name: the enum name of the type
 * @property parse: the deserialization function
 */
class FunctionType(val name: String) {
  companion object {
    val STRING = FunctionType("STRING")
    val INTEGER = FunctionType("INTEGER")
    val LONG = FunctionType("INTEGER")
    val NUMBER = FunctionType("NUMBER")
    val BOOLEAN = FunctionType("BOOLEAN")
    val ARRAY =
      FunctionType("ARRAY")
    val OBJECT = FunctionType("OBJECT")
  }
}
