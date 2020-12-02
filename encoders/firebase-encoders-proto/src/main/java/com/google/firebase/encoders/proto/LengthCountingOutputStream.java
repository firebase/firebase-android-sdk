// Copyright 2020 Google LLC
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

package com.google.firebase.encoders.proto;

import androidx.annotation.NonNull;
import java.io.OutputStream;

/** OutputStream that only keeps track of the number of bytes written into it. */
final class LengthCountingOutputStream extends OutputStream {
  private long length = 0;

  @Override
  public void write(int b) {
    length++;
  }

  @Override
  public void write(byte[] b) {
    length += b.length;
  }

  @Override
  public void write(@NonNull byte[] b, int off, int len) {
    if ((off < 0)
        || (off > b.length)
        || (len < 0)
        || ((off + len) > b.length)
        || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    }
    length += len;
  }

  long getLength() {
    return length;
  }
}
