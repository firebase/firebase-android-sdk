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

package com.google.firebase.testapps.database;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

public class TestActivity extends Activity {
  private final CountingIdlingResource idlingResource =
      new CountingIdlingResource("Firebase database listener");
  private FirebaseDatabase db;
  private FirebaseAuth auth;
  private TextView restaurantTextView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.test_activity);

    db = FirebaseDatabase.getInstance();
    auth = FirebaseAuth.getInstance();

    restaurantTextView = this.findViewById(R.id.restaurant);
    idlingResource.increment();

    //// Since offline persistence is enabled by default, the event listener is invoked even without
    db.setPersistenceEnabled(false);

    //// Listen for a change to the collection
    db.getReference("restaurants")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                Map<String, Map<String, String>> value =
                    (Map<String, Map<String, String>>) dataSnapshot.getValue();
                if (value != null) {
                  restaurantTextView.setText(value.get("Baadal").toString());
                  idlingResource.decrement();
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                // Failed to read value
                Toast.makeText(TestActivity.this, error.toString(), Toast.LENGTH_LONG).show();
                idlingResource.decrement();
              }
            });

    //// Signout of any existing sessions and sign in with email and password
    auth.signOut();
    auth.signInWithEmailAndPassword("test@mailinator.com", "password")
        .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
          @Override
          public void onSuccess(AuthResult authResult) {
            db.getReference("restaurants")
                    .child("Baadal")
                    .child("location")
                    .setValue("Google MTV");
          }
        });
  }

  @VisibleForTesting
  @NonNull
  @Keep
  public IdlingResource getIdlingResource() {
    return idlingResource;
  }
}
