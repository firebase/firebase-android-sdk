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
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MemoryDocumentOverlay implements DocumentOverlay {
  private ImmutableSortedMap<DocumentKey, Mutation> overlays =
      ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());

  @Nullable
  @Override
  public Mutation getOverlay(DocumentKey key) {
    return overlays.get(key);
  }

  @Override
  public void saveOverlay(DocumentKey key, Mutation mutation) {
    overlays = overlays.insert(key, mutation);
  }

  @Override
  public void saveOverlays(Map<DocumentKey, Mutation> overlays) {
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      this.overlays = this.overlays.insert(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void removeOverlay(DocumentKey key) {
    overlays = overlays.remove(key);
  }

  @Override
  public Map<DocumentKey, Mutation> getAllOverlays(ResourcePath path) {
    Map<DocumentKey, Mutation> result = new HashMap<>();

    int immediateChildrenPathLength = path.length() + 1;
    DocumentKey prefix = DocumentKey.fromPath(path.append(""));
    Iterator<Map.Entry<DocumentKey, Mutation>> iterator = overlays.iteratorFrom(prefix);
    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, Mutation> entry = iterator.next();

      DocumentKey key = entry.getKey();
      if (!path.isPrefixOf(key.getPath())) {
        break;
      }
      // Documents from sub-collections
      if (key.getPath().length() != immediateChildrenPathLength) {
        continue;
      }
      result.put(entry.getKey(), entry.getValue());
    }

    return result;
  }
}
