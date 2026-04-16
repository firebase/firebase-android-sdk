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

package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceGenerativeModelFactory
import com.google.firebase.ai.ondevice.interop.GenerationConfig
import com.google.firebase.ai.ondevice.interop.GenerativeModel
import com.google.mlkit.genai.prompt.Generation

/**
 * Factory class for Firebase AI OnDevice.
 *
 * @hide
 */
internal class FirebaseAIOnDeviceComponent : FirebaseAIOnDeviceGenerativeModelFactory {

  @Deprecated(
    "Use newGenerativeModel(GenerationConfig) instead",
    replaceWith = ReplaceWith("newGenerativeModel(GenerationConfig)")
  )
  override fun newGenerativeModel(): GenerativeModel = newGenerativeModel(null)

  override fun newGenerativeModel(generationConfig: GenerationConfig?): GenerativeModel =
    if (generationConfig == null) {
      GenerativeModelImpl(Generation.getClient())
    } else {
      GenerativeModelImpl(Generation.getClient(generationConfig.toMlKit()))
    }
}
