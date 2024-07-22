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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unlike the ArrayTransformsTest tests, these tests intentionally avoid having any ongoing
 * listeners so that we can test what gets stored in the offline cache based purely on the write
 * acknowledgement (without receiving an updated document via watch). As such they also rely on
 * persistence being enabled so documents remain in the cache after the write.
 */
@RunWith(AndroidJUnit4.class)
public class ArrayTransformsServerApplicationTest {
  // A document reference to read and write to.
  DocumentReference docRef;

  @Before
  public void setUp() {
    docRef = testDocument();
  }

  @Test
  public void setWithNoCachedBaseDoc() {
    waitFor(docRef.set(map("array", FieldValue.arrayUnion(1L, 2L))));
    DocumentSnapshot snapshot = waitFor(docRef.get(Source.CACHE));
    assertEquals(map("array", asList(1L, 2L)), snapshot.getData());
  }

  @Test
  public void updateWithNoCachedBaseDoc() {
    // Write an initial document in an isolated Firestore instance so it's not stored in our cache.
    waitFor(testFirestore().document(docRef.getPath()).set(map("array", asList(42L))));

    waitFor(docRef.update(map("array", FieldValue.arrayUnion(1L, 2L))));

    // Nothing should be cached since it was an update and we had no base doc.
    Exception e = waitForException(docRef.get(Source.CACHE));
    assertEquals(Code.UNAVAILABLE, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void mergeSetWithNoCachedBaseDoc() {
    // Write an initial document in an isolated Firestore instance so it's not stored in our cache.
    waitFor(testFirestore().document(docRef.getPath()).set(map("array", asList(42L))));

    waitFor(docRef.set(map("array", FieldValue.arrayUnion(1L, 2L)), SetOptions.merge()));

    // Document will be cached but we'll be missing 42.
    DocumentSnapshot snapshot = waitFor(docRef.get(Source.CACHE));
    assertEquals(map("array", asList(1L, 2L)), snapshot.getData());
  }

  @Test
  public void updateWithCachedBaseDocUsingArrayUnion() {
    waitFor(docRef.set(map("array", asList(42L))));
    waitFor(docRef.update(map("array", FieldValue.arrayUnion(1, 2))));
    DocumentSnapshot snapshot = waitFor(docRef.get(Source.CACHE));
    assertEquals(map("array", asList(42L, 1L, 2L)), snapshot.getData());
  }

  @Test
  public void updateWithCachedBaseDocUsingArrayRemove() {
    waitFor(docRef.set(map("array", asList(42L, 1L, 2L))));
    waitFor(docRef.update(map("array", FieldValue.arrayRemove(1, 2))));
    DocumentSnapshot snapshot = waitFor(docRef.get(Source.CACHE));
    assertEquals(map("array", asList(42L)), snapshot.getData());
  }
}
