// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.util;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Wraps a ByteBuffer in an InputStream. */
public class ByteBufferInputStream extends InputStream {
  ByteBuffer buffer;

  public ByteBufferInputStream(ByteBuffer buf) {
    this.buffer = buf;
  }

  public int read() {
    if (!buffer.hasRemaining()) {
      return -1;
    }
    return buffer.get() & 0xFF;
  }

  public int read(@NonNull byte[] b, int off, int len) throws IOException {
    if (!buffer.hasRemaining()) {
      return -1;
    }

    len = Math.min(len, buffer.remaining());
    buffer.get(b, off, len);
    return len;
  }
}
