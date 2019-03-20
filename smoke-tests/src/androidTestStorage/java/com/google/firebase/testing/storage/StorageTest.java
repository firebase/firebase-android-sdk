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

package com.google.firebase.testing.storage;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.testing.common.MainThread;
import com.google.firebase.testing.common.TaskChannel;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StorageTest {

  @Test
  public void getSet() throws Exception {
    TaskChannel<byte[]> channel = new TaskChannel<>();

    MainThread.run(() -> test_GetSet(channel));

    byte[] bytes = channel.waitForSuccess();
    String text = new String(bytes, StandardCharsets.UTF_8);
    assertThat(text).isEqualTo("Google MTV");
  }

  private void test_GetSet(TaskChannel<byte[]> channel) {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseStorage storage = FirebaseStorage.getInstance();

    auth.signOut();

    Task<AuthResult> signIn = auth.signInWithEmailAndPassword("test@mailinator.com", "password");
    channel
        .trapFailure(signIn)
        .andThen(
            authResult -> {
              StorageReference baadal = storage.getReference("restaurants/Baadal");
              byte[] data = "Google MTV".getBytes(StandardCharsets.UTF_8);

              channel
                  .trapFailure(baadal.putBytes(data))
                  .andThen(
                      v -> {
                        channel.sendOutcome(baadal.getBytes(1024));
                      });
            });
  }
}
