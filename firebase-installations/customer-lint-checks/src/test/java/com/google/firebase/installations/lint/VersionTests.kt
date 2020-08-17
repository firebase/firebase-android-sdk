// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.installations.lint

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class VersionTests {
    companion object Detector {
        val detector = IncompatibleIidVersionDetector()
    }

    @Test
    fun `isCompatibleVersion with 19_0_0 should be false`() {
        assertThat(detector.isCompatibleVersion("19.0.0")).isFalse()
    }

    @Test
    fun `isCompatibleVersion with 20_0_0 should be false`() {
        assertThat(detector.isCompatibleVersion("20.0.0")).isFalse()
    }

    @Test
    fun `isCompatibleVersion with 20_1_0 should be true`() {
        assertThat(detector.isCompatibleVersion("20.1.0")).isTrue()
    }

    @Test
    fun `isCompatibleVersion with 20_1_1 should be true`() {
        assertThat(detector.isCompatibleVersion("20.1.1")).isTrue()
    }

    @Test
    fun `isCompatibleVersion with 21_0_0 should be true`() {
        assertThat(detector.isCompatibleVersion("21.0.0")).isTrue()
    }
}
