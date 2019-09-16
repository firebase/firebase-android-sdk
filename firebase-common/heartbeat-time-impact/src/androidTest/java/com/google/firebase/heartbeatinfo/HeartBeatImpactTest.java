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

package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeartBeatImpactTest {

  @Test
  public void testHeartBeatTime() {
    Context context = ApplicationProvider.getApplicationContext();
    FirebaseApp.initializeApp(context);
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    long startTime = System.currentTimeMillis();
    HeartBeatInfo info = new DefaultHeartBeatInfo(context);
    info.getHeartBeatCode("foo");
    long endTime = System.currentTimeMillis();
    Map<String, Object> data = new HashMap<>();
    data.put("deviceModel", Build.MODEL);
    data.put("timestamp", Long.toString(System.currentTimeMillis()));
    data.put("measurementMs", Long.toString(endTime - startTime));
    db.collection("HeartBeat")
        .add(data)
        .addOnSuccessListener(
            new OnSuccessListener<DocumentReference>() {
              @Override
              public void onSuccess(DocumentReference documentReference) {
                Log.d("HeartBeat", "HeartBeatInfo written with ID: " + documentReference.getId());
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.w("HeartBeat", "Error adding HeartBeatInfo", e);
              }
            });
  }
}
