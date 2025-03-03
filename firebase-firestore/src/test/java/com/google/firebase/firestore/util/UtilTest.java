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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.util.Util.firstNEntries;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.firebase.firestore.testutil.TestUtil;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UtilTest {

  @Test
  public void testToDebugString() {
    assertEquals("", Util.toDebugString(ByteString.EMPTY));
    assertEquals("00ff", Util.toDebugString(TestUtil.byteString(0, 0xFF)));
    assertEquals("1f3b", Util.toDebugString(TestUtil.byteString(0x1F, 0x3B)));
    assertEquals(
        "000102030405060708090a0b0c0d0e0f",
        Util.toDebugString(
            TestUtil.byteString(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF)));
  }

  @Test
  public void testDiffCollectionsWithMissingElement() {
    List<String> before = Arrays.asList("a", "b", "c");
    List<String> after = Arrays.asList("a", "b");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithAddedElement() {
    List<String> before = Arrays.asList("a", "b");
    List<String> after = Arrays.asList("a", "b", "c");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithoutOrdering() {
    List<String> before = Arrays.asList("b", "a");
    List<String> after = Arrays.asList("a", "b");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithEmptyLists() {
    validateDiffCollection(Collections.singletonList("a"), Collections.emptyList());
    validateDiffCollection(Collections.emptyList(), Collections.singletonList("a"));
    validateDiffCollection(Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void testFirstNEntries() {
    Map<Integer, Integer> data = new HashMap<>();
    data.put(1, 1);
    data.put(3, 3);
    data.put(2, 2);
    data = firstNEntries(data, 2, Integer::compare);
    assertThat(data).containsExactly(1, 1, 2, 2);
  }

  private void validateDiffCollection(List<String> before, List<String> after) {
    List<String> result = new ArrayList<>(before);
    Util.diffCollections(before, after, String::compareTo, result::add, result::remove);
    assertThat(result).containsExactlyElementsIn(after);
  }

  @Test
  public void compareUtf8StringsShouldReturnCorrectValue() {
    ArrayList<String> errors = new ArrayList<>();
    int seed = new Random().nextInt(Integer.MAX_VALUE);
    int passCount = 0;
    StringGenerator stringGenerator = new StringGenerator(29750468);
    StringPairGenerator stringPairGenerator = new StringPairGenerator(stringGenerator);
    for (int i = 0; i < 1_000_000 && errors.size() < 10; i++) {
      StringPairGenerator.StringPair stringPair = stringPairGenerator.next();
      final String s1 = stringPair.s1;
      final String s2 = stringPair.s2;

      int actual = Util.compareUtf8Strings(s1, s2);

      ByteString b1 = ByteString.copyFromUtf8(s1);
      ByteString b2 = ByteString.copyFromUtf8(s2);
      int expected = Util.compareByteStrings(b1, b2);

      if (actual == expected) {
        passCount++;
      } else {
        errors.add(
            "compareUtf8Strings(s1=\""
                + s1
                + "\", s2=\""
                + s2
                + "\") returned "
                + actual
                + ", but expected "
                + expected
                + " (i="
                + i
                + ", s1.length="
                + s1.length()
                + ", s2.length="
                + s2.length()
                + ")");
      }
    }

    if (!errors.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append(errors.size()).append(" test cases failed, ");
      sb.append(passCount).append(" test cases passed, ");
      sb.append("seed=").append(seed).append(";");
      for (int i = 0; i < errors.size(); i++) {
        sb.append("\nerrors[").append(i).append("]: ").append(errors.get(i));
      }
      fail(sb.toString());
    }
  }

  private static class StringPairGenerator {

    private final StringGenerator stringGenerator;

    public StringPairGenerator(StringGenerator stringGenerator) {
      this.stringGenerator = stringGenerator;
    }

    public StringPair next() {
      String prefix = stringGenerator.next();
      String s1 = prefix + stringGenerator.next();
      String s2 = prefix + stringGenerator.next();
      return new StringPair(s1, s2);
    }

    public static class StringPair {
      public final String s1, s2;

      public StringPair(String s1, String s2) {
        this.s1 = s1;
        this.s2 = s2;
      }
    }
  }

  private static class StringGenerator {

    private static final float DEFAULT_SURROGATE_PAIR_PROBABILITY = 0.33f;
    private static final int DEFAULT_MAX_LENGTH = 20;

    private static final int MIN_HIGH_SURROGATE = 0xD800;
    private static final int MAX_HIGH_SURROGATE = 0xDBFF;
    private static final int MIN_LOW_SURROGATE = 0xDC00;
    private static final int MAX_LOW_SURROGATE = 0xDFFF;

    private final Random rnd;
    private final float surrogatePairProbability;
    private final int maxLength;

    public StringGenerator(int seed) {
      this(new Random(seed), DEFAULT_SURROGATE_PAIR_PROBABILITY, DEFAULT_MAX_LENGTH);
    }

    public StringGenerator(Random rnd, float surrogatePairProbability, int maxLength) {
      this.rnd = rnd;
      this.surrogatePairProbability = validateProbability(surrogatePairProbability);
      this.maxLength = validateLength(maxLength);
    }

    private static float validateProbability(float probability) {
      if (!Float.isFinite(probability)) {
        throw new IllegalArgumentException(
            "invalid surrogate pair probability: "
                + probability
                + " (must be between 0.0 and 1.0, inclusive)");
      } else if (probability < 0.0f) {
        throw new IllegalArgumentException(
            "invalid surrogate pair probability: "
                + probability
                + " (must be greater than or equal to zero)");
      } else if (probability > 1.0f) {
        throw new IllegalArgumentException(
            "invalid surrogate pair probability: "
                + probability
                + " (must be less than or equal to 1)");
      }
      return probability;
    }

    private static int validateLength(int length) {
      if (length < 0) {
        throw new IllegalArgumentException(
            "invalid maximum string length: "
                + length
                + " (must be greater than or equal to zero)");
      }
      return length;
    }

    public String next() {
      final int length = rnd.nextInt(maxLength + 1);
      final StringBuilder sb = new StringBuilder();
      while (sb.length() < length) {
        int codePoint = nextCodePoint();
        sb.appendCodePoint(codePoint);
      }
      return sb.toString();
    }

    private boolean isNextSurrogatePair() {
      return nextBoolean(rnd, surrogatePairProbability);
    }

    private static boolean nextBoolean(Random rnd, float probability) {
      if (probability == 0.0f) {
        return false;
      } else if (probability == 1.0f) {
        return true;
      } else {
        return rnd.nextFloat() < probability;
      }
    }

    private int nextCodePoint() {
      if (isNextSurrogatePair()) {
        return nextSurrogateCodePoint();
      } else {
        return nextNonSurrogateCodePoint();
      }
    }

    private int nextSurrogateCodePoint() {
      int highSurrogate =
          rnd.nextInt(MAX_HIGH_SURROGATE - MIN_HIGH_SURROGATE + 1) + MIN_HIGH_SURROGATE;
      int lowSurrogate = rnd.nextInt(MAX_LOW_SURROGATE - MIN_LOW_SURROGATE + 1) + MIN_LOW_SURROGATE;
      return Character.toCodePoint((char) highSurrogate, (char) lowSurrogate);
    }

    private int nextNonSurrogateCodePoint() {
      int codePoint;
      do {
        codePoint = rnd.nextInt(0x10000); // BMP range
      } while (codePoint >= MIN_HIGH_SURROGATE
          && codePoint <= MAX_LOW_SURROGATE); // Exclude surrogate range
      return codePoint;
    }
  }
}
