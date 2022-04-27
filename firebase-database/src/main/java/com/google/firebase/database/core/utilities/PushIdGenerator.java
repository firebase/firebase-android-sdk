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

package com.google.firebase.database.core.utilities;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;
import static com.google.firebase.database.core.utilities.Utilities.tryParseInt;

import com.google.firebase.database.snapshot.ChildKey;
import java.util.Random;

public class PushIdGenerator {

  private static final String PUSH_CHARS =
      "-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  private static final char MIN_PUSH_CHAR = '-';

  private static final char MAX_PUSH_CHAR = 'z';

  private static final int MAX_KEY_LEN = 786;

  private static final Random randGen = new Random();

  private static long lastPushTime = 0L;

  private static final int[] lastRandChars = new int[12];

  public static synchronized String generatePushChildName(long now) {
    boolean duplicateTime = (now == lastPushTime);
    lastPushTime = now;

    char[] timeStampChars = new char[8];
    StringBuilder result = new StringBuilder(20);
    for (int i = 7; i >= 0; i--) {
      timeStampChars[i] = PUSH_CHARS.charAt((int) (now % 64));
      now = now / 64;
    }
    hardAssert(now == 0);

    result.append(timeStampChars);

    if (!duplicateTime) {
      for (int i = 0; i < 12; i++) {
        lastRandChars[i] = randGen.nextInt(64);
      }
    } else {
      incrementArray();
    }
    for (int i = 0; i < 12; i++) {
      result.append(PUSH_CHARS.charAt(lastRandChars[i]));
    }
    hardAssert(result.length() == 20);
    return result.toString();
  }

  // `key` is assumed to be non-empty.
  public static final String predecessor(String key) {
    Validation.validateNullableKey(key);
    Integer num = tryParseInt(key);
    if (num != null) {
      if (num == Integer.MIN_VALUE) {
        return ChildKey.MIN_KEY_NAME;
      }
      return String.valueOf(num - 1);
    }
    StringBuilder next = new StringBuilder(key);
    if (next.charAt(next.length() - 1) == MIN_PUSH_CHAR) {
      if (next.length() == 1) {
        return String.valueOf(Integer.MAX_VALUE);
      }
      // If the last character is the smallest possible character, then the next
      // smallest string is the prefix of `key` without it.
      return next.substring(0, next.length() - 1);
    }
    // Replace the last character with it's immediate predecessor, and fill the
    // suffix of the key with MAX_PUSH_CHAR. This is the lexicographically largest
    // possible key smaller than `key`.
    next.setCharAt(
        next.length() - 1,
        PUSH_CHARS.charAt(PUSH_CHARS.indexOf(next.charAt(next.length() - 1)) - 1));
    return next.append(
            new String(new char[MAX_KEY_LEN - next.length()]).replace("\0", "" + MAX_PUSH_CHAR))
        .toString();
  }

  public static final String successor(String key) {
    Validation.validateNullableKey(key);
    Integer num = tryParseInt(key);
    if (num != null) {
      if (num == Integer.MAX_VALUE) {
        // See https://firebase.google.com/docs/database/web/lists-of-data#data-order
        return String.valueOf(MIN_PUSH_CHAR);
      }
      return String.valueOf(num + 1);
    }
    StringBuilder next = new StringBuilder(key);

    if (next.length() < MAX_KEY_LEN) {
      // If this key doesn't have all possible character slots filled,
      // the lexicographical successor is the same string with the smallest
      // possible character appended to the end.
      next.append(MIN_PUSH_CHAR);
      return next.toString();
    }

    int i = next.length() - 1;
    while (i >= 0 && next.charAt(i) == MAX_PUSH_CHAR) {
      i--;
    }

    // `successor` was called on the lexicographically largest possible key, so return the
    // maxName, which sorts larger than all keys.
    if (i == -1) {
      return ChildKey.MAX_KEY_NAME;
    }

    // `i` now points to the last character in `key` that is < MAX_PUSH_CHAR,
    // where all characters in `key.substring(i + 1, key.length)` are MAX_PUSH_CHAR.
    // The lexicographical successor is attained by increment this character, and
    // returning the prefix of `key` up to and including it.
    char source = next.charAt(i);
    char sourcePlusOne = PUSH_CHARS.charAt(PUSH_CHARS.indexOf(source) + 1);
    next.replace(i, i + 1, String.valueOf(sourcePlusOne));

    return next.substring(0, i + 1);
  }

  private static void incrementArray() {
    for (int i = 11; i >= 0; i--) {
      if (lastRandChars[i] != 63) {
        lastRandChars[i] = lastRandChars[i] + 1;
        return;
      }
      lastRandChars[i] = 0;
    }
  }
}
