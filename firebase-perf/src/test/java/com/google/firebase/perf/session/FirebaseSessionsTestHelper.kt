/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.perf.session

import com.google.firebase.perf.util.Clock

fun createTestSession(suffix: Int): PerfSession {
  // TODO(b/394127311): Add a method to verify legacy behavior.
  // only hex characters and so it's AQS.
  return PerfSession(testSessionId(suffix), Clock())
}

fun testSessionId(suffix: Int): String = "abc$suffix"

fun testLegacySessionId(suffix: Int): String = "zabc$suffix"
