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

package com.google.firebase.crashlytics.telemetry.compose

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScreenNameTest {
  @Test
  fun destinationWithRoute_usesRoutePattern() {
    assertThat(screenName(route = "profile/{userId}", displayName = "com.example:id/profile"))
      .isEqualTo("profile/{userId}")
  }

  @Test
  fun destinationWithoutRoute_usesDisplayName() {
    assertThat(screenName(route = null, displayName = "com.example:id/home"))
      .isEqualTo("com.example:id/home")
  }
}
