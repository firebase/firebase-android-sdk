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

package com.google.firebase.sessions.testing

import com.google.firebase.sessions.UuidGenerator
import java.util.UUID

/** Fake implementation of [UuidGenerator] to provide uuids of the given names in order. */
internal class FakeUuidGenerator(private val names: List<String> = listOf(UUID_1, UUID_2, UUID_3)) :
  UuidGenerator {
  private var index = -1

  override fun next(): UUID {
    index = (index + 1).coerceAtMost(names.size - 1)
    return UUID.fromString(names[index])
  }

  companion object {
    const val UUID_1 = "11111111-1111-1111-1111-111111111111"
    const val UUID_2 = "22222222-2222-2222-2222-222222222222"
    const val UUID_3 = "CCCCCCCC-CCCC-CCCC-CCCC-CCCCCCCCCCCC"
  }
}
