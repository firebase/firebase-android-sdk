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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BloomFilterTest {

  @Test
  public void testEmptyBloomFilter() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertEquals(bloomFilter.getSize(), 0);
  }

  @Test
  public void testEmptyBloomFilterThrowException() {
    IllegalArgumentException paddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 1, 0));
    assertThat(paddingException)
        .hasMessageThat()
        .contains("Invalid padding when bitmap length is 0: 1");
    IllegalArgumentException hashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 0, -1));
    assertThat(hashCountException).hasMessageThat().contains("Invalid hash count: -1");
  }

  @Test
  public void testNonEmptyBloomFilter() {
    BloomFilter bloomFilter1 = new BloomFilter(new byte[1], 0, 1);
    assertEquals(bloomFilter1.getSize(), 8);
    BloomFilter bloomFilter2 = new BloomFilter(new byte[1], 7, 1);
    assertEquals(bloomFilter2.getSize(), 1);
  }

  @Test
  public void testNonEmptyBloomFilterThrowException() {
    IllegalArgumentException negativePaddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], -1, 1));
    assertThat(negativePaddingException).hasMessageThat().contains("Invalid padding: -1");
    IllegalArgumentException overflowPaddingException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 8, 1));
    assertThat(overflowPaddingException).hasMessageThat().contains("Invalid padding: 8");

    IllegalArgumentException negativeHashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 1, -1));
    assertThat(negativeHashCountException).hasMessageThat().contains("Invalid hash count: -1");
    IllegalArgumentException zeroHashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[1], 1, 0));
    assertThat(zeroHashCountException).hasMessageThat().contains("Invalid hash count: 0");
  }

  @Test
  public void testBloomFilterProcessNonStandardCharacters() {
    // A non-empty BloomFilter object with 1 insertion : "ÀÒ∑"
    BloomFilter bloomFilter = new BloomFilter(new byte[] {(byte) 237, 5}, 5, 8);
    assertTrue(bloomFilter.mightContain("ÀÒ∑"));
    assertFalse(bloomFilter.mightContain("Ò∑À"));
  }

  @Test
  public void testEmptyBloomFilterMightContainAlwaysReturnFalse() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertFalse(bloomFilter.mightContain("abc"));
  }

  @Test
  public void testBloomFilterMightContainOnEmptyStringAlwaysReturnFalse() {
    BloomFilter emptyBloomFilter = new BloomFilter(new byte[0], 0, 0);
    BloomFilter nonEmptyBloomFilter =
        new BloomFilter(new byte[] {(byte) 255, (byte) 255, (byte) 255}, 1, 16);

    assertFalse(emptyBloomFilter.mightContain(""));
    assertFalse(nonEmptyBloomFilter.mightContain(""));
  }
}
