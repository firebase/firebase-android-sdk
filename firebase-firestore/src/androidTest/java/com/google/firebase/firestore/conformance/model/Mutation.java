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

@AutoValue
public abstract class Mutation {

  enum Type {
    SET,
    UPDATE,
    DELETE;
  }

  /** Action type to apply. */
  public abstract Type getType();

  public abstract String getCollectionId();

  public abstract Document getDocument();

  /** Whether this Mutation matches the Query. */
  public abstract boolean isShouldTrigger();

  /** Builder for {@link Mutation}. */
  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public static Mutation.Builder builder() {
      return new AutoValue_Mutation.Builder();
    }

    @NonNull
    public abstract Mutation.Builder setType(@NonNull Type type);

    @NonNull
    public abstract Mutation.Builder setCollectionId(@NonNull String collectionId);

    @NonNull
    public abstract Mutation.Builder setDocument(@NonNull Document document);

    @NonNull
    public abstract Mutation.Builder setShouldTrigger(boolean shouldTrigger);

    @NonNull
    public abstract Mutation build();
  }
}
