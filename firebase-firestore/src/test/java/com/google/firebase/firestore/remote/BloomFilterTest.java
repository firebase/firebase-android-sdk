// Copyright 2023 Google LLC
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

import com.google.protobuf.ByteString;
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
    BloomFilter bloomFilter = new BloomFilter(ByteString.empty(), 0, 0);
    assertEquals(bloomFilter.getBitCount(), 0);
  }

  @Test
  public void instantiateNonEmptyBloomFilter() {
    {
      BloomFilter bloomFilter = new BloomFilter(ByteString.copyFrom(new byte[] {1}), 0, 1);
      assertEquals(bloomFilter.getBitCount(), 8);
    }
    {
      BloomFilter bloomFilter = new BloomFilter(ByteString.copyFrom(new byte[] {1}), 7, 1);
      assertEquals(bloomFilter.getBitCount(), 1);
    }
  }

  @Test
  public void constructorShouldThrowNPEOnNullBitmap() {
    assertThrows(NullPointerException.class, () -> new BloomFilter(null, 0, 0));
    assertThrows(NullPointerException.class, () -> new BloomFilter(null, 1, 1));
  }

  @Test
  public void createShouldCreateAnEmptyBloomFilter() throws Exception {
    BloomFilter bloomFilter = BloomFilter.create(ByteString.empty(), 0, 0);
    assertEquals(bloomFilter.getBitCount(), 0);
  }

  @Test
  public void createShouldCreatenNonEmptyBloomFilter() throws Exception {
    {
      BloomFilter bloomFilter = BloomFilter.create(ByteString.copyFrom(new byte[] {1}), 0, 1);
      assertEquals(bloomFilter.getBitCount(), 8);
    }
    {
      BloomFilter bloomFilter = BloomFilter.create(ByteString.copyFrom(new byte[] {1}), 7, 1);
      assertEquals(bloomFilter.getBitCount(), 1);
    }
  }

  @Test
  public void createShouldThrowBFEOnEmptyBloomFilterWithNonZeroPadding() {
    BloomFilter.BloomFilterCreateException exception =
        assertThrows(
            BloomFilter.BloomFilterCreateException.class,
            () -> BloomFilter.create(ByteString.empty(), 1, 0));
    assertThat(exception).hasMessageThat().ignoringCase().contains("padding of 0");
    assertThat(exception).hasMessageThat().ignoringCase().contains("bitmap length is 0");
    assertThat(exception).hasMessageThat().ignoringCase().contains("got 1");
  }

  @Test
  public void createShouldThrowOnNonEmptyBloomFilterWithZeroHashCount() {
    BloomFilter.BloomFilterCreateException exception =
        assertThrows(
            BloomFilter.BloomFilterCreateException.class,
            () -> BloomFilter.create(ByteString.copyFrom(new byte[] {1}), 1, 0));
    assertThat(exception).hasMessageThat().ignoringCase().contains("hash count: 0");
  }

  @Test
  public void createShouldThrowOnNegativePadding() {
    {
      BloomFilter.BloomFilterCreateException exception =
          assertThrows(
              BloomFilter.BloomFilterCreateException.class,
              () -> BloomFilter.create(ByteString.empty(), -1, 0));
      assertThat(exception).hasMessageThat().ignoringCase().contains("padding: -1");
    }
    {
      BloomFilter.BloomFilterCreateException exception =
          assertThrows(
              BloomFilter.BloomFilterCreateException.class,
              () -> BloomFilter.create(ByteString.copyFrom(new byte[] {1}), -1, 1));
      assertThat(exception).hasMessageThat().ignoringCase().contains("padding: -1");
    }
  }

  @Test
  public void createShouldThrowOnNegativeHashCount() {
    {
      BloomFilter.BloomFilterCreateException exception =
          assertThrows(
              BloomFilter.BloomFilterCreateException.class,
              () -> BloomFilter.create(ByteString.empty(), 0, -1));
      assertThat(exception).hasMessageThat().ignoringCase().contains("hash count: -1");
    }
    {
      BloomFilter.BloomFilterCreateException exception =
          assertThrows(
              BloomFilter.BloomFilterCreateException.class,
              () -> BloomFilter.create(ByteString.copyFrom(new byte[] {1}), 1, -1));
      assertThat(exception).hasMessageThat().ignoringCase().contains("hash count: -1");
    }
  }

  @Test
  public void createShouldThrowIfPaddingIsTooLarge() {
    BloomFilter.BloomFilterCreateException exception =
        assertThrows(
            BloomFilter.BloomFilterCreateException.class,
            () -> BloomFilter.create(ByteString.copyFrom(new byte[] {1}), 8, 1));
    assertThat(exception).hasMessageThat().ignoringCase().contains("padding: 8");
  }

  @Test
  public void mightContainCanProcessNonStandardCharacters() {
    // A non-empty BloomFilter object with 1 insertion : "ÀÒ∑"
    BloomFilter bloomFilter =
        new BloomFilter(ByteString.copyFrom(new byte[] {(byte) 237, 5}), 5, 8);
    assertTrue(bloomFilter.mightContain("ÀÒ∑"));
    assertFalse(bloomFilter.mightContain("Ò∑À"));
  }

  @Test
  public void mightContainOnEmptyBloomFilterShouldReturnFalse() {
    BloomFilter bloomFilter = new BloomFilter(ByteString.empty(), 0, 0);
    assertFalse(bloomFilter.mightContain(""));
    assertFalse(bloomFilter.mightContain("a"));
  }

  @Test
  public void mightContainWithEmptyStringMightReturnFalsePositiveResult() {
    {
      BloomFilter bloomFilter = new BloomFilter(ByteString.copyFrom(new byte[] {1}), 1, 1);
      assertFalse(bloomFilter.mightContain(""));
    }
    {
      BloomFilter bloomFilter =
          new BloomFilter(ByteString.copyFrom(new byte[] {(byte) 255}), 0, 16);
      assertTrue(bloomFilter.mightContain(""));
    }
  }

  @Test
  public void toStringOnEmptyBitmap() {
    String toStringResult = new BloomFilter(ByteString.empty(), 0, 0).toString();
    assertThat(toStringResult).startsWith("BloomFilter{");
    assertThat(toStringResult).endsWith("}");
    assertThat(toStringResult).contains("hashCount=0");
    assertThat(toStringResult).contains("size=0");
    assertThat(toStringResult).contains("bitmap=\"\"");
  }

  @Test
  public void toStringOnNonEmptyBitmap() {
    String toStringResult =
        new BloomFilter(ByteString.copyFrom(new byte[] {0x01, (byte) 0xAE, (byte) 0xFF}), 3, 7)
            .toString();
    assertThat(toStringResult).startsWith("BloomFilter{");
    assertThat(toStringResult).endsWith("}");
    assertThat(toStringResult).contains("hashCount=7");
    assertThat(toStringResult).contains("size=21");
    assertThat(toStringResult).contains("bitmap=\"Aa7/\"");
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
  private static void runGoldenTest(String testFile) throws Exception {
    String resultFile = testFile.replace("bloom_filter_proto", "membership_test_result");
    if (resultFile.equals(testFile)) {
      throw new IllegalArgumentException("Cannot find corresponding result file for " + testFile);
    }

    JSONObject testJson = readJsonFile(testFile);
    JSONObject resultJSON = readJsonFile(resultFile);

    JSONObject bits = testJson.getJSONObject("bits");
    String bitmap = bits.getString("bitmap");
    int padding = bits.getInt("padding");
    int hashCount = testJson.getInt("hashCount");
    BloomFilter bloomFilter =
        BloomFilter.create(
            ByteString.copyFrom(Base64.getDecoder().decode(bitmap)), padding, hashCount);

    String membershipTestResults = resultJSON.getString("membershipTestResults");

    // Run and compare mightContain result with the expectation.
    for (int i = 0; i < membershipTestResults.length(); i++) {
      boolean expectedResult = membershipTestResults.charAt(i) == '1';
      boolean mightContainResult = bloomFilter.mightContain(GOLDEN_DOCUMENT_PREFIX + i);
      assertEquals(
          "For document "
              + GOLDEN_DOCUMENT_PREFIX
              + i
              + " mightContain() returned "
              + mightContainResult
              + ", but expected "
              + expectedResult,
          mightContainResult,
          expectedResult);
    }
  }

  private static JSONObject readJsonFile(String fileName) throws Exception {
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
