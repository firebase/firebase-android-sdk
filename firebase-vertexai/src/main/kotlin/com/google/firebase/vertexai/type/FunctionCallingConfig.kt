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

/**
 * Contains configuration for function calling from the model. This can be used to force function
 * calling predictions or disable them.
 *
 * @param mode The function calling mode of the model
 */
class FunctionCallingConfig(val mode: Mode) {

  /** Configuration for dictating when the model should call the attached function. */
  enum class Mode {
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
    NONE
  }
}
