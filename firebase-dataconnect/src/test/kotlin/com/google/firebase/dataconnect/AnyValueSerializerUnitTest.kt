/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.serializers.AnyValueSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.StructureKind
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class AnyValueSerializerUnitTest {

  @Test
  fun `descriptor should have expected values`() {
    assertSoftly {
      AnyValueSerializer.descriptor.serialName shouldBe "com.google.firebase.dataconnect.AnyValue"
      AnyValueSerializer.descriptor.kind shouldBe StructureKind.CLASS
    }
  }

  @Test
  fun `serialize() should throw UnsupportedOperationException`() {
    shouldThrow<UnsupportedOperationException> { AnyValueSerializer.serialize(mockk(), mockk()) }
  }

  @Test
  fun `deserialize() should throw UnsupportedOperationException`() {
    shouldThrow<UnsupportedOperationException> { AnyValueSerializer.deserialize(mockk()) }
  }
}
