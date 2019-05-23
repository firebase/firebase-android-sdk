// Copyright 2019 Google LLC
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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.ListNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import org.json.JSONException;

/** A Task that lists the entries under a {@link StorageReference} */
class ListTask implements Runnable {
  static final int DEFAULT_PAGE_SIZE = 1000;

  private static final String TAG = "ListTask";

  private StorageReference storageRef;
  private TaskCompletionSource<ListResult> pendingResult;
  private ListResult listResult;
  private ExponentialBackoffSender sender;
  @Nullable String pageToken;
  private int maxResults;

  ListTask(
      @NonNull StorageReference storageRef,
      int maxResults,
      @Nullable String pakeToken,
      @NonNull TaskCompletionSource<ListResult> pendingResult) {
    Preconditions.checkNotNull(storageRef);
    Preconditions.checkNotNull(storageRef);
    Preconditions.checkNotNull(pendingResult);

    this.storageRef = storageRef;
    this.maxResults = maxResults;
    this.pageToken = pakeToken;
    this.pendingResult = pendingResult;

    FirebaseStorage storage = this.storageRef.getStorage();
    sender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxDownloadRetryTimeMillis());
  }

  @Override
  public void run() {
    final NetworkRequest request =
        new ListNetworkRequest(
            storageRef.getStorageUri(), storageRef.getApp(), maxResults, pageToken);

    sender.sendWithExponentialBackoff(request);

    if (request.isResultSuccess()) {
      try {
        listResult = ListResult.fromJSON(storageRef.getStorage(), request.getResultBody());
      } catch (final JSONException e) {
        Log.e(TAG, "Unable to parse response body. " + request.getRawResult(), e);

        pendingResult.setException(StorageException.fromException(e));
        return;
      }
    }

    if (pendingResult != null) {
      request.completeTask(pendingResult, listResult);
    }
  }
}
