// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.bundle.BundleMetadata;
import com.google.firebase.firestore.bundle.NamedQuery;
import java.util.HashMap;
import java.util.Map;

/** A memory-backed implementation of the bundle cache. */
/* package */ class MemoryBundleCache implements BundleCache {
  private final Map<String, BundleMetadata> bundles = new HashMap<>();
  private final Map<String, NamedQuery> namedQueries = new HashMap<>();

  @Nullable
  @Override
  public BundleMetadata getBundleMetadata(String bundleId) {
    return bundles.get(bundleId);
  }

  @Override
  public void saveBundleMetadata(BundleMetadata metadata) {
    bundles.put(metadata.getBundleId(), metadata);
  }

  @Override
  @Nullable
  public NamedQuery getNamedQuery(String queryName) {
    return namedQueries.get(queryName);
  }

  @Override
  public void saveNamedQuery(NamedQuery query) {
    namedQueries.put(query.getName(), query);
  }
}
