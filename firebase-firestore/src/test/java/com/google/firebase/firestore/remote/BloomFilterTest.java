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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BloomFilterTest {
  private static final String GOLDEN_DOCUMENT_PREFIX =
      "projects/project-1/databases/database-1/documents/coll/doc";
  private static final String GOLDEN_TEST_LOCATION =
      "src/test/resources/bloom_filter_golden_test_data/";

  @Test
  public void instantiateEmptyBloomFilter() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertEquals(bloomFilter.getBitCount(), 0);
  }

  @Test
  public void instantiateNonEmptyBloomFilter() {
    BloomFilter bloomFilter1 = new BloomFilter(new byte[] {1}, 0, 1);
    assertEquals(bloomFilter1.getBitCount(), 8);
    BloomFilter bloomFilter2 = new BloomFilter(new byte[] {1}, 7, 1);
    assertEquals(bloomFilter2.getBitCount(), 1);
  }

  @Test
  public void constructorShouldThrowNPEOnNullBitmap() {
    NullPointerException emptyBloomFilterException =
        assertThrows(NullPointerException.class, () -> new BloomFilter(null, 0, 0));
    assertThat(emptyBloomFilterException).hasMessageThat().contains("Bitmap cannot be null.");
    NullPointerException nonEmptyBloomFilterException =
        assertThrows(NullPointerException.class, () -> new BloomFilter(null, 1, 1));
    assertThat(nonEmptyBloomFilterException).hasMessageThat().contains("Bitmap cannot be null.");
  }

  @Test
  public void constructorShouldThrowIAEOnEmptyBloomFilterWithNonZeroPadding() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 1, 0));
    assertThat(exception).hasMessageThat().contains("Invalid padding when bitmap length is 0: 1");
  }

  @Test
  public void constructorShouldThrowIAEOnNonEmptyBloomFilterWithZeroHashCount() {
    IllegalArgumentException zeroHashCountException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[] {1}, 1, 0));
    assertThat(zeroHashCountException).hasMessageThat().contains("Invalid hash count: 0");
  }

  @Test
  public void constructorShouldThrowIAEOnNegativePadding() {
    IllegalArgumentException emptyBloomFilterException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], -1, 0));
    assertThat(emptyBloomFilterException).hasMessageThat().contains("Invalid padding: -1");
    IllegalArgumentException nonEmptyBloomFilterException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[] {1}, -1, 1));
    assertThat(nonEmptyBloomFilterException).hasMessageThat().contains("Invalid padding: -1");
  }

  @Test
  public void constructorShouldThrowIAEOnNegativeHashCount() {
    IllegalArgumentException emptyBloomFilterException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[0], 0, -1));
    assertThat(emptyBloomFilterException).hasMessageThat().contains("Invalid hash count: -1");
    IllegalArgumentException nonEmptyBloomFilterException =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[] {1}, 1, -1));
    assertThat(nonEmptyBloomFilterException).hasMessageThat().contains("Invalid hash count: -1");
  }

  @Test
  public void constructorShouldThrowIAEOnOverflowPadding() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(new byte[] {1}, 8, 1));
    assertThat(exception).hasMessageThat().contains("Invalid padding: 8");
  }

  @Test
  public void mightContainCanProcessNonStandardCharacters() {
    // A non-empty BloomFilter object with 1 insertion : "ÀÒ∑"
    BloomFilter bloomFilter = new BloomFilter(new byte[] {(byte) 237, 5}, 5, 8);
    assertTrue(bloomFilter.mightContain("ÀÒ∑"));
    assertFalse(bloomFilter.mightContain("Ò∑À"));
  }

  @Test
  public void mightContainOnEmptyBloomFilterShouldReturnFalse() {
    BloomFilter bloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertFalse(bloomFilter.mightContain("a"));
  }

  @Test
  public void mightContainWithEmptyStringShouldReturnFalse() {
    BloomFilter emptyBloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertFalse(emptyBloomFilter.mightContain(""));
    BloomFilter nonEmptyBloomFilter = new BloomFilter(new byte[] {(byte) 255}, 0, 1);
    assertFalse(nonEmptyBloomFilter.mightContain(""));
  }

  @Test
  public void bloomFilterToString() {
    BloomFilter emptyBloomFilter = new BloomFilter(new byte[0], 0, 0);
    assertEquals(emptyBloomFilter.toString(), "BloomFilter{hashCount=0, size=0, bitmap=\"\"}");
    BloomFilter nonEmptyBloomFilter = new BloomFilter(new byte[] {1}, 1, 1);
    assertEquals(
        nonEmptyBloomFilter.toString(), "BloomFilter{hashCount=1, size=7, bitmap=\"AQ==\"}");
  }

  /**
   * Golden tests are generated by backend based on inserting n number of document paths into a
   * bloom filter.
   *
   * <p>Full document path is generated by concatenating documentPrefix and number n, eg,
   * projects/project-1/databases/database-1/documents/coll/doc12.
   *
   * <p>The test result is generated by checking the membership of documents from documentPrefix+0
   * to documentPrefix+2n. The membership results from 0 to n is expected to be true, and the
   * membership results from n to 2n is expected to be false with some false positive results.
   */
  private void runGoldenTest(String testFile) throws Exception {
    String resultFile = testFile.replace("bloom_filter_proto", "membership_test_result");

    JSONObject testJson = readJsonFile(testFile);
    JSONObject resultJSON = readJsonFile(resultFile);

    JSONObject bits = testJson.getJSONObject("bits");
    String bitmap = bits.getString("bitmap");
    int padding = bits.getInt("padding");
    int hashCount = testJson.getInt("hashCount");
    BloomFilter bloomFilter =
        new BloomFilter(Base64.getDecoder().decode(bitmap), padding, hashCount);

    String membershipTestResults = resultJSON.getString("membershipTestResults");

    // Run and compare mightContain result with the expectation.
    for (int i = 0; i < membershipTestResults.length(); i++) {
      boolean expectedMembershipResult = membershipTestResults.charAt(i) == '1';
      boolean mightContain = bloomFilter.mightContain(GOLDEN_DOCUMENT_PREFIX + i);
      assertEquals(
          "mightContain() result doesn't match the expectation. File: "
              + testFile
              + ". Document: "
              + GOLDEN_DOCUMENT_PREFIX
              + i,
          mightContain,
          expectedMembershipResult);
    }
  }

  private JSONObject readJsonFile(String fileName) throws Exception {
    // Read the file into JSON object.
    StringBuilder builder = new StringBuilder();
    InputStreamReader streamReader =
        new InputStreamReader(
            new FileInputStream(GOLDEN_TEST_LOCATION + fileName), StandardCharsets.UTF_8);
    BufferedReader reader = new BufferedReader(streamReader);
    Stream<String> lines = reader.lines();
    lines.forEach(builder::append);
    String json = builder.toString();
    return new JSONObject(json);
  }

  @Test
  public void goldenTest_1Document_1FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_1_1_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_1Document_01FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_1_01_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_1Document_0001FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_1_0001_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_500Document_1FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_500_1_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_500Document_01FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_500_01_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_500Document_0001FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_500_0001_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_5000Document_1FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_5000_1_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_5000Document_01FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_5000_01_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_5000Document_0001FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_5000_0001_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_50000Document_1FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_50000_1_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_50000Document_01FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_50000_01_bloom_filter_proto.json");
  }

  @Test
  public void goldenTest_50000Document_0001FalsePositiveRate() throws Exception {
    runGoldenTest("Validation_BloomFilterTest_MD5_50000_0001_bloom_filter_proto.json");
  }
}
