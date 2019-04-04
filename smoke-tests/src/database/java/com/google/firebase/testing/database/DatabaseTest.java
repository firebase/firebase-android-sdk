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

import android.app.Activity;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.testing.common.Tasks2;
import com.google.firebase.testing.common.TestId;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Database smoke tests. */
@RunWith(AndroidJUnit4.class)
public final class DatabaseTest {

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Test
  public void setValueShouldTriggerListenerWithNewlySetData() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseDatabase database = FirebaseDatabase.getInstance();

    auth.signOut();
    Task<?> signInTask = auth.signInWithEmailAndPassword("test@mailinator.com", "password");
    Tasks2.waitForSuccess(signInTask);

    DatabaseReference doc = database.getReference("restaurants").child(TestId.create());
    SnapshotListener listener = new SnapshotListener();
    doc.addListenerForSingleValueEvent(listener);

    HashMap<String, Object> data = new HashMap<>();
    data.put("location", "Google NYC");

    try {
      Task<?> setTask = doc.setValue(new HashMap<>(data));
      Task<DataSnapshot> snapshotTask = listener.toTask();
      Tasks2.waitForSuccess(setTask);
      Tasks2.waitForSuccess(snapshotTask);

      DataSnapshot result = snapshotTask.getResult();
      assertThat(result.getValue()).isEqualTo(data);
    } finally {
      Tasks2.waitBestEffort(doc.removeValue());
    }
  }

  private static class SnapshotListener implements ValueEventListener {
    private final TaskCompletionSource<DataSnapshot> taskFactory = new TaskCompletionSource<>();

    @Override
    public void onCancelled(DatabaseError error) {
      taskFactory.trySetException(error.toException());
    }

    @Override
    public void onDataChange(DataSnapshot snapshot) {
      taskFactory.trySetResult(snapshot);
    }

    public Task<DataSnapshot> toTask() {
      return taskFactory.getTask();
    }
  }
}
