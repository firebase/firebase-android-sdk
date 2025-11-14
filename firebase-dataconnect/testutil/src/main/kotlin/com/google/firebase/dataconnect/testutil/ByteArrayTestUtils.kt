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

interface BuildByteArrayDSL {
  fun putChar(value: Char)
  fun putInt(value: Int)
  fun put(value: Byte)
  fun put(value: ByteArray)
  fun put(value: ByteBuffer)
}

fun buildByteArray(byteOrder: ByteOrder? = null, block: BuildByteArrayDSL.() -> Unit): ByteArray =
  ByteArrayOutputStream().use { byteArrayOutputStream ->
    Channels.newChannel(byteArrayOutputStream).use { channel ->
      block(BuildByteArrayDSLImpl(byteOrder, channel))
    }
    byteArrayOutputStream.toByteArray()
  }

private class BuildByteArrayDSLImpl(
  byteOrder: ByteOrder?,
  private val channel: WritableByteChannel
) : BuildByteArrayDSL {
  private val byteBuffer =
    ByteBuffer.allocate(8).also {
      if (byteOrder !== null) {
        it.order(byteOrder)
      }
    }

  override fun putChar(value: Char) {
    write { putChar(value) }
  }

  override fun putInt(value: Int) {
    write { putInt(value) }
  }

  override fun put(value: Byte) {
    write { put(value) }
  }

  override fun put(value: ByteArray) {
    put(ByteBuffer.wrap(value))
  }

  override fun put(value: ByteBuffer) {
    write { channel.write(value) }
  }

  private inline fun write(block: ByteBuffer.() -> Unit) {
    byteBuffer.clear()
    block(byteBuffer)
    byteBuffer.flip()
    channel.write(byteBuffer)
  }
}
