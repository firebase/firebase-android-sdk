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
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeartBeatImpactTest {

  @Test
  public void testHeartBeatTime() throws ExecutionException, InterruptedException {
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
    Tasks.await(db.collection("HeartBeat").add(data));
  }
}
