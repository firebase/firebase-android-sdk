// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime;

import androidx.annotation.NonNull;
import com.google.android.datatransport.Encoding;
import java.util.Arrays;

/**
 * Represents encoded payloads.
 *
 * <p>It is essentially a pair of {@code (encoding, bytes)}, where {@code bytes} are encoded using
 * {@code encoding}.
 *
 * <p>Overrides {@link #equals(Object)} and {@link #hashCode()} to enable value semantics.
 */
public final class EncodedPayload {
  private final Encoding encoding;
  private final byte[] bytes;

  public EncodedPayload(@NonNull Encoding encoding, @NonNull byte[] bytes) {
    if (encoding == null) {
      throw new NullPointerException("encoding is null");
    }
    if (bytes == null) {
      throw new NullPointerException("bytes is null");
    }
    this.encoding = encoding;
    this.bytes = bytes;
  }

  public Encoding getEncoding() {
    return encoding;
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EncodedPayload)) return false;

    EncodedPayload that = (EncodedPayload) o;

    if (!encoding.equals(that.encoding)) return false;
    return Arrays.equals(bytes, that.bytes);
  }

  @Override
  public int hashCode() {
    int h = 1000003;
    h ^= encoding.hashCode();
    h *= 1000003;
    h ^= Arrays.hashCode(bytes);
    return h;
  }

  @Override
  public String toString() {
    return "EncodedPayload{" + "encoding=" + encoding + ", bytes=[...]}";
  }
}
