/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseSessionsProviderTest {
  @Before
  fun setUp() {
    FirebaseSessionsProvider.reset()
  }

  @After
  fun cleanUp() {
    FirebaseSessionsProvider.reset()
  }

  @Test
  fun isColdStart() {
    // It is not a cold start when the provider has not been created yet.
    assertThat(FirebaseSessionsProvider.isColdStart()).isFalse()

    // Create the provider, this happens in an app exactly once regardless of multiple processes.
    FirebaseSessionsProvider().onCreate()

    // The first time we check, it's a cold start.
    assertThat(FirebaseSessionsProvider.isColdStart()).isTrue()

    // Every check after, it's not a cold start.
    assertThat(FirebaseSessionsProvider.isColdStart()).isFalse()
    assertThat(FirebaseSessionsProvider.isColdStart()).isFalse()
  }
}
