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
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class TestCase {

  /** Name of the test. * */
  public abstract String getName();

  /** A list of collections with documents to add to Firestore before test. * */
  public abstract List<Collection> getCollections();

  /** Query to test. */
  public abstract Query getQuery();

  @Nullable
  public abstract Result getResult();

  public abstract boolean getException();

  /** OPTIONAL: if set, test will apply series of mutations to test listen. * */
  @Nullable
  public abstract List<Mutation> getMutations();

  /** Returns the last segment of test name. */
  public String toString() {
    String[] fullPath = getName().split("\\.");
    return fullPath[fullPath.length - 1];
  }

  /** Builder for {@link TestCase}. */
  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public static TestCase.Builder builder() {
      return new AutoValue_TestCase.Builder();
    }

    @NonNull
    public abstract TestCase.Builder setName(@NonNull String name);

    @NonNull
    public abstract TestCase.Builder setCollections(@NonNull List<Collection> collections);

    @NonNull
    public abstract TestCase.Builder setQuery(@NonNull Query query);

    @NonNull
    public abstract TestCase.Builder setResult(@NonNull Result result);

    @NonNull
    public abstract TestCase.Builder setException(boolean exception);

    @NonNull
    public abstract TestCase.Builder setMutations(@NonNull List<Mutation> mutations);

    @NonNull
    public abstract TestCase build();
  }
}
