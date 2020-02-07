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

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.storage.internal.ExponentialBackoffSender;
import com.google.firebase.storage.network.GetMetadataNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import org.json.JSONObject;

/** A Task that retrieves the download URL for a {@link StorageReference} object */
class GetDownloadUrlTask implements Runnable {
  private static final String TAG = "GetMetadataTask";

  @NonNull private static final String DOWNLOAD_TOKENS_KEY = "downloadTokens";

  private StorageReference storageRef;
  private TaskCompletionSource<Uri> pendingResult;
  private ExponentialBackoffSender sender;

  GetDownloadUrlTask(
      @NonNull StorageReference storageRef, @NonNull TaskCompletionSource<Uri> pendingResult) {
    Preconditions.checkNotNull(storageRef);
    Preconditions.checkNotNull(pendingResult);

    this.storageRef = storageRef;
    this.pendingResult = pendingResult;
    if (storageRef.getRoot().getName().equals(storageRef.getName())) {
      throw new IllegalArgumentException(
          "getDownloadUrl() is not supported at the root of the bucket.");
    }
    FirebaseStorage storage = this.storageRef.getStorage();
    sender =
        new ExponentialBackoffSender(
            storage.getApp().getApplicationContext(),
            storage.getAuthProvider(),
            storage.getMaxOperationRetryTimeMillis());
  }

  private Uri extractDownloadUrl(JSONObject response) {
    @Nullable String downloadTokens = response.optString(DOWNLOAD_TOKENS_KEY);

    if (!TextUtils.isEmpty(downloadTokens)) {
      String downloadToken = downloadTokens.split(",", -1)[0];
      Uri.Builder uriBuilder = NetworkRequest.getDefaultURL(storageRef.getStorageUri()).buildUpon();
      uriBuilder.appendQueryParameter("alt", "media");
      uriBuilder.appendQueryParameter("token", downloadToken);
      return uriBuilder.build();
    }

    return null;
  }

  @Override
  public void run() {
    final NetworkRequest request =
        new GetMetadataNetworkRequest(storageRef.getStorageUri(), storageRef.getApp());

    sender.sendWithExponentialBackoff(request);

    Uri downloadUrl = null;
    if (request.isResultSuccess()) {
      downloadUrl = extractDownloadUrl(request.getResultBody());
    }

    if (pendingResult != null) {
      request.completeTask(pendingResult, downloadUrl);
    }
  }
}
