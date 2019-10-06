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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.testing.common.Tasks2;
import com.google.firebase.testing.common.TestId;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Storage smoke tests. */
@RunWith(AndroidJUnit4.class)
public final class StorageTest {

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Test
  public void putShouldFailWithNotAuthorized() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseStorage storage = FirebaseStorage.getInstance();

    auth.signOut();
    StorageReference blob = storage.getReference("restaurants").child(TestId.create());
    byte[] data = "Google NYC".getBytes(StandardCharsets.UTF_8);

    try {
      Task<?> putTask = blob.putBytes(Arrays.copyOf(data, data.length));
      Throwable failure = Tasks2.waitForFailure(putTask);
      StorageException ex = (StorageException) failure;
      assertThat(ex.getErrorCode()).isEqualTo(StorageException.ERROR_NOT_AUTHORIZED);
    } finally {
      Tasks2.waitBestEffort(blob.delete());
    }
  }

  @Test
  public void getShouldReturnNewlyPutData() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseStorage storage = FirebaseStorage.getInstance();

    auth.signOut();
    Task<?> signInTask = auth.signInWithEmailAndPassword("test@mailinator.com", "password");
    Tasks2.waitForSuccess(signInTask);

    StorageReference blob = storage.getReference("restaurants").child(TestId.create());

    byte[] data = "Google NYC".getBytes(StandardCharsets.UTF_8);

    try {
      Task<?> putTask = blob.putBytes(Arrays.copyOf(data, data.length));
      Tasks2.waitForSuccess(putTask);

      Task<byte[]> getTask = blob.getBytes(128);
      Tasks2.waitForSuccess(getTask);

      byte[] result = getTask.getResult();
      assertThat(result).isEqualTo(data);
    } finally {
      Tasks2.waitBestEffort(blob.delete());
    }
  }
}
