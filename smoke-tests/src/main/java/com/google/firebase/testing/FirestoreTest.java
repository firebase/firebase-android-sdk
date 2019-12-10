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
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.testing.common.Tasks2;
import com.google.firebase.testing.common.TestId;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Firestore smoke tests. */
@RunWith(AndroidJUnit4.class)
public final class FirestoreTest {

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Test
  public void setShouldFailWithPermissionDenied() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    auth.signOut();
    Thread.sleep(1000); // TODO(allisonbm92): Introduce a better means to reduce flakes.
    DocumentReference doc = firestore.collection("restaurants").document(TestId.create());
    try {
      HashMap<String, Object> data = new HashMap<>();
      data.put("popularity", 5000L);

      Task<?> setTask = doc.set(new HashMap<>(data));
      Throwable failure = Tasks2.waitForFailure(setTask);
      FirebaseFirestoreException ex = (FirebaseFirestoreException) failure;

      assertThat(ex.getCode()).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED);
    } finally {
      Tasks2.waitBestEffort(doc.delete());
    }
  }

  @Test
  public void setShouldTriggerListenerWithNewlySetData() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    auth.signOut();
    Task<?> signInTask = auth.signInWithEmailAndPassword("test@mailinator.com", "password");
    Tasks2.waitForSuccess(signInTask);

    DocumentReference doc = firestore.collection("restaurants").document(TestId.create());
    SnapshotListener listener = new SnapshotListener();
    ListenerRegistration registration = doc.addSnapshotListener(listener);

    try {
      HashMap<String, Object> data = new HashMap<>();
      data.put("location", "Google NYC");

      Task<?> setTask = doc.set(new HashMap<>(data));
      Task<DocumentSnapshot> snapshotTask = listener.toTask();
      Tasks2.waitForSuccess(setTask);
      Tasks2.waitForSuccess(snapshotTask);

      DocumentSnapshot result = snapshotTask.getResult();
      assertThat(result.getData()).isEqualTo(data);
    } finally {
      registration.remove();
      Tasks2.waitBestEffort(doc.delete());
    }
  }

  @Test
  public void updateShouldTriggerListenerWithUpdatedData() throws Exception {
    FirebaseAuth auth = FirebaseAuth.getInstance();
    FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    auth.signOut();
    Task<?> signInTask = auth.signInWithEmailAndPassword("test@mailinator.com", "password");
    Tasks2.waitForSuccess(signInTask);

    DocumentReference doc = firestore.collection("restaurants").document(TestId.create());
    try {
      HashMap<String, Object> originalData = new HashMap<>();
      originalData.put("location", "Google NYC");

      Task<?> setTask = doc.set(new HashMap<>(originalData));
      Tasks2.waitForSuccess(setTask);

      SnapshotListener listener = new SnapshotListener(2);
      ListenerRegistration registration = doc.addSnapshotListener(listener);

      try {
        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("priority", 5L);

        Task<?> updateTask = doc.update(new HashMap<>(updateData));
        Task<DocumentSnapshot> snapshotTask = listener.toTask();
        Tasks2.waitForSuccess(updateTask);
        Tasks2.waitForSuccess(snapshotTask);

        DocumentSnapshot result = snapshotTask.getResult();
        HashMap<String, Object> finalData = new HashMap<>();
        finalData.put("location", "Google NYC");
        finalData.put("priority", 5L);
        assertThat(result.getData()).isEqualTo(finalData);
      } finally {
        registration.remove();
      }
    } finally {
      Tasks2.waitBestEffort(doc.delete());
    }
  }

  private static class SnapshotListener implements EventListener<DocumentSnapshot> {
    private final TaskCompletionSource<DocumentSnapshot> taskFactory = new TaskCompletionSource<>();

    private int eventsUntilFinished;

    SnapshotListener() {
      this.eventsUntilFinished = 1;
    }

    SnapshotListener(int count) {
      this.eventsUntilFinished = count;
    }

    @Override
    public void onEvent(DocumentSnapshot snapshot, FirebaseFirestoreException error) {
      if (error != null) {
        taskFactory.trySetException(error);
      } else {
        if (eventsUntilFinished == 1) {
          taskFactory.trySetResult(snapshot);
        }
        eventsUntilFinished -= 1;
      }
    }

    public Task<DocumentSnapshot> toTask() {
      return taskFactory.getTask();
    }
  }
}
