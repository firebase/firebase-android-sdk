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
import java.util.Objects;

/**
 * Immutable class representing an array of bytes in Cloud Firestore.
 *
 * <p>Can represent either standard binary data or BSON binary data with a specific subtype.
 */
public class Blob implements Comparable<Blob> {
  private final ByteString bytes;
  private final int subtype;
  private final boolean isBson;

  private Blob(ByteString bytes, int subtype, boolean isBson) {
    this.bytes = bytes;
    this.subtype = subtype;
    this.isBson = isBson;
  }

  /**
   * Creates a new {@code Blob} instance from the provided bytes. Will make a copy of the bytes
   * passed in.
   *
   * <p>By default, the subtype of a standard Blob is 0.
   *
   * @param bytes The bytes to use for this {@code Blob} instance.
   * @return The new {@code Blob} instance
   */
  @NonNull
  public static Blob fromBytes(@NonNull byte[] bytes) {
    checkNotNull(bytes, "Provided bytes array must not be null.");
    return new Blob(ByteString.copyFrom(bytes), 0, false);
  }

  /** @hide */
  @NonNull
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static Blob fromByteString(@NonNull ByteString bytes) {
    checkNotNull(bytes, "Provided ByteString must not be null.");
    return new Blob(bytes, 0, false);
  }

  /**
   * Creates a BSON Binary type Blob with subtype 0. Will make a copy of the bytes passed in.
   *
   * @param bytes The bytes to use for this BSON binary {@code Blob} instance.
   * @return The new BSON binary {@code Blob} instance
   */
  @NonNull
  public static Blob createBsonBinary(@NonNull byte[] bytes) {
    checkNotNull(bytes, "Provided bytes array must not be null.");
    return new Blob(ByteString.copyFrom(bytes), 0, true);
  }

  /**
   * Creates a BSON Binary type Blob with the specified subtype. Will make a copy of the bytes
   * passed in.
   *
   * @param subtype The BSON binary subtype. Must be in the [0, 255] range.
   * @param bytes The bytes to use for this BSON binary {@code Blob} instance.
   * @return The new BSON binary {@code Blob} instance
   */
  @NonNull
  public static Blob createBsonBinary(int subtype, @NonNull byte[] bytes) {
    checkNotNull(bytes, "Provided bytes array must not be null.");
    if (subtype < 0 || subtype > 255) {
      throw new IllegalArgumentException(
          "The subtype for Blob must be a value in the inclusive [0, 255] range.");
    }
    return new Blob(ByteString.copyFrom(bytes), subtype, true);
  }

  /** @hide */
  @NonNull
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static Blob createBsonBinary(int subtype, @NonNull ByteString bytes) {
    checkNotNull(bytes, "Provided ByteString must not be null.");
    if (subtype < 0 || subtype > 255) {
      throw new IllegalArgumentException(
          "The subtype for Blob must be a value in the inclusive [0, 255] range.");
    }
    return new Blob(bytes, subtype, true);
  }

  /**
   * Returns the subtype of this BSON binary data. Returns 0 for standard non-BSON Blobs.
   *
   * @return The BSON binary subtype.
   */
  public int getSubType() {
    return subtype;
  }

  /**
   * Returns true if this Blob is a BSON binary data type.
   *
   * @return Whether this Blob represents BSON binary data.
   */
  public boolean isBson() {
    return isBson;
  }

  /** @return The bytes of this blob as a new byte[] array. */
  @NonNull
  public byte[] toBytes() {
    return bytes.toByteArray();
  }

  @Override
  @NonNull
  public String toString() {
    return "Blob { bytes="
        + Util.toDebugString(bytes)
        + ", isBson="
        + isBson
        + ", subtype="
        + subtype
        + " }";
  }

  /** @hide */
  @NonNull
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public ByteString toByteString() {
    return bytes;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Blob)) {
      return false;
    }
    Blob o = (Blob) other;
    return subtype == o.subtype && bytes.equals(o.bytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes, subtype);
  }

  @Override
  public int compareTo(@NonNull Blob other) {
    int subtypeCompare = Integer.compare(subtype, other.subtype);
    if (subtypeCompare != 0) {
      return subtypeCompare;
    }
    return Util.compareByteStrings(bytes, other.bytes);
  }
}
