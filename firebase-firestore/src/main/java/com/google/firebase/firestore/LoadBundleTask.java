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

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.Executor;

/* package */ class LoadBundleTask extends Task<LoadBundleTaskProgress> {
  @Override
  public boolean isComplete() {
    return false;
  }

  @Override
  public boolean isSuccessful() {
    return false;
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Nullable
  @Override
  public LoadBundleTaskProgress getResult() {
    return null;
  }

  @Nullable
  @Override
  public <X extends Throwable> LoadBundleTaskProgress getResult(@NonNull Class<X> aClass) throws X {
    return null;
  }

  @Nullable
  @Override
  public Exception getException() {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull Executor executor,
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull Activity activity,
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull OnFailureListener onFailureListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
    return null;
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return super.addOnCompleteListener(onCompleteListener);
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull Executor executor,
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return super.addOnCompleteListener(executor, onCompleteListener);
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull Activity activity,
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return super.addOnCompleteListener(activity, onCompleteListener);
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull OnCanceledListener onCanceledListener) {
    return super.addOnCanceledListener(onCanceledListener);
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull Executor executor, @NonNull OnCanceledListener onCanceledListener) {
    return super.addOnCanceledListener(executor, onCanceledListener);
  }

  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull Activity activity, @NonNull OnCanceledListener onCanceledListener) {
    return super.addOnCanceledListener(activity, onCanceledListener);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Continuation<LoadBundleTaskProgress, TContinuationResult> continuation) {
    return super.continueWith(continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Executor executor,
      @NonNull Continuation<LoadBundleTaskProgress, TContinuationResult> continuation) {
    return super.continueWith(executor, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Continuation<LoadBundleTaskProgress, Task<TContinuationResult>> continuation) {
    return super.continueWithTask(continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Executor executor,
      @NonNull Continuation<LoadBundleTaskProgress, Task<TContinuationResult>> continuation) {
    return super.continueWithTask(executor, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull
          SuccessContinuation<LoadBundleTaskProgress, TContinuationResult> successContinuation) {
    return super.onSuccessTask(successContinuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull Executor executor,
      @NonNull
          SuccessContinuation<LoadBundleTaskProgress, TContinuationResult> successContinuation) {
    return super.onSuccessTask(executor, successContinuation);
  }
}
