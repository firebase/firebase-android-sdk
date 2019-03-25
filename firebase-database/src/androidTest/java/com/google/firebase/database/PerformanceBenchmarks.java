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

package com.google.firebase.database;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;

/** Created by jonny on 7/22/14. */
public class PerformanceBenchmarks {

  private static void addChildren(DatabaseReference reference, int amount, int offset)
      throws InterruptedException {
    Random random = new Random();
    for (int i = 0; i < amount; i++) {
      String randomStr = "p-" + random.nextInt(Integer.MAX_VALUE);
      Map<String, Object> map =
          new MapBuilder().put("g", randomStr).put("l", Arrays.asList(0, 0)).build();
      reference.child("k-" + (i + offset)).setValue(map, randomStr);
    }
  }

  private static void addChildrenAndWait(DatabaseReference reference, int amount)
      throws InterruptedException {
    addChildren(reference, amount, 0);
    final Semaphore semaphore = new Semaphore(0);
    reference
        .child("k-" + amount)
        .setValue(
            "last-value",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                semaphore.release();
              }
            });
    Assert.assertTrue(semaphore.tryAcquire(60, TimeUnit.SECONDS));
  }

  // @Test
  public void queryPerformance() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    System.err.println("Setting up...");

    final int initialNumberOfChildren = 20000;
    final int furtherNumberOfChildren = 2000;
    final int totalNumberOfChildren = initialNumberOfChildren + furtherNumberOfChildren;

    addChildrenAndWait(ref, initialNumberOfChildren);

    System.err.println("Settling down...");
    Thread.sleep(5000);
    System.err.println("Benchmarking...");

    final int[] counter = new int[1];
    final Semaphore semaphore = new Semaphore(0);
    final long start = System.currentTimeMillis();
    ChildEventListener listener =
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            counter[0]++;
            if (counter[0] == totalNumberOfChildren) {
              semaphore.release();
            }
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        };
    ref.startAt("p").endAt("q").addChildEventListener(listener);

    addChildren(ref, furtherNumberOfChildren, initialNumberOfChildren + 1);

    semaphore.acquire();
    Assert.assertEquals(totalNumberOfChildren, counter[0]);
    System.err.println(String.format("Test took %dms", System.currentTimeMillis() - start));
    ref.removeEventListener(listener);
  }

  private static String alphaNumeric =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private static String randomString(Random random, String characters, int length) {
    char[] text = new char[length];
    for (int i = 0; i < length; i++) {
      text[i] = characters.charAt(random.nextInt(characters.length()));
    }
    return new String(text);
  }

  private Object randomObject(int recursionDepth, int recursionFanOut) {
    Random random = new Random();
    if (recursionDepth == 0) {
      double randomVar = random.nextDouble();
      if (randomVar < 0.25) {
        // String
        return randomString(random, alphaNumeric, 16);
      } else if (randomVar < 0.5) {
        // double
        return Math.random();
      } else if (randomVar < 0.75) {
        // long
        return Long.valueOf((long) (Math.random() * Integer.MAX_VALUE));
      } else {
        // boolean
        return Math.random() > 0.5;
      }
    } else {
      Map<String, Object> object = new HashMap<String, Object>();
      for (int i = 0; i < recursionFanOut; i++) {
        Object child = randomObject(recursionDepth - 1, recursionFanOut);
        object.put(randomString(random, alphaNumeric, random.nextInt(8) + 4), child);
      }
      return object;
    }
  }

  // @Test
  public void largeValuePerformance() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final int approximateObjectSize = 2 * 1024 * 1024;
    final int topLevelChildren = 200;
    final int fanOut = 5;
    final int childSizeBytes = 24;

    final int recursionDepth =
        (int) Math.log(approximateObjectSize / (topLevelChildren * fanOut * childSizeBytes));

    // create large object
    Map<String, Object> map = new HashMap<String, Object>();
    Random random = new Random();
    for (int i = 0; i < topLevelChildren; i++) {
      map.put(
          randomString(random, alphaNumeric, random.nextInt(8) + 2),
          randomObject(recursionDepth, fanOut));
    }

    ref.setValue(map);

    System.err.println("Settling down...");
    Thread.sleep(10000);
    System.err.println("Adding listeners...");
    final long start = System.currentTimeMillis();
    final Semaphore semaphore = new Semaphore(0);

    ChildEventListener childListener =
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        };
    ref.addChildEventListener(childListener);

    ValueEventListener valueListener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        };

    ref.addValueEventListener(valueListener);

    semaphore.acquire();
    System.err.println(String.format("Benchmark took %dms", System.currentTimeMillis() - start));
    ref.removeEventListener(childListener);
    ref.removeEventListener(valueListener);
  }

  // @Test
  public void childObserverPerformance() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final int numberOfChildren = 50000;

    final int[] counter = new int[1];
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            counter[0]++;
            semaphore.release();
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        };
    ref.addChildEventListener(listener);

    String[] values = new String[numberOfChildren];
    Random random = new Random();
    for (int i = 0; i < numberOfChildren; i++) {
      values[i] = randomString(random, alphaNumeric, random.nextInt(12) + 4);
    }

    System.err.println("Settling down...");
    Thread.sleep(10000);
    System.err.println("Benchmarking...");

    final long start = System.currentTimeMillis();

    for (int i = 0; i < numberOfChildren; i++) {
      DatabaseReference child = ref.push();
      child.setValue(values[i]);
    }

    semaphore.acquire(numberOfChildren);
    Assert.assertEquals(numberOfChildren, counter[0]);
    System.err.println(String.format("Benchmark took %dms", System.currentTimeMillis() - start));
    ref.removeEventListener(listener);
  }
}
