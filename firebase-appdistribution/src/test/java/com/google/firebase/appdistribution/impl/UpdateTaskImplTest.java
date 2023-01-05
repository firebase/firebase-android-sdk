// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UpdateTaskImplTest {

  private static final UpdateProgress PROGRESS_DOWNLOADING =
      UpdateProgressImpl.builder()
          .setUpdateStatus(UpdateStatus.DOWNLOADING)
          .setApkBytesDownloaded(100)
          .setApkFileTotalBytes(123)
          .build();

  private static final UpdateProgress PROGRESS_DOWNLOADED =
      UpdateProgressImpl.builder()
          .setUpdateStatus(UpdateStatus.DOWNLOADED)
          .setApkBytesDownloaded(123)
          .setApkFileTotalBytes(123)
          .build();

  @Test
  public void addOnProgressListener_supportsMultipleListeners() {
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();
    List<UpdateProgress> progressEvents1 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents1::add);
    List<UpdateProgress> progressEvents2 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents2::add);

    updateTaskImpl.updateProgress(PROGRESS_DOWNLOADING);

    assertThat(progressEvents1).containsExactly(PROGRESS_DOWNLOADING);
    assertThat(progressEvents2).containsExactly(PROGRESS_DOWNLOADING);
  }

  @Test
  public void setException_clearsProgressListeners() {
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();
    List<UpdateProgress> progressEvents1 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents1::add);
    List<UpdateProgress> progressEvents2 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents2::add);

    // Set the result
    updateTaskImpl.setException(new RuntimeException("Error"));

    // Has no effect on the listeners after the exception is set
    updateTaskImpl.updateProgress(PROGRESS_DOWNLOADING);

    assertThat(progressEvents1).isEmpty();
    assertThat(progressEvents2).isEmpty();
  }

  @Test
  public void setResult_clearsProgressListeners() {
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();
    List<UpdateProgress> progressEvents1 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents1::add);
    List<UpdateProgress> progressEvents2 = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents2::add);

    // Set the result
    updateTaskImpl.setResult();

    // Has no effect on the listeners after the result is set
    updateTaskImpl.updateProgress(PROGRESS_DOWNLOADING);

    assertThat(progressEvents1).isEmpty();
    assertThat(progressEvents2).isEmpty();
  }

  @Test
  public void shadow_completesWithShadowedException() {
    UpdateTaskImpl taskToShadow = new UpdateTaskImpl();
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();

    // Shadow the task
    updateTaskImpl.shadow(taskToShadow);

    // Complete the shadowed task
    RuntimeException expectedException = new RuntimeException("Error");
    taskToShadow.setException(expectedException);

    assertThat(updateTaskImpl.isComplete()).isEqualTo(true);
    assertThat(updateTaskImpl.isSuccessful()).isEqualTo(false);
    assertThat(updateTaskImpl.getException()).isEqualTo(expectedException);
  }

  @Test
  public void shadow_completesWithShadowedResult() {
    UpdateTaskImpl taskToShadow = new UpdateTaskImpl();
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();

    // Shadow the task
    updateTaskImpl.shadow(taskToShadow);

    // Complete the shadowed task
    taskToShadow.setResult();

    assertThat(updateTaskImpl.isComplete()).isEqualTo(true);
    assertThat(updateTaskImpl.isSuccessful()).isEqualTo(true);
  }

  @Test
  public void shadow_updatesWithShadowedProgress() {
    UpdateTaskImpl taskToShadow = new UpdateTaskImpl();
    UpdateTaskImpl updateTaskImpl = new UpdateTaskImpl();
    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTaskImpl.addOnProgressListener(FirebaseExecutors.directExecutor(), progressEvents::add);

    // Shadow the task
    updateTaskImpl.shadow(taskToShadow);

    // Update the shadowed task
    taskToShadow.updateProgress(PROGRESS_DOWNLOADING);
    taskToShadow.updateProgress(PROGRESS_DOWNLOADED);

    assertThat(updateTaskImpl.isComplete()).isEqualTo(false);
    assertThat(progressEvents).containsExactly(PROGRESS_DOWNLOADING, PROGRESS_DOWNLOADED);
  }
}
