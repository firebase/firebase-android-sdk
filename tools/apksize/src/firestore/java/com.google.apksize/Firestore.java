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

package com.google.apksize;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.Map;

public class Firestore implements SampleCode {
  private static final String TAG = "sizetest";

  @Override
  public void runSample(Context context) {
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    Map<String, Object> user = new HashMap<>();
    user.put("first", "Ada");
    user.put("last", "Lovelace");
    user.put("born", 1815);

    db.collection("users")
        .add(user)
        .addOnSuccessListener(
            new OnSuccessListener<DocumentReference>() {
              @Override
              public void onSuccess(DocumentReference documentReference) {
                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error adding document", e);
              }
            });
    // Create a new user with a first, middle, and last name
    user = new HashMap<>();
    user.put("first", "Alan");
    user.put("middle", "Mathison");
    user.put("last", "Turing");
    user.put("born", 1912);

    // Add a new document with a generated ID
    db.collection("users")
        .add(user)
        .addOnSuccessListener(
            new OnSuccessListener<DocumentReference>() {
              @Override
              public void onSuccess(DocumentReference documentReference) {
                Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error adding document", e);
              }
            });
    db.collection("users")
        .get()
        .addOnCompleteListener(
            new OnCompleteListener<QuerySnapshot>() {
              @Override
              public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                  for (QueryDocumentSnapshot document : task.getResult()) {
                    Log.d(TAG, document.getId() + " => " + document.getData());
                  }
                } else {
                  Log.w(TAG, "Error getting documents.", task.getException());
                }
              }
            });
  }
}
