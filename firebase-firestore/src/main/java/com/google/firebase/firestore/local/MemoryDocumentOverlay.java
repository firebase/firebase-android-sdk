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
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import java.util.HashMap;
import java.util.Map;

public class MemoryDocumentOverlay implements DocumentOverlay {
  private Map<DocumentKey, Mutation> overlays = new HashMap<>();

  @Nullable
  @Override
  public Mutation getOverlay(DocumentKey key) {
    return overlays.get(key);
  }

  @Override
  public void saveOverlay(DocumentKey key, Mutation mutation) {
    overlays.put(key, mutation);
  }

  @Override
  public void removeOverlay(DocumentKey key) {
    overlays.remove(key);
  }
}
