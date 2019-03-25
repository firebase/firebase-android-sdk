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

package com.google.firebase.testing.firestore;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FirestoreTest {

  @Test
  public void listenForUpdate() throws Exception {
    FirestoreChannel channel =
        FirestoreChannel.runWithFirestoreChannel(c -> test_ListenForUpdate(c));

    DocumentSnapshot snapshot = channel.waitForSuccess();
    assertThat(snapshot.getString("location")).isEqualTo("Google SVL");
  }

  private void test_ListenForUpdate(FirestoreChannel channel) {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    FirebaseFirestoreSettings.Builder settings = new FirebaseFirestoreSettings.Builder();
    firestore.setFirestoreSettings(settings.setPersistenceEnabled(false).build());

    auth.signOut();

    channel
        .trapFailure(auth.signInWithEmailAndPassword("test@mailinator.com", "password"))
        .andThen(
            authResult -> {
              DocumentReference baadal = firestore.collection("restaurants").document("Baadal");
              channel.sendNextSnapshot(baadal);

              HashMap<String, Object> map = new HashMap<>();
              map.put("location", "Google SVL");
              channel.trapFailure(baadal.set(map)).andIgnoreResult();
            });
  }
}
