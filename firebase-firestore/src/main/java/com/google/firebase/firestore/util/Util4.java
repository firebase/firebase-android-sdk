package com.google.firebase.firestore.util;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.BuildConfig;

import java.util.Arrays;

public final class Util4 {

  private Util4() {
  }

  public static int compareUtf8Strings(String left, String right) {
    final int firstDifferingCharIndex = indexOfFirstDifferingChar(left, right);
    if (firstDifferingCharIndex < 0) {
      return Integer.compare(left.length(), right.length());
    }

    final char leftChar = left.charAt(firstDifferingCharIndex);
    final char rightChar = right.charAt(firstDifferingCharIndex);
    if (leftChar == rightChar) {
      throw new IllegalStateException("internal error: leftChar==rightChar: " + leftChar + " (" + ((int) leftChar) + ")");
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
    } if (Character.isSurrogate(leftChar) && Character.isSurrogate((rightChar))) {
      return compareUtf8Surrogates(left, right, firstDifferingCharIndex);
    } else if (Character.isSurrogate(leftChar)) {
      return 1;
    } else if (Character.isSurrogate(rightChar)) {
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
      if (!(Character.isSurrogate(leftChar) && Character.isSurrogate(rightChar))) {
        throw new IllegalArgumentException("both characters should have been surrogates, but left.charAt(" + index + ")==" + ((int) leftChar) + " isSurrogate=" + Character.isSurrogate(leftChar) + " and right.charAt(" + index + ")==" + ((int) rightChar) + " isSurrogate=" + Character.isSurrogate(rightChar));
      }
    }

    final SurrogateType leftSurrogateType = SurrogateType.atIndex(left, index);
    final SurrogateType rightSurrogateType = SurrogateType.atIndex(left, index);

    // Valid case: A high surrogate is followed by a low surrogate.
    if (leftSurrogateType != SurrogateType.INVALID && leftSurrogateType == rightSurrogateType) {
      int codePointIndex = leftSurrogateType == SurrogateType.START ? index : index - 1;
      int leftCodePoint = Character.codePointAt(left, codePointIndex);
      int rightCodePoint = Character.codePointAt(right, codePointIndex);
      return compare4ByteUtf8Encoding(leftCodePoint, rightCodePoint);

    // Invalid cases: A high surrogate is NOT followed by a low surrogate.
    // This is technically invalid; however, to avoid throwing an exception that would likely crash
    // the Firestore SDK, just produce a consistent relative ordering.
    } else if (leftHighSurrogateIndex >= 0) {

    } else if (rightHighSurrogateIndex >= 0) {

    } else {

    }
  }

  private enum SurrogateType {
    START,
    END,
    INVALID;

    static SurrogateType atIndex(String s, int index) {
      if (index + 1 < s.length() && Character.isHighSurrogate(s.charAt(index)) && Character.isLowSurrogate(s.charAt(index +1))) {
        return START;
      } else if (index - 1 > 0 && Character.isHighSurrogate(s.charAt(index -1)) && Character.isLowSurrogate(s.charAt(index))) {
        return END;
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
