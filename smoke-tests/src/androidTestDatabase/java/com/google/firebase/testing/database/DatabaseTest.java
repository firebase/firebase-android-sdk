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

package com.google.firebase.testing.database;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DatabaseTest {

  @Test
  public void listenForUpdate() throws Exception {
    DatabaseChannel channel = DatabaseChannel.runWithDatabaseChannel(c -> test_ListenForUpdate(c));

    DataSnapshot snapshot = channel.waitForSuccess();
    assertThat(snapshot.child("location").getValue()).isEqualTo("Google SVL");
  }

  private void test_ListenForUpdate(DatabaseChannel channel) {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    database.setPersistenceEnabled(false);

    auth.signOut();

    channel
        .trapFailure(auth.signInWithEmailAndPassword("test@mailinator.com", "password"))
        .andThen(
            authResult -> {
              DatabaseReference baadal = database.getReference("restaurants").child("Baadal");
              channel.sendNextValueEvent(baadal);
              channel
                  .trapFailure(baadal.child("location").setValue("Google SVL"))
                  .andIgnoreResult();
            });
  }
}
