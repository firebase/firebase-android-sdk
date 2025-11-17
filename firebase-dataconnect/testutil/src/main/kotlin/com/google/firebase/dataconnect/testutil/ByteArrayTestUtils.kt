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

package com.google.firebase.dataconnect.testutil

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel

fun buildByteArray(byteOrder: ByteOrder? = null, block: BuildByteArrayDSL.() -> Unit): ByteArray =
  ByteArrayOutputStream().use { byteArrayOutputStream ->
    Channels.newChannel(byteArrayOutputStream).use { channel ->
      block(BuildByteArrayDSL(byteOrder, channel))
    }
    byteArrayOutputStream.toByteArray()
  }

class BuildByteArrayDSL(byteOrder: ByteOrder?, val channel: WritableByteChannel) {
  val byteBuffer: ByteBuffer =
    ByteBuffer.allocate(8).also {
      if (byteOrder !== null) {
        it.order(byteOrder)
      }
    }

  fun putChar(value: Char) {
    write { it.putChar(value) }
  }

  fun putInt(value: Int) {
    write { it.putInt(value) }
  }

  fun putDouble(value: Double) {
    write { it.putDouble(value) }
  }

  fun put(value: Byte) {
    write { it.put(value) }
  }

  fun put(value: ByteArray) {
    put(ByteBuffer.wrap(value))
  }

  fun put(value: ByteBuffer) {
    write { channel.write(value) }
  }

  inline fun write(block: (ByteBuffer) -> Unit): Int {
    byteBuffer.clear()
    block(byteBuffer)
    byteBuffer.flip()
    channel.write(byteBuffer)
    return byteBuffer.position()
  }
}
