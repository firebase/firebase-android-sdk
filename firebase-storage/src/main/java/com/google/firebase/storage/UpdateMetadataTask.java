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
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.NetworkRequest;
import com.google.firebase.storage.network.UpdateMetadataNetworkRequest;
import org.json.JSONException;

/** A Task that updates metadata on a {@link StorageReference} */
class UpdateMetadataTask implements Runnable {
  private static final String TAG = "UpdateMetadataTask";

  private final StorageReference mStorageRef;
  private final TaskCompletionSource<StorageMetadata> mPendingResult;
  private final StorageMetadata mNewMetadata;
  private StorageMetadata mResultMetadata = null;
  private ExponentialBackoffSender mSender;

  public UpdateMetadataTask(
      @NonNull StorageReference storageRef,
      @NonNull TaskCompletionSource<StorageMetadata> pendingResult,
      @NonNull StorageMetadata newMetadata) {
    this.mStorageRef = storageRef;
    this.mPendingResult = pendingResult;
    mNewMetadata = newMetadata;

    FirebaseStorage storage = mStorageRef.getStorage();
    mSender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxOperationRetryTimeMillis());
  }

  @Override
  public void run() {
    final NetworkRequest request =
        new UpdateMetadataNetworkRequest(
            mStorageRef.getStorageUri(), mStorageRef.getApp(), mNewMetadata.createJSONObject());

    mSender.sendWithExponentialBackoff(request);
    if (request.isResultSuccess()) {
      try {
        mResultMetadata = new StorageMetadata.Builder(request.getResultBody(), mStorageRef).build();
      } catch (final JSONException e) {
        Log.e(
            TAG,
            "Unable to parse a valid JSON object from resulting metadata:" + request.getRawResult(),
            e);

        mPendingResult.setException(StorageException.fromException(e));
        return;
      }
    }

    if (mPendingResult != null) {
      request.completeTask(mPendingResult, mResultMetadata);
    }
  }
}
