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

package com.google.firebase.firestore.performance;

import static com.google.firebase.firestore.FieldValue.arrayUnion;
import static com.google.firebase.firestore.FieldValue.increment;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.github.javafaker.Faker;
import com.github.javafaker.service.RandomService;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.local.Persistence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO: Add the skipped tests from typescript.
@RunWith(AndroidJUnit4.class)
public class PerformanceTest {
  Map<String, List<DocumentReference>> docs = new HashMap<>();
  FirebaseFirestore db = null;

  @Before
  public void setUp() {
    Persistence.OVERLAY_SUPPORT_ENABLED = true;
    // Persistence.OVERLAY_SUPPORT_ENABLED = false;
    int mutationPerDoc = 3;
    System.out.println(
        "PERF: Testing with overlay support: " + Persistence.OVERLAY_SUPPORT_ENABLED);
    System.out.println("PERF: Testing with mutation per doc: " + mutationPerDoc * 3);
    Faker faker = new Faker(new Locale("zh-CN"));
    RandomService randomService = new RandomService();
    db = testFirestore();
    for (int i = 0; i < 100; ++i) {
      String coll = randomService.hex(20);
      docs.put(coll, new ArrayList<>());
      for (int j = 0; j < 100; ++j) {
        Object doc =
            map(
                "name", faker.pokemon().name(),
                "location", faker.pokemon().location(),
                "bool", randomService.nextBoolean(),
                "double", randomService.nextDouble(),
                "color", faker.color().name());
        DocumentReference ref = waitFor(db.collection(coll).add(doc));
        docs.get(coll).add(ref);
      }
    }

    waitFor(db.disableNetwork());

    long milli = System.currentTimeMillis();
    for (Map.Entry<String, List<DocumentReference>> entry : docs.entrySet()) {
      for (DocumentReference ref : entry.getValue()) {
        Object doc =
            map(
                "name", faker.pokemon().name(),
                "location", faker.pokemon().location(),
                "bool", randomService.nextBoolean(),
                "double", randomService.nextDouble(),
                "color", faker.color().name());
        ref.set(doc);
        for (int i = 0; i < mutationPerDoc; i++) {
          ref.update(map("bool", faker.app().version()));
          ref.update(map("double", increment(randomService.nextInt(-10, 10))));
          ref.update(map("color", arrayUnion(faker.color().name())));
        }
      }
    }

    waitFor(db.getAsyncQueue().enqueue(() -> {}));

    long durationInMilli = System.currentTimeMillis() - milli;

    System.out.println("PERF: Setup mutation takes " + durationInMilli + " milliseconds");
  }

  @Test
  public void testQueries() {
    Faker faker = new Faker(new Locale("zh-CN"));
    RandomService randomService = new RandomService();
    long milli = System.currentTimeMillis();
    for (Map.Entry<String, List<DocumentReference>> entry : docs.entrySet()) {
      CollectionReference coll = db.collection(entry.getKey());
      QuerySnapshot snap =
          waitFor(coll.whereEqualTo("name", faker.pokemon().name()).get(Source.CACHE));
      snap = waitFor(coll.whereLessThan("double", randomService.nextDouble()).get(Source.CACHE));
      snap = waitFor(coll.whereArrayContains("color", faker.color().name()).get(Source.CACHE));
      // System.out.println("PERF: Collection query take " + (System.currentTimeMillis() - milli) +
      // " milliseconds");
    }
    long durationInMilli = System.currentTimeMillis() - milli;
    System.out.println("PERF: Queries take " + durationInMilli + " milliseconds");
  }
}
