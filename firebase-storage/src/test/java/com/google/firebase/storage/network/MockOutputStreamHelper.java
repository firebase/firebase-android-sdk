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

import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * MockOutputStreamHelper is a test helper that allows injection of exceptions at a specified write
 * position.
 */
public class MockOutputStreamHelper extends OutputStream {
  private final OutputStream outputStream;
  private final SortedSet<Integer> injectExceptions;
  private int currentOffset;

  public MockOutputStreamHelper(final OutputStream outputStream) {
    this.outputStream = outputStream;
    this.injectExceptions = new TreeSet<>();
    this.currentOffset = 0;
  }

  public void injectExceptionAt(int bytePos) {
    injectExceptions.add(bytePos);
  }

  @Override
  public void write(int i) throws IOException {
    write(new byte[] {(byte) i});
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (!injectExceptions.subSet(currentOffset, currentOffset + len).isEmpty()) {
      throw new IOException("Exception thrown by mock");
    }
    outputStream.write(b, off, len);
    currentOffset += len;
  }

  @Override
  public void flush() throws IOException {
    outputStream.flush();
  }

  @Override
  public void close() throws IOException {
    outputStream.close();
  }
}
