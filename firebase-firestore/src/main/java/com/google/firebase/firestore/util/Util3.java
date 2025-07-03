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

public final class Util3 {

  private Util3() {}

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
          int comp = Util.compareByteStrings(leftBytes, rightBytes);
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
}
