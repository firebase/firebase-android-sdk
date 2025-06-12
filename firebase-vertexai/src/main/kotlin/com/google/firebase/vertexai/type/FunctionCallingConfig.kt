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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The configuration that specifies the function calling behavior.
 *
 * See the static methods in the `companion object` for the list of available behaviors.
 */
@Deprecated(
  """The Vertex AI in Firebase SDK (firebase-vertexai) has been replaced with the FirebaseAI SDK (firebase-ai) to accommodate the evolving set of supported features and services.
For migration details, see the migration guide: https://firebase.google.com/docs/vertex-ai/migrate-to-latest-sdk"""
)
public class FunctionCallingConfig
internal constructor(
  internal val mode: Mode,
  internal val allowedFunctionNames: List<String>? = null
) {

  /** Configuration for dictating when the model should call the attached function. */
  internal enum class Mode {
    /**
     * The default behavior for function calling. The model calls functions to answer queries at its
     * discretion
     */
    AUTO,

    /** The model always predicts a provided function call to answer every query. */
    ANY,

    /**
     * The model will never predict a function call to answer a query. This can also be achieved by
     * not passing any tools to the model.
     */
    NONE,
  }

  @Serializable
  internal data class Internal(
    val mode: Mode,
    @SerialName("allowed_function_names") val allowedFunctionNames: List<String>? = null
  ) {
    @Serializable
    enum class Mode {
      @SerialName("MODE_UNSPECIFIED") UNSPECIFIED,
      AUTO,
      ANY,
      NONE,
    }
  }

  public companion object {
    /**
     * The default behavior for function calling. The model calls functions to answer queries at its
     * discretion.
     */
    @JvmStatic public fun auto(): FunctionCallingConfig = FunctionCallingConfig(Mode.AUTO)

    /** The model always predicts a provided function call to answer every query. */
    @JvmStatic
    @JvmOverloads
    public fun any(allowedFunctionNames: List<String>? = null): FunctionCallingConfig =
      FunctionCallingConfig(Mode.ANY, allowedFunctionNames)

    /**
     * The model will never predict a function call to answer a query. This can also be achieved by
     * not passing any tools to the model.
     */
    @JvmStatic public fun none(): FunctionCallingConfig = FunctionCallingConfig(Mode.NONE)
  }
}
