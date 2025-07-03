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
// package com.google.firebase.firestore.util;

package com.google.firebase.firestore.util;

import static java.lang.Character.codePointAt;
import static java.lang.Character.isHighSurrogate;
import static java.lang.Character.isSurrogate;
import static java.lang.Integer.toHexString;

import com.google.firebase.firestore.BuildConfig;
import com.google.protobuf.ByteString;

public final class Utf8Compare {

  private Utf8Compare() {
  }

  /**
   * Efficiently compares two Java Strings (which are UTF-16) by the lexicographical ordering of
   * their respective UTF-8 encoding.
   * <p>
   * A naive implementation of such a comparison would be to first perform UTF-8 encoding of both
   * strings, such as by {@link String#getBytes()}, then compare the resulting byte arrays with a
   * method like {@link Util#compareByteArrays}. In fact, this naive implementation was initially
   * used in <a href="https://github.com/firebase/firebase-android-sdk/pull/6615">#6615</a>.
   * This naive algorithm, however, is both computationally expensive and requires heap allocation
   * and, therefore, garbage collection, resulting in slow String comparisons. Customers quickly
   * noticed performance degradations and the algorithm was improved in
   * <a href="https://github.com/firebase/firebase-android-sdk/pull/6706">#6706</a>. This
   * improvement, however, was still a noticeable degradation from the old, incorrect algorithm and
   * so it was optimized further in the code you see here.
   * <p>
   * The implementation of the comparison method defined in this class exploits properties of UTF-16
   * and UTF-8 to provide a comparison that is far more efficient than that aforementioned naive
   * implementation, avoiding heap allocations altogether. In a "release" build with R8 optimizations,
   * the performance is comparable to {@link String#compareTo}, the original, buggy implementation.
   */
  public static int compareUtf8Strings(String left, String right) {
    //noinspection StringEquality
    if (left == right) {
      return 0;
    }

    final int firstDifferingCharIndex = indexOfFirstDifferingChar(left, right);
    if (firstDifferingCharIndex < 0) {
      return Integer.compare(left.length(), right.length());
    }

    final char leftChar = left.charAt(firstDifferingCharIndex);
    final char rightChar = right.charAt(firstDifferingCharIndex);
    if (BuildConfig.DEBUG) {
      if (leftChar == rightChar) {
        throw new IllegalStateException("internal error: leftChar==rightChar " +
                "but they should NOT be equal (leftChar=0x" + toHexString(leftChar) + ")");
      }
    }

    // Notes about UTF-8 Encoding of Unicode characters:
    // Code points in the range 0x0000 - 0x007F are encoded using 1 byte.
    // Code points in the range 0x00FF - 0x07FF are encoded using 2 bytes.
    // Code points in the range 0x0FFF - 0xFFFF are encoded using 3 bytes.
    // These 3 character ranges collectively encode the Basic Multilingual Plane ("BMP").
    //
    // But as specific exception for UTF-16, of which Java's `char` type is a code unit, code points
    // in the range 0xD800 to 0xDBFF are "high surrogate" characters and pair with a "low surrogate"
    // character in the range 0xDC00 to 0xDFFF to produce a Unicode code point in the Supplementary
    // Multilingual Plane ("SMP") and are encoded using 4 bytes in UTF-8.
    if (leftChar < 0x80 && rightChar < 0x80) {
      return Integer.compare(leftChar, rightChar);
    } else if (leftChar < 0x80) {
      return 1;
    } else if (rightChar < 0x80) {
      return -1;
    } if (leftChar < 0x7FF && rightChar < 0x7FF) {
      return compare2ByteUtf8Encoding(leftChar, rightChar);
    } else if (leftChar < 0x7FF) {
      return 1;
    } else if (rightChar < 0x7FF) {
      return -1;
    } if (isSurrogate(leftChar) && isSurrogate((rightChar))) {
      return compareUtf8Surrogates(left, right, firstDifferingCharIndex);
    } else if (isSurrogate(leftChar)) {
      return 1;
    } else if (isSurrogate(rightChar)) {
      return -1;
    } else {
      return compare3ByteUtf8Encoding(leftChar, rightChar);
    }
  }

  private static int compare2ByteUtf8Encoding(int leftChar, int rightChar) {
    {
      int leftByte1 = utf8Encoded2ByteEncodingByte1(leftChar);
      int rightByte1 = utf8Encoded2ByteEncodingByte1(rightChar);
      int byte1Compare = Integer.compare(leftByte1, rightByte1);
      if (byte1Compare != 0) {
        return byte1Compare;
      }
    }
    {
      int leftByte2 = utf8Encoded2ByteEncodingByte2(leftChar);
      int rightByte2 = utf8Encoded2ByteEncodingByte2(rightChar);
      return Integer.compare(leftByte2, rightByte2);
    }
  }

  private static int utf8Encoded2ByteEncodingByte1(int codepoint) {
    return 0xC0 | (codepoint >> 6);
  }

  private static int utf8Encoded2ByteEncodingByte2(int codepoint) {
    return 0x80 | (codepoint & 0x3F);
  }

  private static int compare3ByteUtf8Encoding(int leftChar, int rightChar) {
    {
      int leftByte1 = utf8Encoded3ByteEncodingByte1(leftChar);
      int rightByte1 = utf8Encoded3ByteEncodingByte1(rightChar);
      int byte1Compare = Integer.compare(leftByte1, rightByte1);
      if (byte1Compare != 0) {
        return byte1Compare;
      }
    }
    {
      int leftByte2 = utf8Encoded3ByteEncodingByte2(leftChar);
      int rightByte2 = utf8Encoded3ByteEncodingByte2(rightChar);
      int byte2Compare = Integer.compare(leftByte2, rightByte2);
      if (byte2Compare != 0) {
        return byte2Compare;
      }
    }
    {
      int leftByte3 = utf8Encoded3ByteEncodingByte3(leftChar);
      int rightByte3 = utf8Encoded3ByteEncodingByte3(rightChar);
      return Integer.compare(leftByte3, rightByte3);
    }
  }

  private static int utf8Encoded3ByteEncodingByte1(int codepoint) {
    return 0xE0 | (codepoint >> 12);
  }

  private static int utf8Encoded3ByteEncodingByte2(int codepoint) {
    return 0x80 | ((codepoint >> 6) & 0x3F);
  }

  private static int utf8Encoded3ByteEncodingByte3(int codepoint) {
    return utf8Encoded2ByteEncodingByte2(codepoint);
  }

  private static int compare4ByteUtf8Encoding(int leftChar, int rightChar) {
    {
      int leftByte1 = utf8Encoded4ByteEncodingByte1(leftChar);
      int rightByte1 = utf8Encoded4ByteEncodingByte1(rightChar);
      int byte1Compare = Integer.compare(leftByte1, rightByte1);
      if (byte1Compare != 0) {
        return byte1Compare;
      }
    }
    {
      int leftByte2 = utf8Encoded4ByteEncodingByte2(leftChar);
      int rightByte2 = utf8Encoded4ByteEncodingByte2(rightChar);
      int byte2Compare = Integer.compare(leftByte2, rightByte2);
      if (byte2Compare != 0) {
        return byte2Compare;
      }
    }
    {
      int leftByte3 = utf8Encoded4ByteEncodingByte3(leftChar);
      int rightByte3 = utf8Encoded4ByteEncodingByte3(rightChar);
      int byte3Compare = Integer.compare(leftByte3, rightByte3);
      if (byte3Compare != 0) {
        return byte3Compare;
      }
    }
    {
      int leftByte4 = utf8Encoded4ByteEncodingByte4(leftChar);
      int rightByte4 = utf8Encoded4ByteEncodingByte4(rightChar);
      return Integer.compare(leftByte4, rightByte4);
    }
  }

  private static int utf8Encoded4ByteEncodingByte1(int codepoint) {
    return 0xF0 | (codepoint >> 18);
  }

  private static int utf8Encoded4ByteEncodingByte2(int codepoint) {
    return 0x80 | ((codepoint >> 12) & 0x3F);
  }

  private static int utf8Encoded4ByteEncodingByte3(int codepoint) {
    return utf8Encoded3ByteEncodingByte2(codepoint);
  }

  private static int utf8Encoded4ByteEncodingByte4(int codepoint) {
    return utf8Encoded2ByteEncodingByte2(codepoint);
  }

  private static int compareUtf8Surrogates(String left, String right, int index) {
    final char leftChar = left.charAt(index);
    final char rightChar = right.charAt(index);

    if (BuildConfig.DEBUG) {
      if (!(isSurrogate(leftChar) && isSurrogate(rightChar))) {
        throw new IllegalArgumentException("both characters should have been surrogates: index=" + index + ", leftChar=0x" + toHexString(leftChar) + " isSurrogate(leftChar)=" + isSurrogate(leftChar) + ", rightChar=0x" + toHexString(rightChar) + " isSurrogate(rightChar)=" + isSurrogate(rightChar));
      }
    }

    final SurrogateType leftSurrogateType = SurrogateType.atIndex(left, index);
    final SurrogateType rightSurrogateType = SurrogateType.atIndex(right, index);

    if (leftSurrogateType == rightSurrogateType && leftSurrogateType != SurrogateType.INVALID) {
      int startIndex = leftSurrogateType == SurrogateType.HIGH ? index : index - 1;
      return compareValidUtf8Surrogates(left, right, startIndex);
    } else {
      return compareInvalidUtf8Surrogates(left, right, index);
    }
  }

  private static int compareValidUtf8Surrogates(String left, String right, int index) {
    if (BuildConfig.DEBUG) {
      if (index + 1 >= left.length() || index + 1 >= right.length()) {
        throw new IllegalArgumentException("invalid index: " + index + " (left.length=" + left.length() + ", right.length=" + right.length() + ")");
      }
      if (!(isHighSurrogate(left.charAt(index)) && isHighSurrogate(right.charAt(index)))) {
        throw new IllegalArgumentException("unexpected character(s) at index: " + index +
                " (leftChar=0x" + toHexString(left.charAt(index)) +
                ", isHighSurrogate(leftChar)=" + isHighSurrogate(left.charAt(index)) +
                ", rightChar=0x" + toHexString(right.charAt(index)) +
                ", isHighSurrogate(rightChar)=" + isHighSurrogate(right.charAt(index)) +
                ")"
                );
      }
    }

    int leftCodePoint = codePointAt(left, index);
    int rightCodePoint = codePointAt(right, index);
    
    if (BuildConfig.DEBUG) {
      if (leftCodePoint == left.charAt(index) || rightCodePoint == right.charAt(index)) {
        throw new IllegalStateException("internal error: decoding surrogate pair failed: " +
                "index=" + index + ", leftCodePoint=" + leftCodePoint + ", rightCodePoint=" + rightCodePoint);
      }
    }
    
    return compare4ByteUtf8Encoding(leftCodePoint, rightCodePoint);
  }
  
  private static int compareInvalidUtf8Surrogates(String left, String right, int index) {
    // This is quite inefficient; however, since we're dealing with invalid UTF-16 character
    // sequences, which "should never happen", it seems wasteful to spend time optimizing this code.
    // If this is ever optimized, we need to make sure to preserve whatever semantics
    // ByteString.copyFromUtf8() implements for invalid occurrences of surrogate code points.
    ByteString leftBytes = ByteString.copyFromUtf8(left);
    ByteString rightBytes = ByteString.copyFromUtf8(right);
    return Util.compareByteStrings(leftBytes, rightBytes);
  }

  private enum SurrogateType {
    HIGH,
    LOW,
    INVALID;

    static SurrogateType atIndex(String s, int index) {
      if (index + 1 < s.length() && isHighSurrogate(s.charAt(index)) && Character.isLowSurrogate(s.charAt(index +1))) {
        return HIGH;
      } else if (index - 1 > 0 && isHighSurrogate(s.charAt(index -1)) && Character.isLowSurrogate(s.charAt(index))) {
        return LOW;
      }
      return INVALID;
    }
  }

  private static int indexOfFirstDifferingChar(String left, String right) {
    for (int i=0; i < left.length() && i < right.length(); i++) {
      if (left.charAt(i) != right.charAt(i)) {
        return i;
      }
    }
    return -1;
  }

}
