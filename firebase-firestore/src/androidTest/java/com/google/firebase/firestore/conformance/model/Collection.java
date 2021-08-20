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

package com.google.firebase.firestore.conformance.model;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firestore.v1.Document;
import java.util.Collections;
import java.util.List;

/** A collection of documents. */
@AutoValue
public abstract class Collection {

  /** Name of the collection. */
  public abstract String getName();

  /** A list of documents in the collection. */
  public abstract List<Document> getDocuments();

  /** Builder for {@link Collection}. */
  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public static Builder builder() {
      return new AutoValue_Collection.Builder().setDocuments(Collections.emptyList());
    }

    @NonNull
    public abstract Builder setName(@NonNull String name);

    @NonNull
    public abstract Builder setDocuments(@NonNull List<Document> documents);

    @NonNull
    public abstract Collection build();
  }
}
