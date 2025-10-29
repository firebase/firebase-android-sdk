/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.protobuf.Struct
import org.junit.Test

class QueryResultEncoderUnitTest {

  @Test
  fun `empty struct`() {
    val encodeResult = QueryResultEncoder.encode(Struct.getDefaultInstance())
    val decodeResult = QueryResultDecoder.decode(encodeResult.byteArray, encodeResult.entities)
    decodeResult shouldBe Struct.getDefaultInstance()
  }
}
