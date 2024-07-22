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
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TypeTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private static void verifySuccessfulWriteReadCycle(
      Map<String, Object> data, DocumentReference documentReference) {
    waitFor(documentReference.set(data));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
  }

  private static DocumentReference testDoc() {
    return testCollection().document();
  }

  @Test
  public void testCanReadAndWriteNullFields() {
    verifySuccessfulWriteReadCycle(map("a", 1.0, "b", null), testDoc());
  }

  @Test
  public void testCanReadAndWriteListFields() {
    verifySuccessfulWriteReadCycle(
        map("array", Arrays.asList(1.0, "foo", map("deep", true), null)), testDoc());
  }

  @Test
  public void testCanReadAndWriteBlobFields() {
    verifySuccessfulWriteReadCycle(map("blob", blob(1, 2, 3)), testDoc());
  }

  @Test
  public void testCanReadAndWriteGeoPointFields() {
    verifySuccessfulWriteReadCycle(map("geoPoint", new GeoPoint(1.23, 4.56)), testDoc());
  }

  @Test
  public void testCanReadAndWriteTimestamps() {
    Timestamp timestamp = new Timestamp(100, 123000000);
    verifySuccessfulWriteReadCycle(map("timestamp", timestamp), testDoc());
  }

  @Test
  public void testCanReadAndWriteDates() {
    Date date = new Date(1491847082123L);
    // Tests are set up to read back Timestamps, not Dates.
    verifySuccessfulWriteReadCycle(map("date", new Timestamp(date)), testDoc());
  }

  @Test
  public void testCanUseTypedAccessors() {
    DocumentReference doc = testDoc();
    Map<String, Object> data =
        map(
            "null",
            null,
            "boolean",
            true,
            "string",
            "string",
            "double",
            0.0,
            "int",
            1L,
            "geoPoint",
            new GeoPoint(1.24, 4.56),
            "blob",
            blob(0, 1, 2),
            "date",
            new Date(),
            "timestamp",
            new Timestamp(100, 123000000),
            "reference",
            doc);

    waitFor(doc.set(data));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(data.get("null"), snapshot.get("null"));
    assertEquals(data.get("null"), snapshot.get(FieldPath.fromDotSeparatedPath("null")));
    assertEquals(data.get("boolean"), snapshot.getBoolean("boolean"));
    assertEquals(data.get("string"), snapshot.getString("string"));
    assertEquals(data.get("double"), snapshot.getDouble("double"));
    assertEquals(Long.valueOf(0), snapshot.getLong("double"));
    assertEquals(data.get("int"), snapshot.getLong("int"));
    assertEquals(Double.valueOf(1.0), snapshot.getDouble("int"));
    assertEquals(data.get("geoPoint"), snapshot.getGeoPoint("geoPoint"));
    assertEquals(data.get("blob"), snapshot.getBlob("blob"));
    assertEquals(data.get("date"), snapshot.getDate("date"));
    assertEquals(new Timestamp((Date) data.get("date")), snapshot.getTimestamp("date"));
    assertEquals(data.get("timestamp"), snapshot.getTimestamp("timestamp"));
    Timestamp timestamp = (Timestamp) data.get("timestamp");
    assertEquals(timestamp.toDate(), snapshot.getDate("timestamp"));
    assertTrue(data.get("reference") instanceof DocumentReference);
    assertEquals(((DocumentReference) data.get("reference")).getPath(), doc.getPath());
  }

  @Test
  public void testTypeAccessorsCanReturnNull() {
    DocumentReference doc = testDoc();
    Map<String, Object> data = map();

    waitFor(doc.set(data));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertNull(snapshot.get("missing"));
    assertNull(snapshot.getBoolean("missing"));
    assertNull(snapshot.getString("missing"));
    assertNull(snapshot.getDouble("missing"));
    assertNull(snapshot.getLong("missing"));
    assertNull(snapshot.getLong("missing"));
    assertNull(snapshot.getDouble("missing"));
    assertNull(snapshot.getGeoPoint("missing"));
    assertNull(snapshot.getBlob("missing"));
    assertNull(snapshot.getDate("missing"));
    assertNull(snapshot.getTimestamp("missing"));
    assertNull(snapshot.getDocumentReference("missing"));
  }

  @Test
  public void testCanReadAndWriteDocumentReferences() {
    DocumentReference docRef = testDoc();
    Map<String, Object> data = map("a", 42L, "ref", docRef);
    verifySuccessfulWriteReadCycle(data, docRef);
  }

  @Test
  public void testCanReadAndWriteDocumentReferencesInLists() {
    DocumentReference docRef = testDoc();
    List<Object> refs = Collections.singletonList(docRef);
    Map<String, Object> data = map("a", 42L, "refs", refs);
    verifySuccessfulWriteReadCycle(data, docRef);
  }
}
