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

import androidx.annotation.NonNull;
import com.google.firebase.firestore.util.Logger;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class BloomFilter {
  private static final String TAG = "BloomFilter";
  private static final BigInteger MAX_64_BIT_UNSIGNED_INTEGER =
      new BigInteger("ffffffffffffffff", 16);

  private final int size;
  private final byte[] bitmap;
  private final int hashCount;

  public BloomFilter(@NonNull byte[] bitmap, @NonNull int padding, @NonNull int hashCount) {
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

  @NonNull
  public int getSize() {
    return this.size;
  }

  public boolean isEmpty() {
    return this.size == 0;
  }

  public boolean mightContain(@NonNull String value) {
    // Empty bitmap or empty value should always return false on membership check.
    if (this.isEmpty() || value.isEmpty()) {
      return false;
    }

    byte[] md5HashedValue = this.MD5Hash(value);
    if (md5HashedValue == null || md5HashedValue.length != 16) {
      //
      return false;
    }

    long[] hashedValues = get64BitUnsignedInt(md5HashedValue);

    for (int i = 0; i < this.hashCount; i++) {
      int index = this.getBitIndex(hashedValues, i);
      if (!this.isBitSet(index)) {
        return false;
      }
    }
    return true;
  }

  public static byte[] MD5Hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(value.getBytes());
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      Logger.warn(TAG, "Could not create hashing algorithm: MD5.", e);
      return null;
    }
  }

  // Interpret the 16 bytes array as two 64-bit unsigned integers, encoded using
  // 2’s complement using little endian.
  private static long[] get64BitUnsignedInt(byte[] bytes) {
    byte[] chunk1 = Arrays.copyOfRange(bytes, 0, 8);
    byte[] chunk2 = Arrays.copyOfRange(bytes, 8, 16);

    long num1 = ByteBuffer.wrap(chunk1).order(ByteOrder.LITTLE_ENDIAN).getLong();
    long num2 = ByteBuffer.wrap(chunk2).order(ByteOrder.LITTLE_ENDIAN).getLong();

    return new long[] {num1, num2};
  }

  // Calculate the ith hash value based on the hashed 64bit integers,
  // and calculate its corresponding bit index in the bitmap to be checked.
  private int getBitIndex(long[] numbers, int index) {
    BigInteger num1 = new BigInteger(Long.toUnsignedString(numbers[0]));
    BigInteger num2 = new BigInteger(Long.toUnsignedString(numbers[1]));

    // Calculate hashed value h(i) = h1 + (i * h2).
    BigInteger hashValue = num1.add(num2.multiply(BigInteger.valueOf(index)));

    // Wrap if hash value overflow 64bit.
    if (hashValue.compareTo(this.MAX_64_BIT_UNSIGNED_INTEGER) == 1) {
      hashValue = new BigInteger(Long.toUnsignedString(hashValue.longValue()));
    }

    return hashValue.mod(BigInteger.valueOf(this.size)).intValue();
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
        + "bitmap="
        + Arrays.toString(bitmap)
        + ", hashCount="
        + hashCount
        + ", size="
        + size
        + '}';
  }
}
