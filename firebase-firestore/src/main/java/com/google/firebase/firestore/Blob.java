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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firebase.firestore.util.Util;
import com.google.protobuf.ByteString;

/** Immutable class representing an array of bytes in Cloud Firestore. */
public class Blob implements Comparable<Blob> {
  private final ByteString bytes;

  private Blob(ByteString bytes) {
    this.bytes = bytes;
  }

  /**
   * Creates a new {@code Blob} instance from the provided bytes. Will make a copy of the bytes
   * passed in.
   *
   * @param bytes The bytes to use for this {@code Blob} instance.
   * @return The new {@code Blob} instance
   */
  @NonNull
  public static Blob fromBytes(@NonNull byte[] bytes) {
    checkNotNull(bytes, "Provided bytes array must not be null.");
    return new Blob(ByteString.copyFrom(bytes));
  }

  /** @hide */
  @NonNull
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static Blob fromByteString(@NonNull ByteString bytes) {
    checkNotNull(bytes, "Provided ByteString must not be null.");
    return new Blob(bytes);
  }

  /** @return The bytes of this blob as a new byte[] array. */
  @NonNull
  public byte[] toBytes() {
    return bytes.toByteArray();
  }

  @Override
  @NonNull
  public String toString() {
    return "Blob { bytes=" + Util.toDebugString(bytes) + " }";
  }

  /** @hide */
  @NonNull
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public ByteString toByteString() {
    return bytes;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof Blob && bytes.equals(((Blob) other).bytes);
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  @Override
  public int compareTo(@NonNull Blob other) {
    return Util.compareByteStrings(bytes, other.bytes);
  }
}
