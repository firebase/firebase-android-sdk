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
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DocumentSnapshotTest {
  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testCanUpdateAnExistingDocument() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    waitFor(documentReference.set(initialValue));

    DocumentSnapshot snapshot1 = waitFor(documentReference.get());
    DocumentSnapshot snapshot2 = waitFor(documentReference.get());
    DocumentSnapshot snapshot3 = waitFor(documentReference.get());
    assertEquals(snapshot1.getMetadata(), snapshot2.getMetadata());
    assertEquals(snapshot2.getMetadata(), snapshot3.getMetadata());

    assertEquals(snapshot1.getId(), snapshot2.getId());
    assertEquals(snapshot2.getId(), snapshot3.getId());

    assertEquals(snapshot1.exists(), snapshot2.exists());
    assertEquals(snapshot2.exists(), snapshot3.exists());

    assertEquals(snapshot1.getReference(), snapshot2.getReference());
    assertEquals(snapshot2.getReference(), snapshot3.getReference());

    assertEquals(snapshot1.getData(), snapshot2.getData());
    assertEquals(snapshot2.getData(), snapshot3.getData());

    assertEquals(snapshot1.getData(), snapshot2.getData());
    assertEquals(snapshot2.getData(), snapshot3.getData());

    assertEquals(snapshot1, snapshot2);
    assertEquals(snapshot2, snapshot3);
  }
}
