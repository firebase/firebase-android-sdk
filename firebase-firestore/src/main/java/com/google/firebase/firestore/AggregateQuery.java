// Copyright 2022 Google LLC
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

package com.google.firebase.firestore;

import android.app.Activity;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.Executor;

public class AggregateQuery {

  AggregateQuery() {}

  @NonNull
  public Query getQuery() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Task<AggregateQuerySnapshot> get() {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public Task<AggregateQuerySnapshot> get(@NonNull AggregateSource source) {
    throw new RuntimeException("not implemented");
  }

  @NonNull
  public ListenConfig listen() {
    throw new RuntimeException("not implemented");
  }

  @Override
  public int hashCode() {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean equals(Object obj) {
    throw new RuntimeException("not implemented");
  }

  public static final class ListenConfig {

    private ListenConfig() {}

    @NonNull
    public ListenConfig executeCallbacksOn(@NonNull Executor executor) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public ListenConfig scopeTo(@NonNull Activity executor) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public ListenConfig includeMetadataOnlyChanges(boolean includeMetadataOnlyChanges) {
      throw new RuntimeException("not implemented");
    }

    @NonNull
    public ListenerRegistration startDirectFromServer(
        @NonNull EventListener<AggregateQuerySnapshot> listener) {
      throw new RuntimeException("not implemented");
    }
  }
}
