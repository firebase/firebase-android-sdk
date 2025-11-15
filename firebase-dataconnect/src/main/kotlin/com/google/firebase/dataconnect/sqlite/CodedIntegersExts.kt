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

package com.google.firebase.dataconnect.sqlite

import java.nio.ByteBuffer

internal object CodedIntegersExts {

  fun ByteBuffer.putSInt32(value: Int): ByteBuffer = apply { CodedIntegers.putSInt32(value, this) }

  fun ByteBuffer.getSInt32(): Int = CodedIntegers.getSInt32(this)

  fun ByteBuffer.putSInt64(value: Long): ByteBuffer = apply { CodedIntegers.putSInt64(value, this) }

  fun ByteBuffer.getSInt64(): Long = CodedIntegers.getSInt64(this)

  fun ByteBuffer.putUInt32(value: Int): ByteBuffer = apply { CodedIntegers.putUInt32(value, this) }

  fun ByteBuffer.getUInt32(): Int = CodedIntegers.getUInt32(this)

  fun ByteBuffer.putUInt64(value: Long): ByteBuffer = apply { CodedIntegers.putUInt64(value, this) }

  fun ByteBuffer.getUInt64(): Long = CodedIntegers.getUInt64(this)
}
