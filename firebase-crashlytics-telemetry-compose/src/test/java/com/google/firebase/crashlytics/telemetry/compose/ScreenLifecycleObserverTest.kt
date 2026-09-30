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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ScreenLifecycleObserverTest {
  private val reporter = RecordingScreenReporter()
  private val owner =
    object : LifecycleOwner {
      val registry = LifecycleRegistry.createUnsafe(this)
      override val lifecycle: Lifecycle
        get() = registry
    }
  private val observer = ScreenLifecycleObserver("Profile", "com.example.ProfileScreen", reporter)

  @Test
  fun resume_appears() {
    owner.lifecycle.addObserver(observer)
    owner.registry.currentState = Lifecycle.State.RESUMED

    assertThat(reporter.events).containsExactly("appear:Profile(com.example.ProfileScreen)")
  }

  @Test
  fun resume_withoutScreenClass_appearsWithNameOnly() {
    owner.lifecycle.addObserver(ScreenLifecycleObserver("Profile", reporter))
    owner.registry.currentState = Lifecycle.State.RESUMED

    assertThat(reporter.events).containsExactly("appear:Profile")
  }

  @Test
  fun addedWhileResumed_appearsImmediately() {
    owner.registry.currentState = Lifecycle.State.RESUMED
    owner.lifecycle.addObserver(observer)

    assertThat(reporter.events).containsExactly("appear:Profile(com.example.ProfileScreen)")
  }

  @Test
  fun resumeAgain_appearsAgain() {
    owner.lifecycle.addObserver(observer)
    owner.registry.currentState = Lifecycle.State.RESUMED
    owner.registry.currentState = Lifecycle.State.STARTED
    owner.registry.currentState = Lifecycle.State.RESUMED

    assertThat(reporter.events)
      .containsExactly(
        "appear:Profile(com.example.ProfileScreen)",
        "appear:Profile(com.example.ProfileScreen)",
      )
      .inOrder()
  }

  @Test
  fun startedOnly_reportsNothing() {
    owner.lifecycle.addObserver(observer)
    owner.registry.currentState = Lifecycle.State.STARTED

    assertThat(reporter.events).isEmpty()
  }
}
