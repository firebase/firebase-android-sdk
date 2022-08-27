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

final class AggregateDemo {

  private AggregateDemo() {}

  public void Demo0_NormalQuery(FirebaseFirestore db) {
    CollectionReference query = db.collection("games/halo/players");
    QuerySnapshot snapshot = query.get().getResult();
    assertEqual(snapshot.size(), 5_000_000);
  }

  public void Demo1_CountOfDocumentsInACollection(FirebaseFirestore db) {
    AggregateQuery countQuery = db.collection("games/halo/players").count();
    AggregateQuerySnapshot snapshot = countQuery.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.getCount(), 5_000_000);
  }

  public void Demo2_CountOfDocumentsInACollectionWithFilter(FirebaseFirestore db) {
    Query query = db.collection("games/halo/players").whereEqualTo("online", true);
    AggregateQuery countQuery = query.count();
    AggregateQuerySnapshot snapshot = countQuery.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.getCount(), 2000);
  }

  public void Demo2_CountOfDocumentsInACollectionWithLimit(FirebaseFirestore db) {
    Query query = db.collection("games/halo/players").limit(9000);
    AggregateQuery countQuery = query.count();
    AggregateQuerySnapshot snapshot = countQuery.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.getCount(), 9000);
  }

  private static void assertEqual(Object o1, Object o2) {}
}
