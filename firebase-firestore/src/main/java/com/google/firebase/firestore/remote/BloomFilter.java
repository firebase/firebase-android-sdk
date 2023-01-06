// Copyright 2022 Google LLC
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

package com.google.firebase.firestore.remote;

import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BloomFilter {
  private final int size;
  private final byte[] bitmap;
  private final int hashCount;

  public BloomFilter(@NonNull byte[] bitmap, int padding, int hashCount) {
    if (padding < 0 || padding >= 8) {
      throw new IllegalArgumentException("Invalid padding: " + padding);
    }

    if (bitmap.length > 0) {
      // Only empty bloom filter can have 0 hash count.
      if (hashCount <= 0) {
        throw new IllegalArgumentException("Invalid hash count: " + hashCount);
      }
    } else {
      if (hashCount < 0) {
        throw new IllegalArgumentException("Invalid hash count: " + hashCount);
      }

      // Empty bloom filter should have 0 padding.
      if (padding != 0) {
        throw new IllegalArgumentException("Invalid padding when bitmap length is 0: " + padding);
      }
    }
    this.bitmap = bitmap;
    this.hashCount = hashCount;
    this.size = bitmap.length * 8 - padding;
  }

  /** Return if a bloom filter is empty. */
  @VisibleForTesting
  boolean isEmpty() {
    return this.size == 0;
  }

  public boolean mightContain(@NonNull String value) {
    // Empty bitmap or empty value should always return false on membership check.
    if (this.isEmpty() || value.isEmpty()) {
      return false;
    }

    byte[] md5HashedValue = md5Hash(value);
    if (md5HashedValue.length != 16) {
      throw new RuntimeException(
          "Invalid md5HashedValue.length: " + md5HashedValue.length + " (expected 16)");
    }

    long hash1 = getLongLittleEndian(md5HashedValue, 0);
    long hash2 = getLongLittleEndian(md5HashedValue, 8);

    for (int i = 0; i < this.hashCount; i++) {
      int index = this.getBitIndex(hash1, hash2, i);
      if (!this.isBitSet(index)) {
        return false;
      }
    }
    return true;
  }

  @NonNull
  private static byte[] md5Hash(@NonNull String value) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Missing MD5 MessageDigest provider.", e);
    }
    return digest.digest(value.getBytes());
  }

  // Interpret 8 bytes into a long, using little endian 2’s complement.
  private static long getLongLittleEndian(@NonNull byte[] bytes, int offset) {
    long result = 0;
    for (int i = 0; i < 8 && i < bytes.length; i++) {
      result |= (bytes[offset + i] & 0xFFL) << (i * 8);
    }
    return result;
  }

  // Calculate the ith hash value based on the hashed 64bit integers,
  // and calculate its corresponding bit index in the bitmap to be checked.
  private int getBitIndex(long hash1, long hash2, int index) {
    // Calculate hashed value h(i) = h1 + (i * h2).
    long combinedHash = hash1 + (hash2 * index);
    long mod = unsignedRemainder(combinedHash, this.size);
    return (int) mod;
  }

  private static long unsignedRemainder(long dividend, int divisor) {
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long remainder = dividend - quotient * divisor;
    return remainder - (remainder >= divisor ? divisor : 0);
  }

  // Return whether the bit on the given index in the bitmap is set to 1.
  private boolean isBitSet(int index) {
    // To retrieve bit n, calculate: (bitmap[n / 8] & (0x01 << (n % 8))).
    byte byteAtIndex = this.bitmap[(index / 8)];
    int offset = index % 8;
    return (byteAtIndex & (0x01 << offset)) != 0;
  }

  @Override
  public String toString() {
    return "BloomFilter{"
        + ", hashCount="
        + hashCount
        + ", size="
        + size
        + "bitmap="
        + Base64.encodeToString(bitmap, Base64.NO_WRAP)
        + '}';
  }
}
