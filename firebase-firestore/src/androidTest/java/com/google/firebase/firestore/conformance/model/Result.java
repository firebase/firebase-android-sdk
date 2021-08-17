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

/** Result from query execution. */
@AutoValue
public abstract class Result {

  /** A list of documents to expect from the query. */
  public abstract List<Document> getDocuments();

  /** Builder for {@link Result}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static Result.Builder builder() {
      return new AutoValue_Result.Builder().setDocuments(Collections.emptyList());
    }

    @NonNull
    public abstract Result.Builder setDocuments(@NonNull List<Document> documents);

    @NonNull
    public abstract Result build();
  }
}
