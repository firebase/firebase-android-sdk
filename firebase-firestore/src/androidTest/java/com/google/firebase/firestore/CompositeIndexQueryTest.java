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
package com.google.firebase.firestore;

import static com.google.firebase.firestore.Filter.equalTo;
import static com.google.firebase.firestore.Filter.greaterThan;
import static com.google.firebase.firestore.Filter.or;
import static com.google.firebase.firestore.testutil.TestUtil.map;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.CompositeIndexTestHelper;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompositeIndexQueryTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testOrQueriesWithCompositeIndexes() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("a", 2, "b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1, "b", 1));
    CollectionReference collection = testHelper.withTestDocs(testDocs);

    // with one inequality: a>2 || b==1.
    testHelper.checkOnlineAndOfflineResults(
        testHelper.query(collection.where(or(greaterThan("a", 2), equalTo("b", 1)))),
        "doc5",
        "doc2",
        "doc3");

    // Test with limits (implicit order by ASC): (a==1) || (b > 0) LIMIT 2
    testHelper.checkOnlineAndOfflineResults(
        testHelper.query(collection.where(or(equalTo("a", 1), greaterThan("b", 0))).limit(2)),
        "doc1",
        "doc2");

    // Test with limits (explicit order by): (a==1) || (b > 0) LIMIT_TO_LAST 2
    // Note: The public query API does not allow implicit ordering when limitToLast is used.
    testHelper.checkOnlineAndOfflineResults(
        testHelper.query(
            collection.where(or(equalTo("a", 1), greaterThan("b", 0))).limitToLast(2).orderBy("b")),
        "doc3",
        "doc4");

    // Test with limits (explicit order by ASC): (a==2) || (b == 1) ORDER BY a LIMIT 1
    testHelper.checkOnlineAndOfflineResults(
        testHelper.query(
            collection.where(or(equalTo("a", 2), equalTo("b", 1))).limit(1).orderBy("a")),
        "doc5");

    // Test with limits (explicit order by DESC): (a==2) || (b == 1) ORDER BY a LIMIT_TO_LAST 1
    testHelper.checkOnlineAndOfflineResults(
        testHelper.query(
            collection.where(or(equalTo("a", 2), equalTo("b", 1))).limitToLast(1).orderBy("a")),
        "doc2");
  }
}
