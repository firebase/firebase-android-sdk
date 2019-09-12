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

package com.google.firebase.storage;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.DeleteNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;

/**
 * A task that sends network requests to delete a Google Cloud Storage blob.
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
/*package*/ class DeleteStorageTask implements Runnable {
  private static final String TAG = "DeleteStorageTask";

  private StorageReference mStorageRef;
  private TaskCompletionSource<Void> mPendingResult;
  private ExponentialBackoffSender mSender;

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public DeleteStorageTask(
      @NonNull StorageReference storageRef, @NonNull TaskCompletionSource<Void> pendingResult) {
    Preconditions.checkNotNull(storageRef);
    Preconditions.checkNotNull(pendingResult);
    this.mStorageRef = storageRef;
    this.mPendingResult = pendingResult;

    FirebaseStorage storage = mStorageRef.getStorage();
    mSender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxDownloadRetryTimeMillis());
  }

  @Override
  public void run() {
    final NetworkRequest request =
        new DeleteNetworkRequest(mStorageRef.getStorageUri(), mStorageRef.getApp());
    mSender.sendWithExponentialBackoff(request);
    request.completeTask(mPendingResult, null);
  }
}
