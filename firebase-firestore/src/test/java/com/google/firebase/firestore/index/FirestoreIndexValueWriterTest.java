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

package com.google.firebase.firestore.index;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firestore.v1.Value;
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
}
