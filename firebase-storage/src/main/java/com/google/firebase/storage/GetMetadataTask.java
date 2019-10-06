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

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.GetMetadataNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import org.json.JSONException;

/** A Task that retrieves metadata for a {@link StorageReference} object */
class GetMetadataTask implements Runnable {
  private static final String TAG = "GetMetadataTask";

  private StorageReference mStorageRef;
  private TaskCompletionSource<StorageMetadata> mPendingResult;
  private StorageMetadata mResultMetadata;
  private ExponentialBackoffSender mSender;

  GetMetadataTask(
      @NonNull StorageReference storageRef,
      @NonNull TaskCompletionSource<StorageMetadata> pendingResult) {
    Preconditions.checkNotNull(storageRef);
    Preconditions.checkNotNull(pendingResult);

    this.mStorageRef = storageRef;
    this.mPendingResult = pendingResult;
    if (storageRef.getRoot().getName().equals(storageRef.getName())) {
      throw new IllegalArgumentException(
          "getMetadata() is not supported at the root of the bucket.");
    }

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
        new GetMetadataNetworkRequest(mStorageRef.getStorageUri(), mStorageRef.getApp());

    mSender.sendWithExponentialBackoff(request);
    if (request.isResultSuccess()) {
      try {
        mResultMetadata = new StorageMetadata.Builder(request.getResultBody(), mStorageRef).build();
      } catch (final JSONException e) {
        Log.e(TAG, "Unable to parse resulting metadata. " + request.getRawResult(), e);

        mPendingResult.setException(StorageException.fromException(e));
        return;
      }
    }

    if (mPendingResult != null) {
      request.completeTask(mPendingResult, mResultMetadata);
    }
  }
}
