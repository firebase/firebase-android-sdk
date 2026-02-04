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

package com.google.firebase.ai.hybrid

import android.util.Log
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException

/**
 * Router responsible for directing inference requests to either on-device or in-cloud
 * implementations based on the provided [InferenceMode].
 */
public class HybridRouter(public val inferenceMode: InferenceMode) {

  /**
   * Routes the execution to the appropriate callback based on the [inferenceMode].
   *
   * @param onDeviceCallback The logic to execute for on-device inference.
   * @param inCloudCallback The logic to execute for in-cloud inference.
   */
  public fun route(onDeviceCallback: () -> Unit, inCloudCallback: () -> Unit) {
    when (inferenceMode) {
      InferenceMode.ONLY_ON_DEVICE -> onDeviceCallback()
      InferenceMode.ONLY_IN_CLOUD -> inCloudCallback()
      InferenceMode.PREFER_ON_DEVICE -> {
        try {
          onDeviceCallback()
        } catch (e: FirebaseAIOnDeviceException) {
          Log.i(TAG, "On-device inference failed, falling back to in-cloud.", e)
          inCloudCallback()
        }
      }
      // TODO: Implement logic to route to on-device if device is offline.
      InferenceMode.PREFER_IN_CLOUD -> inCloudCallback()
    }
  }

  public companion object {
    private const val TAG = "HybridRouter"
  }
}
