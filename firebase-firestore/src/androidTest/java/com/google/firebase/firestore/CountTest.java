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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CountTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void count() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    AggregateQuerySnapshot snapshot = waitFor(collection.count().get());
    assertEquals(Long.valueOf(3), snapshot.get(AggregateField.count()));
  }

}
