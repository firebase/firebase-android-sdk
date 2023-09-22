// Copyright 2023 Google LLC
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
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BloomFilter {
  private final int bitCount;
  private final ByteString bitmap;
  private final int hashCount;
  private final MessageDigest md5HashMessageDigest;

  /**
   * Creates a new {@link BloomFilter} with the given parameters.
   *
   * @param bitmap the bitmap of the bloom filter; must not be null.
   * @param padding the padding, in bits, of the last byte of the bloom filter; must be between 0
   *     (zero) and 7, inclusive; must be 0 (zero) if {@code bitmap.length==0}.
   * @param hashCount The number of hash functions to use; must be strictly greater than zero; may
   *     be 0 (zero) if and only if {@code bitmap.length==0}.
   */
  public BloomFilter(@NonNull ByteString bitmap, int padding, int hashCount) {
    if (padding < 0 || padding >= 8) {
      throw new IllegalArgumentException("Invalid padding: " + padding);
    }
    if (hashCount < 0) {
      throw new IllegalArgumentException("Invalid hash count: " + hashCount);
    }
    if (bitmap.size() > 0 && hashCount == 0) {
      // Only empty bloom filter can have 0 hash count.
      throw new IllegalArgumentException("Invalid hash count: " + hashCount);
    }
    if (bitmap.size() == 0 && padding != 0) {
      // Empty bloom filter should have 0 padding.
      throw new IllegalArgumentException(
          "Expected padding of 0 when bitmap length is 0, but got " + padding);
    }

    this.bitmap = bitmap;
    this.hashCount = hashCount;
    this.bitCount = bitmap.size() * 8 - padding;
    this.md5HashMessageDigest = createMd5HashMessageDigest();
  }

  /**
   * Creates an instance of {@link BloomFilter} with the given arguments, throwing a well-defined
   * exception if the given arguments do not satisfy the requirements documented in the {@link
   * BloomFilter} constructor.
   */
  public static BloomFilter create(@NonNull ByteString bitmap, int padding, int hashCount)
      throws BloomFilterCreateException {
    if (padding < 0 || padding >= 8) {
      throw new BloomFilterCreateException("Invalid padding: " + padding);
    }
    if (hashCount < 0) {
      throw new BloomFilterCreateException("Invalid hash count: " + hashCount);
    }
    if (bitmap.size() > 0 && hashCount == 0) {
      // Only empty bloom filter can have 0 hash count.
      throw new BloomFilterCreateException("Invalid hash count: " + hashCount);
    }
    if (bitmap.size() == 0 && padding != 0) {
      // Empty bloom filter should have 0 padding.
      throw new BloomFilterCreateException(
          "Expected padding of 0 when bitmap length is 0, but got " + padding);
    }

    return new BloomFilter(bitmap, padding, hashCount);
  }

  /** Exception thrown by {@link #create} if the given arguments are not valid. */
  public static final class BloomFilterCreateException extends Exception {
    public BloomFilterCreateException(String message) {
      super(message);
    }
  }

  int getBitCount() {
    return this.bitCount;
  }

  /**
   * Check whether the given string is a possible member of the bloom filter. It might return false
   * positive result, ie, the given string is not a member of the bloom filter, but the method
   * returned true.
   *
   * @param value the string to be tested for membership.
   * @return true if the given string might be contained in the bloom filter, or false if the given
   *     string is definitely not contained in the bloom filter.
   */
  public boolean mightContain(@NonNull String value) {
    // Empty bitmap should return false on membership check.
    if (this.bitCount == 0) {
      return false;
    }

    byte[] hashedValue = md5HashDigest(value);
    if (hashedValue.length != 16) {
      throw new RuntimeException(
          "Invalid md5 hash array length: " + hashedValue.length + " (expected 16)");
    }

    long hash1 = getLongLittleEndian(hashedValue, 0);
    long hash2 = getLongLittleEndian(hashedValue, 8);

    for (int i = 0; i < this.hashCount; i++) {
      int index = this.getBitIndex(hash1, hash2, i);
      if (!this.isBitSet(index)) {
        return false;
      }
    }
    return true;
  }

  /** Hash a string using md5 hashing algorithm, and return an array of 16 bytes. */
  @NonNull
  private byte[] md5HashDigest(@NonNull String value) {
    return md5HashMessageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
  }

  @NonNull
  private static MessageDigest createMd5HashMessageDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Missing MD5 MessageDigest provider: ", e);
    }
  }

  /** Interpret 8 bytes into a long, using little endian 2’s complement. */
  private static long getLongLittleEndian(@NonNull byte[] bytes, int offset) {
    long result = 0;
    for (int i = 0; i < 8; i++) {
      result |= (bytes[offset + i] & 0xFFL) << (i * 8);
    }
    return result;
  }

  /**
   * Calculate the ith hash value based on the hashed 64 bit unsigned integers, and calculate its
   * corresponding bit index in the bitmap to be checked.
   */
  private int getBitIndex(long hash1, long hash2, int hashIndex) {
    // Calculate hashed value h(i) = h1 + (i * h2).
    // Even though we are interpreting hash1 and hash2 as unsigned, the addition and multiplication
    // operators still perform the correct operation and give the desired overflow behavior.
    long combinedHash = hash1 + (hash2 * hashIndex);
    long modulo = unsignedRemainder(combinedHash, this.bitCount);
    return (int) modulo;
  }

  /**
   * Calculate modulo, where the dividend and divisor are treated as unsigned 64-bit longs.
   *
   * <p>The implementation is taken from <a href=
   * "https://github.com/google/guava/blob/553037486901cc60820ab7dcb38a25b6f34eba43/android/guava/src/com/google/common/primitives/UnsignedLongs.java">Guava</a>,
   * simplified to our needs.
   *
   * <p>
   */
  private static long unsignedRemainder(long dividend, long divisor) {
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long remainder = dividend - quotient * divisor;
    return remainder - (remainder >= divisor ? divisor : 0);
  }

  /** Return whether the bit at the given index in the bitmap is set to 1. */
  private boolean isBitSet(int index) {
    // To retrieve bit n, calculate: (bitmap[n / 8] & (0x01 << (n % 8))).
    byte byteAtIndex = this.bitmap.byteAt(index / 8);
    int offset = index % 8;
    return (byteAtIndex & (0x01 << offset)) != 0;
  }

  @Override
  public String toString() {
    return "BloomFilter{"
        + "hashCount="
        + hashCount
        + ", size="
        + bitCount
        + ", bitmap=\""
        + Base64.encodeToString(bitmap.toByteArray(), Base64.NO_WRAP)
        + "\"}";
  }
}
