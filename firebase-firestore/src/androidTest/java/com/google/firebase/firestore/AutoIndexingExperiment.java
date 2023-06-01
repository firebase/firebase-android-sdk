package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.toDataMap;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutoIndexingExperiment {
  static List<Object> values =
      Arrays.asList(
          "Hello world",
          46239847,
          -1984092375,
          Arrays.asList(1, "foo", 3, 5, 8, 10, 11),
          Arrays.asList(1, "foo", 9, 5, 8),
          Double.NaN,
          map("nested", "random"));

  private void createTestingDocument(
      CollectionReference colRef, int documentID, boolean isMatched, int numOfFields) {
    Map<String, Object> fields = map("match", isMatched);

    // Randomly generate the rest of fields
    for (int i = 2; i <= numOfFields; i++) {
      int valueIndex = (int) (Math.random() * values.size()) % values.size();
      fields.put("field" + i, values.get(valueIndex));
    }

    DocumentReference docRef = colRef.document(String.valueOf(documentID));
    System.out.println("Document id " + documentID + String.valueOf(isMatched));
    waitFor(docRef.set(fields));
  }

  private void createTestingCollection(
      CollectionReference colRef,
      int totalSetCount,
      int portion /*0 - 10*/,
      int numOfFields /* 1 - 30*/) {
    int documentCounter = 0;
    for (int i = 1; i <= totalSetCount; i++) {
      // Generate a random order list of 0 ... 9
      ArrayList<Integer> indexes = new ArrayList<>();
      for (int index = 0; index < 10; index++) {
        indexes.add(index);
      }
      Collections.shuffle(indexes);

      for (int match = 0; match < portion; match++) {
        int currentID = documentCounter + indexes.get(match);
        createTestingDocument(colRef, currentID, true, numOfFields);
      }
      for (int unmatch = portion; unmatch < 10; unmatch++) {
        int currentID = documentCounter + indexes.get(unmatch);
        createTestingDocument(colRef, currentID, false, numOfFields);
      }
      documentCounter += 10;
    }
  }

  @Test
  public void getDocumentWhileOfflineWithSourceEqualToCache() {
    String basePath = "parent";
    CollectionReference colRef = testCollection(basePath);
    createTestingCollection(colRef, 10, 8, 11);

    // Pull the whole collection data into cache
    Task<QuerySnapshot> onlineResult = colRef.get();
    waitFor(onlineResult);
    assertEquals(100, toDataMap(onlineResult.getResult()).size());

    waitFor(colRef.getFirestore().disableNetwork());

    Task<QuerySnapshot> qrySnapTask = colRef.whereEqualTo("match", true).get(Source.CACHE);
    waitFor(qrySnapTask);
    QuerySnapshot qrySnap = qrySnapTask.getResult();
    System.out.println("result is " + toDataMap(qrySnap));
    assertEquals(80, toDataMap(qrySnap).size());
  }

  @Test
  public void testIndexedWithNonIndexedTime() {
    // Every set contains 10 documents
    final int numOfSet = 9;
    // could overflow. Currently it is safe when numOfSet set to 1000 and running on macbook M1
    long totalBeforeIndex = 0;
    long totalAfterIndex = 0;
    long totalDocumentCount = 0;
    long totalResultCount = 0;

    // Temperate heuristic
    double without = 3.7;
    double with = 4.9;

    for (int totalSetCount = 1; totalSetCount <= numOfSet; totalSetCount *= 10) {
      // portion stands for the percentage of documents matching query
      for (int portion = 0; portion <= 10; portion++) {
        for (int numOfFields = 1; numOfFields <= 31; numOfFields += 10) {
          String basePath = "documentCount" + totalSetCount;
          CollectionReference colRef = testCollection(basePath);
          createTestingCollection(colRef, totalSetCount, portion, numOfFields);

          // Pull the whole collection data into cache
          Task<QuerySnapshot> onlineResult = colRef.get();
          waitFor(onlineResult);
          assertEquals(totalSetCount * 10, toDataMap(onlineResult.getResult()).size());

          waitFor(colRef.getFirestore().disableNetwork());

          // Achieve data through full collection scan
          Task<QuerySnapshot> qrySnapTask = colRef.whereEqualTo("match", true).get(Source.CACHE);
          waitFor(qrySnapTask);
          QuerySnapshot qrySnap = qrySnapTask.getResult();
          assertEquals(totalSetCount * portion, toDataMap(qrySnap).size());
        }
      }
    }
  }
}
