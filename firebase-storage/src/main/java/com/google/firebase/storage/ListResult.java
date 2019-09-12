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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Contains the prefixes and items returned by a {@link StorageReference#list} call. */
public final class ListResult {
  private static final String ITEMS_KEY = "items";
  private static final String NAME_KEY = "name";
  private static final String PAGE_TOKEN_KEY = "nextPageToken";
  private static final String PREFIXES_KEY = "prefixes";

  private final List<StorageReference> prefixes;
  private final List<StorageReference> items;
  @Nullable private final String pageToken;

  ListResult(
      List<StorageReference> prefixes, List<StorageReference> items, @Nullable String pageToken) {
    this.prefixes = prefixes;
    this.items = items;
    this.pageToken = pageToken;
  }

  static ListResult fromJSON(FirebaseStorage storage, JSONObject resultBody) throws JSONException {
    List<StorageReference> prefixes = new ArrayList<>();
    List<StorageReference> items = new ArrayList<>();

    if (resultBody.has(PREFIXES_KEY)) {
      JSONArray prefixEntries = resultBody.getJSONArray(PREFIXES_KEY);
      for (int i = 0; i < prefixEntries.length(); ++i) {
        String pathWithoutTrailingSlash = prefixEntries.getString(i);
        if (pathWithoutTrailingSlash.endsWith("/")) {
          pathWithoutTrailingSlash =
              pathWithoutTrailingSlash.substring(0, pathWithoutTrailingSlash.length() - 1);
        }
        prefixes.add(storage.getReference(pathWithoutTrailingSlash));
      }
    }

    if (resultBody.has(ITEMS_KEY)) {
      JSONArray itemEntries = resultBody.getJSONArray(ITEMS_KEY);
      for (int i = 0; i < itemEntries.length(); ++i) {
        JSONObject metadata = itemEntries.getJSONObject(i);
        items.add(storage.getReference(metadata.getString(NAME_KEY)));
      }
    }

    String pageToken = resultBody.optString(PAGE_TOKEN_KEY, /* fallback= */ null);
    return new ListResult(prefixes, items, pageToken);
  }

  /**
   * The prefixes (folders) returned by the {@code list()} operation.
   *
   * @return A list of prefixes (folders).
   */
  @NonNull
  public List<StorageReference> getPrefixes() {
    return prefixes;
  }

  /**
   * The items (files) returned by the {@code list()} operation.
   *
   * @return A list of items (files).
   */
  @NonNull
  public List<StorageReference> getItems() {
    return items;
  }

  /**
   * Returns a token that can be used to resume a previous {@code list()} operation. {@code null}
   * indicates that there are no more results.
   *
   * @return A page token if more results are available.
   */
  @Nullable
  public String getPageToken() {
    return pageToken;
  }
}
