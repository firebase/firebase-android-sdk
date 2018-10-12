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
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class RealtimeDatabase implements SampleCode {

  @Override
  public void runSample(Context context) {

    FirebaseApp.initializeApp(context, new FirebaseOptions.Builder().build());

    DatabaseReference db =
        FirebaseDatabase.getInstance().getReference("android/saving-data/fireblog/posts");

    db.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            Log.d("test", "onDataChange");
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            Log.d("test", "onDataChange");
          }
        });
  }
}
