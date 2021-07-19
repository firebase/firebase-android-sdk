// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.Executor;

/** Implementation of UpdateTask, the return type of updateApp. */
class UpdateTaskImpl extends UpdateTask {

  @NonNull private final Task<UpdateState> task;
  @Nullable private OnProgressListener listener = null;

  public UpdateTaskImpl(@NonNull Task<UpdateState> task) {
    this.task = task;
  }

  void updateProgress(@NonNull UpdateState updateState) {
    if (this.listener != null) {
      this.listener.onProgressUpdate(updateState);
    }
  }

  @NonNull
  @Override
  public UpdateTask addOnProgressListener(@NonNull OnProgressListener listener) {
    this.listener = listener;
    return this;
  }

  @Override
  public boolean isComplete() {
    return this.task.isComplete();
  }

  @Override
  public boolean isSuccessful() {
    return this.task.isSuccessful();
  }

  @Override
  public boolean isCanceled() {
    return this.task.isCanceled();
  }

  @Nullable
  @Override
  public UpdateState getResult() {
    return this.task.getResult();
  }

  @Nullable
  @Override
  public <X extends Throwable> UpdateState getResult(@NonNull Class<X> aClass) throws X {
    return this.task.getResult(aClass);
  }

  @Nullable
  @Override
  public Exception getException() {
    return this.task.getException();
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnSuccessListener(
      @NonNull OnSuccessListener<? super UpdateState> onSuccessListener) {
    return this.task.addOnSuccessListener(onSuccessListener);
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnSuccessListener(
      @NonNull Executor executor,
      @NonNull OnSuccessListener<? super UpdateState> onSuccessListener) {
    return this.task.addOnSuccessListener(executor, onSuccessListener);
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnSuccessListener(
      @NonNull Activity activity,
      @NonNull OnSuccessListener<? super UpdateState> onSuccessListener) {
    return this.task.addOnSuccessListener(activity, onSuccessListener);
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
    return this.task.addOnFailureListener(onFailureListener);
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
    return this.task.addOnFailureListener(executor, onFailureListener);
  }

  @NonNull
  @Override
  public Task<UpdateState> addOnFailureListener(
      @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
    return this.task.addOnFailureListener(activity, onFailureListener);
  }
}
