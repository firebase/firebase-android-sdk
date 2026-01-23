/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.ndk.internal.csym;

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CSymTest {

  private static final String UUID = "UUID-1234";
  private static final String TYPE = "testCsym";
  private static final String ARCH = "testArch";

  private static final String SYMBOL1 = "symbol1";
  private static final String SYMBOL2 = "symbol2";
  private static final String SYMBOL3 = "symbol3";

  private static final String FILE1 = "file1.c";
  private static final String FILE2 = "file2.c";

  private static final int OFFSET1 = 0;
  private static final int OFFSET2 = 1024;
  private static final int OFFSET3 = 1536;

  private static final int SIZE1 = 1024;
  private static final int SIZE2 = 512;
  private static final int SIZE3 = 2048;

  private static final int LINENUMBER1 = 3;
  private static final int LINENUMBER2 = 7;
  private static final int LINENUMBER3 = 15;

  private CSym.Builder _builder;

  @Before
  public void setUp() throws Exception {
    _builder = new CSym.Builder(UUID, TYPE, ARCH);

    // Deliberately added out of order.
    _builder.addRange(OFFSET1, SIZE1, SYMBOL1);
    _builder.addRange(OFFSET3, SIZE3, SYMBOL3, FILE1, LINENUMBER3);
    _builder.addRange(OFFSET2, SIZE2, SYMBOL2, FILE1);
  }

  @Test
  public void testCSym() {
    CSym csym = _builder.build();
    Assert.assertEquals(UUID, csym.getUUID());
    Assert.assertEquals(TYPE, csym.getType());
    Assert.assertEquals(ARCH, csym.getArchitecture());

    Assert.assertEquals(1, csym.getFiles().size());
    Assert.assertTrue(csym.getFiles().contains(FILE1));

    Assert.assertEquals(3, csym.getSymbols().size());
    Assert.assertTrue(
        csym.getSymbols().containsAll(Arrays.asList(new String[] {SYMBOL1, SYMBOL2, SYMBOL3})));

    Assert.assertEquals(3, csym.getRanges().size());
  }

  @Test
  public void testRangesWithSameOffsetAreDedupedAcceptingLastIn() {
    // We've already added a range at OFFSET1, add a new one with a different file and line number.
    _builder.addRange(OFFSET1, SIZE1, SYMBOL1, FILE1, LINENUMBER2);
    CSym csym = _builder.build();
    Assert.assertEquals(3, csym.getRanges().size());
    Assert.assertEquals(LINENUMBER2, csym.getRanges().get(0).lineNumber);
  }

  @Test
  public void testCSymRangesAreSorted() {
    CSym csym = _builder.build();
    List<CSym.Range> ranges = csym.getRanges();
    for (int i = 1; i < ranges.size(); ++i) {
      if (ranges.get(i).offset < ranges.get(i - 1).offset) {
        Assert.fail("Ranges are not sorted");
      }
    }
  }

  @Test
  public void testRangeEquals() {
    CSym.Range range1 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertNotSame(range1, range2);
    Assert.assertEquals(range1, range2);
    Assert.assertEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  public void testRangeEqualsSame() {
    CSym.Range range1 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertEquals(range1.hashCode(), range1.hashCode());
  }

  @Test
  public void testRangeNotEqualsOffset() {
    CSym.Range range1 = new CSym.Range(0, 512, SYMBOL1, FILE1, LINENUMBER1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertTrue(range1 != range2);
    Assert.assertNotEquals(range1, range2);
    Assert.assertNotEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  public void testRangeNotEqualsSize() {
    CSym.Range range1 = new CSym.Range(1024, 0, SYMBOL1, FILE1, LINENUMBER1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertTrue(range1 != range2);
    Assert.assertNotEquals(range1, range2);
    Assert.assertNotEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  public void testRangeNotEqualsSymbol() {
    CSym.Range range1 = new CSym.Range(1024, 512, SYMBOL2, FILE1, LINENUMBER1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertTrue(range1 != range2);
    Assert.assertNotEquals(range1, range2);
    Assert.assertNotEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  public void testRangeNotEqualsFile() {
    CSym.Range range1 = new CSym.Range(1024, 512, SYMBOL1, FILE2, LINENUMBER1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertTrue(range1 != range2);
    Assert.assertNotEquals(range1, range2);
    Assert.assertNotEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  public void testRangeNotEqualsLineNumber() {
    CSym.Range range1 = new CSym.Range(0, 512, SYMBOL1, FILE1, 1);
    CSym.Range range2 = new CSym.Range(1024, 512, SYMBOL1, FILE1, LINENUMBER1);

    Assert.assertTrue(range1 != range2);
    Assert.assertNotEquals(range1, range2);
    Assert.assertNotEquals(range1.hashCode(), range2.hashCode());
  }
}
