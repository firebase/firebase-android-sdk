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
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.firebase.firestore.bundle.BundleMetadata;

/** Represents a progress update or a final state from loading bundles. */
public final class LoadBundleTaskProgress {
  static final LoadBundleTaskProgress INITIAL =
      new LoadBundleTaskProgress(0, 0, 0, 0, null, TaskState.SUCCESS);

  /**
   * Represents the state of bundle loading tasks.
   *
   * <p>Both {@link TaskState#SUCCESS} and {@link TaskState#ERROR} are final states: task will abort
   * or complete and there will be no more updates after they are reported.
   */
  public enum TaskState {
    ERROR,
    RUNNING,
    SUCCESS
  }

  private final int documentsLoaded;
  private final int totalDocuments;
  private final long bytesLoaded;
  private final long totalBytes;
  @NonNull private final TaskState taskState;
  @Nullable private final Exception exception;

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public LoadBundleTaskProgress(
      int documentsLoaded,
      int totalDocuments,
      long bytesLoaded,
      long totalBytes,
      @Nullable Exception exception,
      @NonNull TaskState taskState) {
    this.documentsLoaded = documentsLoaded;
    this.totalDocuments = totalDocuments;
    this.bytesLoaded = bytesLoaded;
    this.totalBytes = totalBytes;
    this.taskState = taskState;
    this.exception = exception;
  }

  /**
   * Creates an "initial" status update from a Bundle's metadata. The initial status sets all
   * loading indicators to 0.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static @NonNull LoadBundleTaskProgress forInitial(@NonNull BundleMetadata bundleMetadata) {
    return new LoadBundleTaskProgress(
        0,
        bundleMetadata.getTotalDocuments(),
        0,
        bundleMetadata.getTotalBytes(),
        /* exception= */ null,
        TaskState.RUNNING);
  }

  /**
   * Creates a "success" status update from a Bundle's metadata. The initial status sets all loading
   * indicators to their maximum values.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public static @NonNull LoadBundleTaskProgress forSuccess(@NonNull BundleMetadata bundleMetadata) {
    return new LoadBundleTaskProgress(
        bundleMetadata.getTotalDocuments(),
        bundleMetadata.getTotalDocuments(),
        bundleMetadata.getTotalBytes(),
        bundleMetadata.getTotalBytes(),
        /* exception= */ null,
        LoadBundleTaskProgress.TaskState.SUCCESS);
  }

  /** Returns how many documents have been loaded. */
  public int getDocumentsLoaded() {
    return documentsLoaded;
  }

  /**
   * Returns the total number of documents in the bundle. Returns 0 if the bundle failed to parse.
   */
  public int getTotalDocuments() {
    return totalDocuments;
  }

  /** Returns how many bytes have been loaded. */
  public long getBytesLoaded() {
    return bytesLoaded;
  }

  /** Returns the total number of bytes in the bundle. Returns 0 if the bundle failed to parse. */
  public long getTotalBytes() {
    return totalBytes;
  }

  /** Returns the current state of the {@link LoadBundleTask}. */
  @NonNull
  public TaskState getTaskState() {
    return taskState;
  }

  /**
   * If the {@link LoadBundleTask} failed, returns the exception. Otherwise, returns {@code null}.
   */
  @Nullable
  public Exception getException() {
    return exception;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LoadBundleTaskProgress that = (LoadBundleTaskProgress) o;

    if (documentsLoaded != that.documentsLoaded) return false;
    if (totalDocuments != that.totalDocuments) return false;
    if (bytesLoaded != that.bytesLoaded) return false;
    if (totalBytes != that.totalBytes) return false;
    if (taskState != that.taskState) return false;
    return exception != null ? exception.equals(that.exception) : that.exception == null;
  }

  @Override
  public int hashCode() {
    int result = documentsLoaded;
    result = 31 * result + totalDocuments;
    result = 31 * result + (int) (bytesLoaded ^ (bytesLoaded >>> 32));
    result = 31 * result + (int) (totalBytes ^ (totalBytes >>> 32));
    result = 31 * result + taskState.hashCode();
    result = 31 * result + (exception != null ? exception.hashCode() : 0);
    return result;
  }
}
