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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class POJOTest {
  public static final class POJO {

    double number;
    String str;
    Date date;
    Timestamp timestamp;
    Blob blob;
    GeoPoint geoPoint;
    DocumentReference documentReference;

    public POJO() {}

    public POJO(double number, String str, DocumentReference documentReference) {
      this.number = number;
      this.str = str;
      this.documentReference = documentReference;

      // Just set default values so we can make sure they round-trip.
      this.date = new Date(123);
      this.timestamp = new Timestamp(123, 123456000);
      this.blob = Blob.fromBytes(new byte[] {3, 1, 4, 1, 5});
      this.geoPoint = new GeoPoint(3.1415, 9.2653);
    }

    public double getNumber() {
      return number;
    }

    public void setNumber(double number) {
      this.number = number;
    }

    public String getStr() {
      return str;
    }

    public void setStr(String str) {
      this.str = str;
    }

    public Date getDate() {
      return date;
    }

    public void setDate(Date date) {
      this.date = date;
    }

    public Timestamp getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
      this.timestamp = timestamp;
    }

    public Blob getBlob() {
      return blob;
    }

    public void setBlob(Blob blob) {
      this.blob = blob;
    }

    public GeoPoint getGeoPoint() {
      return geoPoint;
    }

    public void setGeoPoint(GeoPoint geoPoint) {
      this.geoPoint = geoPoint;
    }

    public DocumentReference getDocumentReference() {
      return documentReference;
    }

    public void setDocumentReference(DocumentReference documentReference) {
      this.documentReference = documentReference;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || !(o instanceof POJO)) {
        return false;
      }

      POJO pojo = (POJO) o;

      if (Double.compare(pojo.number, number) != 0) {
        return false;
      }
      if (!str.equals(pojo.str)) {
        return false;
      }
      if (!date.equals(pojo.date)) {
        return false;
      }
      if (!timestamp.equals(pojo.timestamp)) {
        return false;
      }
      if (!blob.equals(pojo.blob)) {
        return false;
      }
      if (!geoPoint.equals(pojo.geoPoint)) {
        return false;
      }

      // TODO: Implement proper equality on DocumentReference.
      return documentReference.getPath().equals(pojo.documentReference.getPath());
    }

    @Override
    public int hashCode() {
      int result;
      long temp;
      temp = Double.doubleToLongBits(number);
      result = (int) (temp ^ (temp >>> 32));
      result = 31 * result + str.hashCode();
      result = 31 * result + date.hashCode();
      result = 31 * result + timestamp.hashCode();
      result = 31 * result + blob.hashCode();
      result = 31 * result + geoPoint.hashCode();
      result = 31 * result + documentReference.getPath().hashCode();
      return result;
    }
  }

  public static class InvalidPOJO {
    @Nullable BigInteger bigIntegerValue = null;
    @Nullable Byte byteValue = null;
    @Nullable Short shortValue = null;

    @Nullable
    public BigInteger getBigIntegerValue() {
      return bigIntegerValue;
    }

    public void setBigIntegerValue(@Nullable BigInteger bigIntegerValue) {
      this.bigIntegerValue = bigIntegerValue;
    }

    @Nullable
    public Byte getByteValue() {
      return byteValue;
    }

    public void setByteValue(@Nullable Byte byteValue) {
      this.byteValue = byteValue;
    }

    @Nullable
    public Short getShortValue() {
      return shortValue;
    }

    public void setShortValue(@Nullable Short shortValue) {
      this.shortValue = shortValue;
    }
  }

  public static final class POJOWithDocumentIdAnnotation {
    String str;
    @DocumentId public DocumentReference autoPopulatedReference;
    @DocumentId String docReferenceId;

    static class NestedPOJO {
      @DocumentId public DocumentReference autoPopulatedReference;
    }

    public NestedPOJO nested = new NestedPOJO();

    public String getDocReferenceId() {
      return docReferenceId;
    }

    public void setDocReferenceId(String id) {
      this.docReferenceId = id;
    }

    public String getStr() {
      return str;
    }

    public void setStr(String str) {
      this.str = str;
    }
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testWriteAndRead() {
    CollectionReference collection = testCollection();
    POJO data = new POJO(1.0, "a", collection.document());
    DocumentReference reference = waitFor(collection.add(data));
    DocumentSnapshot doc = waitFor(reference.get());
    POJO otherData = doc.toObject(POJO.class);
    assertEquals(data, otherData);
  }

  @Test
  public void testDocumentIdAnnotation() {
    CollectionReference collection = testCollection();
    POJOWithDocumentIdAnnotation data = new POJOWithDocumentIdAnnotation();
    data.setStr("name");
    DocumentReference reference = waitFor(collection.add(data));
    DocumentSnapshot doc = waitFor(reference.get());
    POJOWithDocumentIdAnnotation readFromStore = doc.toObject(POJOWithDocumentIdAnnotation.class);
    assertEquals("name", readFromStore.getStr());
    assertEquals(reference, readFromStore.autoPopulatedReference);
    assertEquals(reference, readFromStore.nested.autoPopulatedReference);
    assertEquals(reference.getId(), readFromStore.getDocReferenceId());
  }

  @Test
  public void testSetMerge() {
    CollectionReference collection = testCollection();
    POJO data = new POJO(1.0, "a", collection.document());
    DocumentReference reference = waitFor(collection.add(data));
    DocumentSnapshot doc = waitFor(reference.get());
    POJO otherData = doc.toObject(POJO.class);
    assertEquals(data, otherData);

    otherData = new POJO(2.0, "b", data.getDocumentReference());
    waitFor(reference.set(otherData, SetOptions.mergeFields("number")));
    POJO expected = new POJO(2.0, "a", data.getDocumentReference());
    doc = waitFor(reference.get());
    assertEquals(expected, doc.toObject(POJO.class));
  }

  // General smoke test that makes sure APIs accept POJOs.
  @Test
  public void testAPIsAcceptPOJOsForFields() {
    DocumentReference ref = testDocument();
    ArrayList<Task<?>> tasks = new ArrayList<>();

    // as Map<> entries in a set() call.
    POJO data = new POJO(1.0, "a", ref);
    tasks.add(ref.set(map("a", data, "b", map("c", data))));

    // as Map<> entries in an update() call.
    tasks.add(ref.update(map("a", data)));

    // as field values in an update() call.
    tasks.add(ref.update("c", data));

    // as values in arrayUnion() / arrayRemove().
    tasks.add(ref.update("c", FieldValue.arrayUnion(data)));
    tasks.add(ref.update("c", FieldValue.arrayRemove(data)));

    // as Query parameters.
    data.setBlob(null); // blobs are broken, see b/117680212
    tasks.add(testCollection().whereEqualTo("field", data).get());

    waitFor(Tasks.whenAll(tasks));
  }

  @Test
  public void testDocumentSnapshotGetWithPOJOs() {
    DocumentReference ref = testDocument();

    // Go offline so that we can verify server timestamp behavior overload.
    ref.getFirestore().disableNetwork();

    POJO pojo = new POJO(1.0, "a", ref);
    ref.set(map("field", pojo));

    DocumentSnapshot snap = waitFor(ref.get());

    assertEquals(pojo, snap.get("field", POJO.class));
    assertEquals(pojo, snap.get(FieldPath.of("field"), POJO.class));
    assertEquals(
        pojo, snap.get("field", POJO.class, DocumentSnapshot.ServerTimestampBehavior.DEFAULT));
    assertEquals(
        pojo,
        snap.get(
            FieldPath.of("field"), POJO.class, DocumentSnapshot.ServerTimestampBehavior.DEFAULT));
  }

  @Test
  public void setFieldMaskMustHaveCorrespondingValue() {
    CollectionReference collection = testCollection();
    DocumentReference reference = collection.document();

    expectError(
        () -> reference.set(new POJO(), SetOptions.mergeFields("str", "missing")),
        "Field 'missing' is specified in your field mask but not in your input data.");
  }

  @Test
  public void testCantWriteNonStandardNumberTypes() {
    DocumentReference ref = testDocument();

    Map<InvalidPOJO, String> expectedErrorMessages = new HashMap<>();

    InvalidPOJO pojo = new InvalidPOJO();
    pojo.bigIntegerValue = new BigInteger("0");
    expectedErrorMessages.put(
        pojo,
        "Could not serialize object. Numbers of type BigInteger are not supported, please use an int, long, float or double (found in field 'bigIntegerValue')");

    pojo = new InvalidPOJO();
    pojo.byteValue = 0;
    expectedErrorMessages.put(
        pojo,
        "Could not serialize object. Numbers of type Byte are not supported, please use an int, long, float or double (found in field 'byteValue')");

    pojo = new InvalidPOJO();
    pojo.shortValue = 0;
    expectedErrorMessages.put(
        pojo,
        "Could not serialize object. Numbers of type Short are not supported, please use an int, long, float or double (found in field 'shortValue')");

    for (Map.Entry<InvalidPOJO, String> testCase : expectedErrorMessages.entrySet()) {
      expectError(() -> ref.set(testCase.getKey()), testCase.getValue());
    }
  }

  @Test
  public void testCantReadBigInteger() {
    DocumentReference ref = testDocument();

    Map<String, Object> invalidData =
        map(
            "bigIntegerValue",
            map("bigIntegerValue", 0),
            "byteValue",
            map("byteValue", 0),
            "shortValue",
            map("shortValue", 0));
    waitFor(ref.set(invalidData));
    DocumentSnapshot snap = waitFor(ref.get());

    expectError(
        () -> snap.get("bigIntegerValue", InvalidPOJO.class),
        "Could not deserialize object. Deserializing values to BigInteger is not supported (found in field 'bigIntegerValue')");

    expectError(
        () -> snap.get("byteValue", InvalidPOJO.class),
        "Could not deserialize object. Deserializing values to Byte is not supported (found in field 'byteValue')");

    expectError(
        () -> snap.get("shortValue", InvalidPOJO.class),
        "Could not deserialize object. Deserializing values to Short is not supported (found in field 'shortValue')");
  }
}
