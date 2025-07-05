// Copyright 2024 Google LLC
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

package com.google.firebase.firestore;

import com.google.firebase.firestore.index.DirectionalIndexByteEncoder;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirestoreIndexValueWriterTest {

  @Test
  public void writeIndexValueSupportsVector() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(FieldValue.vector(new double[] {1, 2, 3}));

    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_VECTOR); // Vector type
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeLong(3); // vector length
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_STRING); // String type
    expectedDirectionalEncoder.writeString("value"); // Vector value header
    expectedDirectionalEncoder.writeLong(FirestoreIndexValueWriter.INDEX_TYPE_ARRAY); // Array type
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(1); // position 0
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(2); // position 1
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(3); // position 2
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.NOT_TRUNCATED); // Array not truncated
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsEmptyVector() {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(FieldValue.vector(new double[] {}));

    // Encode an actual VectorValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    // Create the expected representation of the encoded vector
    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_VECTOR); // Vector type
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeLong(0); // vector length
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_STRING); // String type
    expectedDirectionalEncoder.writeString("value"); // Vector value header
    expectedDirectionalEncoder.writeLong(FirestoreIndexValueWriter.INDEX_TYPE_ARRAY); // Array type
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.NOT_TRUNCATED); // Array not truncated
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    // Assert actual and expected encodings are equal
    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsBsonObjectId()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new BsonObjectId("507f191e810c19729de860ea"));

    // Encode an actual ObjectIdValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_OBJECT_ID); // ObjectId type
    expectedDirectionalEncoder.writeBytes(
        ByteString.copyFrom("507f191e810c19729de860ea".getBytes())); // ObjectId value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsBsonBinaryData()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}));

    // Encode an actual BSONBinaryDataValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_BINARY); // BSONBinaryData type
    expectedDirectionalEncoder.writeBytes(
        ByteString.copyFrom(new byte[] {1, 1, 2, 3})); // BSONBinaryData value
    expectedDirectionalEncoder.writeLong(FirestoreIndexValueWriter.NOT_TRUNCATED);
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsBsonBinaryWithEmptyData()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(BsonBinaryData.fromBytes(1, new byte[] {}));

    // Encode an actual BSONBinaryDataValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_BINARY); // BSONBinaryData type
    expectedDirectionalEncoder.writeBytes(
        ByteString.copyFrom(new byte[] {1})); // BSONBinaryData value
    expectedDirectionalEncoder.writeLong(FirestoreIndexValueWriter.NOT_TRUNCATED);
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsBsonTimestamp()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new BsonTimestamp(1, 2));

    // Encode an actual BSONTimestampValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_TIMESTAMP); // BSONTimestamp type
    expectedDirectionalEncoder.writeLong(1L << 32 | 2 & 0xFFFFFFFFL); // BSONTimestamp value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsLargestBsonTimestamp()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new BsonTimestamp(4294967295L, 4294967295L));

    // Encode an actual BSONTimestampValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_TIMESTAMP); // BSONTimestamp type
    expectedDirectionalEncoder.writeLong(
        4294967295L << 32 | 4294967295L & 0xFFFFFFFFL); // BSONTimestamp value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsSmallestBsonTimestamp()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new BsonTimestamp(0, 0));

    // Encode an actual BSONTimestampValue
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_BSON_TIMESTAMP); // BSONTimestamp type
    expectedDirectionalEncoder.writeLong(0L << 32 | 0 & 0xFFFFFFFFL); // BSONTimestamp value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsRegex() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new RegexValue("^foo", "i"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(FirestoreIndexValueWriter.INDEX_TYPE_REGEX); // Regex type
    expectedDirectionalEncoder.writeString("^foo"); // Regex pattern
    expectedDirectionalEncoder.writeString("i"); // Regex options
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.NOT_TRUNCATED); // writeTruncationMarker
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsInt32() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Int32Value(1));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(1); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsLargestInt32()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Int32Value(2147483647));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(2147483647); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsSmallestInt32()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Int32Value(-2147483648));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(-2147483648); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsDecimal128() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Decimal128Value("1.2e3"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(Double.parseDouble("1.2e3")); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsNegativeDecimal128()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Decimal128Value("-1.2e3"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(Double.parseDouble("-1.2e3")); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsSpecialDecimal128()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Decimal128Value("NaN"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NAN); // Number type, special case NaN
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsLargestDecimal128()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Decimal128Value("Infinity"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(Double.parseDouble("Infinity")); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsSmallestDecimal128()
      throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(new Decimal128Value("-Infinity"));
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_NUMBER); // Number type
    expectedDirectionalEncoder.writeDouble(Double.parseDouble("-Infinity")); // Number value
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsMinKey() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(MinKey.instance());
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();

    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);
    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_MIN_KEY); // MinKey type
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();

    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }

  @Test
  public void writeIndexValueSupportsMaxKey() throws ExecutionException, InterruptedException {
    UserDataReader dataReader = new UserDataReader(DatabaseId.EMPTY);
    Value value = dataReader.parseQueryValue(MaxKey.instance());
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    byte[] actualBytes = encoder.getEncodedBytes();
    IndexByteEncoder expectedEncoder = new IndexByteEncoder();
    DirectionalIndexByteEncoder expectedDirectionalEncoder =
        expectedEncoder.forKind(FieldIndex.Segment.Kind.ASCENDING);

    expectedDirectionalEncoder.writeLong(
        FirestoreIndexValueWriter.INDEX_TYPE_MAX_KEY); // MaxKey type
    expectedDirectionalEncoder.writeInfinity();
    byte[] expectedBytes = expectedEncoder.getEncodedBytes();
    Assert.assertArrayEquals(actualBytes, expectedBytes);
  }
}
