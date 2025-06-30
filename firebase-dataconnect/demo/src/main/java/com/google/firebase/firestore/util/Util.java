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

package com.google.firebase.firestore.util;

import com.google.protobuf.ByteString;

public class Util {

  public static int compareUtf8StringsOriginal(String left, String right) {
    return left.compareTo(right);
  }

  public static int compareUtf8StringsSlow(String left, String right) {
    ByteString leftBytes = ByteString.copyFromUtf8(left);
    ByteString rightBytes = ByteString.copyFromUtf8(right);
    return compareByteStrings(leftBytes, rightBytes);
  }

  public static int compareUtf8StringsDenver(String left, String right) {
    int i = 0;
    while (i < left.length() && i < right.length()) {
      if (left.charAt(i) != right.charAt(i)) {
        break;
      }
      i++;
    }

    if (i == left.length() && i == right.length()) {
      return 0;
    } else if (i == left.length()) {
      return -1;
    } else if (i == right.length()) {
      return 1;
    }

    final char leftChar = left.charAt(i);
    final int leftInt = leftChar;
    final char rightChar = right.charAt(i);
    final int rightInt = rightChar;

    if (! Character.isSurrogate(leftChar) && ! Character.isSurrogate(rightChar)) {
      if (leftInt < 0x80 && rightInt < 0x80) {
        return (leftInt < rightInt) ? -1 : 1;
      }
      throw new UnsupportedOperationException("left=" + leftInt + " right=" + rightInt + " [ddwgvgggsn]");
    } else {
      throw new UnsupportedOperationException("left=" + leftInt + " right=" + rightInt + " [vf68aqd2kb]");
    }
  }

  /** Compare strings in UTF-8 encoded byte order */
  public static int compareUtf8Strings(String left, String right) {
    int i = 0;
    while (i < left.length() && i < right.length()) {
      int leftCodePoint = left.codePointAt(i);
      int rightCodePoint = right.codePointAt(i);

      if (leftCodePoint != rightCodePoint) {
        if (leftCodePoint < 128 && rightCodePoint < 128) {
          // ASCII comparison
          return Integer.compare(leftCodePoint, rightCodePoint);
        } else {
          // substring and do UTF-8 encoded byte comparison
          ByteString leftBytes = ByteString.copyFromUtf8(getUtf8SafeBytes(left, i));
          ByteString rightBytes = ByteString.copyFromUtf8(getUtf8SafeBytes(right, i));
          int comp = compareByteStrings(leftBytes, rightBytes);
          if (comp != 0) {
            return comp;
          } else {
            // EXTREMELY RARE CASE: Code points differ, but their UTF-8 byte representations are
            // identical. This can happen with malformed input (invalid surrogate pairs), where
            // Java's encoding leads to unexpected byte sequences. Meanwhile, any invalid surrogate
            // inputs get converted to "?" by protocol buffer while round tripping, so we almost
            // never receive invalid strings from backend.
            // Fallback to code point comparison for graceful handling.
            return Integer.compare(leftCodePoint, rightCodePoint);
          }
        }
      }
      // Increment by 2 for surrogate pairs, 1 otherwise.
      i += Character.charCount(leftCodePoint);
    }

    // Compare lengths if all characters are equal
    return Integer.compare(left.length(), right.length());
  }

  private static String getUtf8SafeBytes(String str, int index) {
    int firstCodePoint = str.codePointAt(index);
    return str.substring(index, index + Character.charCount(firstCodePoint));
  }

  public static int compareByteStrings(ByteString left, ByteString right) {
    int size = Math.min(left.size(), right.size());
    for (int i = 0; i < size; i++) {
      // Make sure the bytes are unsigned
      int thisByte = left.byteAt(i) & 0xff;
      int otherByte = right.byteAt(i) & 0xff;
      if (thisByte < otherByte) {
        return -1;
      } else if (thisByte > otherByte) {
        return 1;
      }
      // Byte values are equal, continue with comparison
    }
    return Util.compareIntegers(left.size(), right.size());
  }

  public static int compareIntegers(int i1, int i2) {
    if (i1 < i2) {
      return -1;
    } else if (i1 > i2) {
      return 1;
    } else {
      return 0;
    }
  }

}
