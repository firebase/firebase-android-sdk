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

package com.google.firebase.ai.type

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contains configuration for the function calling tools of the model. This can be used to change
 * when the model can predict function calls.
 *
 * @param functionCallingConfig The config for function calling
 */
public class ToolConfig(internal val functionCallingConfig: FunctionCallingConfig?) {

  internal fun toInternal() =
    Internal(
      functionCallingConfig?.let {
        FunctionCallingConfig.Internal(
          when (it.mode) {
            FunctionCallingConfig.Mode.ANY -> FunctionCallingConfig.Internal.Mode.ANY
            FunctionCallingConfig.Mode.AUTO -> FunctionCallingConfig.Internal.Mode.AUTO
            FunctionCallingConfig.Mode.NONE -> FunctionCallingConfig.Internal.Mode.NONE
          },
          it.allowedFunctionNames
        )
      }
    )

  @Serializable
  internal data class Internal(
    @SerialName("function_calling_config")
    val functionCallingConfig: FunctionCallingConfig.Internal?
  )
}
