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
import com.google.firebase.storage.internal.Slashes;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
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
  protected String getURL() {
    return sUploadUrl + mGsUri.getAuthority() + "/o";
  }

  @Override
  @NonNull
  protected String getAction() {
    return POST;
  }

  @Override
  @NonNull
  protected String getQueryParameters() throws UnsupportedEncodingException {
    List<String> keys = new ArrayList<>();
    List<String> values = new ArrayList<>();

    String pathWithoutBucket = getPathWithoutBucket();
    keys.add("name");
    values.add(pathWithoutBucket != null ? Slashes.unSlashize(pathWithoutBucket) : "");
    keys.add("uploadType");
    values.add("resumable");
    return getPostDataString(keys, values, false);
  }

  @Override
  @Nullable
  protected JSONObject getOutputJSON() {
    return metadata;
  }
}
