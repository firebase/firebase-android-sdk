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

package com.google.firebase.storage.network;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.google.firebase.FirebaseApp;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/** A network request that lists folder contents within gcs. */
public class ListNetworkRequest extends NetworkRequest {
  private final int maxPageSize;
  @Nullable private final String nextPageToken;

  public ListNetworkRequest(
      @NonNull Uri gsUri,
      @NonNull FirebaseApp app,
      int maxPageSize,
      @Nullable String nextPageToken) {
    super(gsUri, app);
    this.maxPageSize = maxPageSize;
    this.nextPageToken = nextPageToken;
  }

  @Override
  @NonNull
  protected String getAction() {
    return GET;
  }

  @Override
  @NonNull
  protected String getURL() {
    return sNetworkRequestUrl + "/b/" + mGsUri.getAuthority() + "/o";
  }

  @Override
  @Nullable
  protected String getQueryParameters() throws UnsupportedEncodingException {
    List<String> keys = new ArrayList<>();
    List<String> values = new ArrayList<>();

    String prefix = getPathWithoutBucket();
    if (!TextUtils.isEmpty(prefix)) {
      keys.add("prefix");
      values.add(prefix + "/");
    }
    keys.add("delimiter");
    values.add("/");
    keys.add("maxResults");
    values.add(Integer.toString(maxPageSize));

    if (!TextUtils.isEmpty(nextPageToken)) {
      keys.add("pageToken");
      values.add(nextPageToken);
    }

    return getPostDataString(keys, values, true);
  }
}
