// Copyright 2025 Google LLC
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

import com.google.protobuf.ByteString;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Represents a BSON Binary data type in Firestore documents. */
public final class BsonBinaryData {
  private final int subtype;
  private final ByteString data;

  private BsonBinaryData(int subtype, @Nonnull ByteString data) {
    // By definition the subtype should be 1 byte and should therefore
    // have a value between 0 and 255
    if (subtype < 0 || subtype > 255) {
      throw new IllegalArgumentException(
          "The subtype for BsonBinaryData must be a value in the inclusive [0, 255] range.");
    }
    this.subtype = subtype;
    this.data = data;
  }

  /**
   * Creates a new BsonBinaryData instance from the provided ByteString and subtype.
   *
   * @param subtype The subtype to use for this instance.
   * @param byteString The byteString to use for this instance.
   * @return The new BsonBinaryData instance
   */
  @Nonnull
  public static BsonBinaryData fromByteString(int subtype, @Nonnull ByteString byteString) {
    return new BsonBinaryData(subtype, byteString);
  }

  /**
   * Creates a new BsonBinaryData instance from the provided bytes and subtype. Makes a copy of the
   * bytes passed in.
   *
   * @param subtype The subtype to use for this instance.
   * @param bytes The bytes to use for this instance.
   * @return The new BsonBinaryData instance
   */
  @Nonnull
  public static BsonBinaryData fromBytes(int subtype, @Nonnull byte[] bytes) {
    return new BsonBinaryData(subtype, ByteString.copyFrom(bytes));
  }

  /**
   * Returns the underlying data as a ByteString.
   *
   * @return The data as a ByteString.
   */
  @Nonnull
  public ByteString dataAsByteString() {
    return data;
  }

  /**
   * Returns a copy of the underlying data as a byte[] array.
   *
   * @return The data as a byte[] array.
   */
  @Nonnull
  public byte[] dataAsBytes() {
    return data.toByteArray();
  }

  /**
   * Returns the subtype of this binary data.
   *
   * @return The subtype of the binary data.
   */
  public int subtype() {
    return subtype;
  }

  /**
   * Returns true if this BsonBinaryData is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this BsonBinaryData is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BsonBinaryData)) {
      return false;
    }
    BsonBinaryData other = (BsonBinaryData) obj;
    return subtype == other.subtype && data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subtype, data);
  }

  @Override
  public String toString() {
    return "BsonBinaryData{subtype=" + subtype + ", data=" + data + "}";
  }
}
