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

package com.google.firebase.testapps.storage;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.util.Log;
import android.widget.TextView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.UploadTask;
import java.nio.charset.Charset;

public class TestActivity extends Activity {
  private static final String TAG = TestActivity.class.toString();

  private FirebaseStorage storage;
  private FirebaseAuth auth;
  private CountingIdlingResource idlingResource = new CountingIdlingResource("Firebase storage download");
  private TextView stringTextView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.test_activity);

    storage = FirebaseStorage.getInstance();
    auth = FirebaseAuth.getInstance();

    stringTextView = this.findViewById(R.id.restaurant);

    idlingResource.increment();

    // Signout of any existing sessions and sign in with email and password
    auth.signOut();

    auth.signInWithEmailAndPassword("test@mailinator.com", "password")
        .addOnCompleteListener(
            new OnCompleteListener<AuthResult>() {
              @Override
              public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                  uploadString();
                } else {
                  Log.d(TAG, "Unable to sign in");
                  idlingResource.decrement();
                }
              }
            });
  }

  private void uploadString() {
    storage
        .getReference()
        .child("restaurants/Baadal")
        .putBytes("Google MTV".getBytes(Charset.forName("UTF-8")))
        .addOnCompleteListener(
            new OnCompleteListener<UploadTask.TaskSnapshot>() {
              @Override
              public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                if (task.isSuccessful()) {
                  readBytes();
                } else {
                  Log.d(TAG, "Failed to upload");
                  idlingResource.decrement();
                }
              }
            });
  }

  private void readBytes() {
    storage
        .getReference()
        .child("restaurants/Baadal")
        .getBytes(1024)
        .addOnCompleteListener(
            new OnCompleteListener<byte[]>() {
              @Override
              public void onComplete(@NonNull Task<byte[]> task) {
                if (task.isSuccessful()) {
                  String s = new String(task.getResult(), Charset.forName("UTF-8"));
                  stringTextView.setText(s);
                  idlingResource.decrement();
                } else {
                  Log.d(TAG, "Failed to download");
                }
              }
            });
  }

  @VisibleForTesting
  @Nullable
  @Keep
  public IdlingResource getIdlingResource() {
    return idlingResource;
  }
}
