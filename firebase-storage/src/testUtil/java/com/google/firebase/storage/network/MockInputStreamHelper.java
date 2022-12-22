// Copyright 2018 Google LLC
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

package com.google.firebase.storage.network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * MockInputStreamHelper is a test helper that allows injection of exceptions at a specified read
 * position.
 */
public class MockInputStreamHelper extends InputStream {
  private final ByteArrayInputStream inputStream;
  private final SortedSet<Integer> injectExceptions;
  private int currentOffset;
  private boolean opened;
  private boolean throwOnNextRead;

  /** @param responseData The byte array that contains the response data. */
  public MockInputStreamHelper(final byte[] responseData) {
    this.inputStream = new ByteArrayInputStream(responseData);
    this.injectExceptions = new TreeSet<>();
  }

  public MockInputStreamHelper injectExceptionAt(int bytePos) {
    if (opened) {
      throw new IllegalStateException("Can't add exception points after reading from stream.");
    }
    injectExceptions.add(bytePos);
    return this;
  }

  @Override
  public int read() throws IOException {
    byte[] b = new byte[1];
    if (read(b, 0, 1) == 1) {
      return b[0];
    }
    return -1;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    opened = true;

    if (throwOnNextRead) {
      throwOnNextRead = false;
      throw new IOException("IOException thrown by test mock.");
    }

    if (!injectExceptions.subSet(currentOffset, currentOffset + len).isEmpty()) {
      throwOnNextRead = true;
      int capAt = injectExceptions.subSet(currentOffset, currentOffset + len).first();
      injectExceptions.remove(capAt);
      len = capAt - currentOffset;
    }

    int bytesRead = inputStream.read(b, off, len);
    currentOffset += Math.max(bytesRead, 0);
    return bytesRead;
  }

  @Override
  public long skip(long n) throws IOException {
    return read(new byte[(int) n]);
  }

  @Override
  public int available() throws IOException {
    opened = true;
    return inputStream.available();
  }
}
