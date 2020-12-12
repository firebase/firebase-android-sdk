// Copyright 2020 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.NonNull;

/* package */ final class LoadBundleTaskProgress {
  public enum TaskState {
    ERROR,
    RUNNING,
    SUCCESS
  }

  private int documentsLoaded;
  private int totalDocuments;
  private long bytesLoaded;
  private long totalBytes;
  private TaskState taskState;

  public LoadBundleTaskProgress(
      int documentsLoaded,
      int totalDocuments,
      long bytesLoaded,
      long totalBytes,
      @NonNull TaskState taskState) {
    this.documentsLoaded = documentsLoaded;
    this.totalDocuments = totalDocuments;
    this.bytesLoaded = bytesLoaded;
    this.totalBytes = totalBytes;
    this.taskState = taskState;
  }

  public int getDocumentsLoaded() {
    return documentsLoaded;
  }

  public int getTotalDocuments() {
    return totalDocuments;
  }

  public long getBytesLoaded() {
    return bytesLoaded;
  }

  public long getTotalBytes() {
    return totalBytes;
  }

  @NonNull
  public TaskState getTaskState() {
    return taskState;
  }
}
