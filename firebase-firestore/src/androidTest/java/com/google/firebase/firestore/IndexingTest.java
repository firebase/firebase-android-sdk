// Copyright 2021 Google LLC
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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.testutil.Assert;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IndexingTest {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @BeforeClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testCanConfigureIndices() throws ExecutionException, InterruptedException {
    FirebaseFirestore db = testFirestore();
    Task<Void> indexTask =
        db.setIndexConfiguration(
            "{\n"
                + "  \"indexes\": [\n"
                + "    {\n"
                + "      \"collectionGroup\": \"restaurants\",\n"
                + "      \"queryScope\": \"COLLECTION\",\n"
                + "      \"fields\": [\n"
                + "        {\n"
                + "          \"fieldPath\": \"price\",\n"
                + "          \"order\": \"ASCENDING\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"fieldPath\": \"avgRating\",\n"
                + "          \"order\": \"DESCENDING\"\n"
                + "        }\n"
                + "      ]\n"
                + "    },\n"
                + "    {\n"
                + "      \"collectionGroup\": \"restaurants\",\n"
                + "      \"queryScope\": \"COLLECTION\",\n"
                + "      \"fields\": [\n"
                + "        {\n"
                + "          \"fieldPath\": \"price\",\n"
                + "          \"order\": \"ASCENDING\"\n"
                + "        }"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"fieldOverrides\": []\n"
                + "}");
    Tasks.await(indexTask);
  }

  @Test
  public void testBadJsonDoesNotCrashClient() {
    FirebaseFirestore db = testFirestore();

    Assert.assertThrows(IllegalArgumentException.class, () -> db.setIndexConfiguration("{,"));
  }

  @Test
  public void testBadIndexDoesNotCrashClient() {
    FirebaseFirestore db = testFirestore();
    Assert.assertThrows(
        IllegalArgumentException.class,
        () ->
            db.setIndexConfiguration(
                "{\n"
                    + "  \"indexes\": [\n"
                    + "    {\n"
                    + "      \"collectionGroup\": \"restaurants\",\n"
                    + "      \"queryScope\": \"COLLECTION\",\n"
                    + "      \"fields\": [\n"
                    + "        {\n"
                    + "          \"fieldPath\": \"price\",\n"
                    + "          \"order\": \"INVALID\"\n"
                    + "        ,\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"fieldOverrides\": []\n"
                    + "}"));
  }

  // TODO(indexing): Add tests that validate that indices are active
}
