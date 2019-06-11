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
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/** A network request that lists folder contents within gcs. */
public class ListNetworkRequest extends NetworkRequest {
  @Nullable private final Integer maxPageSize;
  @Nullable private final String nextPageToken;

  public ListNetworkRequest(
      @NonNull Uri gsUri,
      @NonNull FirebaseApp app,
      @Nullable Integer maxPageSize,
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

    // Firebase Storage uses file system semantics and treats slashes as separators. GCS's List API
    // does not prescribe a separator, and hence we need to provide a slash as the delimiter.
    keys.add("delimiter");
    values.add("/");

    // We don't set the `maxPageSize` for listAll() as this allows Firebase Storage to return
    // fewer items per page. This removes the need to backfill results if Firebase Storage filters
    // objects that are considered invalid (such as items with two consecutive slashes).
    if (maxPageSize != null) {
      keys.add("maxResults");
      values.add(Integer.toString(maxPageSize));
    }

    if (!TextUtils.isEmpty(nextPageToken)) {
      keys.add("pageToken");
      values.add(nextPageToken);
    }

    return getPostDataString(keys, values, true);
  }
}
