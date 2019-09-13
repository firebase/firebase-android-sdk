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

package com.google.firebase.storage.network;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** Starts a resumable upload session with GCS. */
public class ResumableUploadStartRequest extends ResumableNetworkRequest {
  private final JSONObject metadata;
  private final String contentType;

  public ResumableUploadStartRequest(
      @NonNull Uri gsUri,
      @NonNull FirebaseApp app,
      @Nullable JSONObject metadata,
      @NonNull String contentType) {
    super(gsUri, app);
    this.metadata = metadata;
    this.contentType = contentType;
    if (TextUtils.isEmpty(this.contentType)) {
      super.mException = new IllegalArgumentException("mContentType is null or empty");
    }
    super.setCustomHeader(PROTOCOL, "resumable");
    super.setCustomHeader(COMMAND, "start");
    super.setCustomHeader(CONTENT_TYPE, this.contentType);
  }

  @Override
  @NonNull
  protected Uri getURL() {
    Uri.Builder uriBuilder = sNetworkRequestUrl.buildUpon();
    uriBuilder.appendPath("b");
    uriBuilder.appendPath(mGsUri.getAuthority());
    uriBuilder.appendPath("o");
    return uriBuilder.build();
  }

  @Override
  @NonNull
  protected String getAction() {
    return POST;
  }

  @Override
  @NonNull
  protected Map<String, String> getQueryParameters() {
    Map<String, String> headers = new HashMap<>();
    headers.put("name", getPathWithoutBucket());
    headers.put("uploadType", "resumable");
    return headers;
  }

  @Override
  @Nullable
  protected JSONObject getOutputJSON() {
    return metadata;
  }
}
