/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts an array of bytes to an 8-byte long value, given a particular byte order.
 */
public class ReferenceBytesConverter {

  private final ByteOrder byteOrder;

  public ReferenceBytesConverter(ByteOrder byteOrder) {
    this.byteOrder = byteOrder;
  }

  public long asLongValue(byte[] referenceBytes) {
    return referenceBytesAsLong(referenceBytes, byteOrder);
  }

  private static long referenceBytesAsLong(byte[] data, ByteOrder byteOrder) {
    final int dataLen = data.length;
    final byte[] padded = new byte[8];
    int dest = (byteOrder == ByteOrder.BIG_ENDIAN) ? 8 - dataLen : 0;
    System.arraycopy(data, 0, padded, dest, dataLen);
    return ByteBuffer.wrap(padded).order(byteOrder).getLong();
  }
}
