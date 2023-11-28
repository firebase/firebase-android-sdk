// Copyright 2023 Google LLC
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

import androidx.annotation.NonNull;
import java.util.Objects;

public class SnapshotListenOptions {
  private final MetadataChanges metadataChanges;
  private final ListenSource source;

  private SnapshotListenOptions(Builder builder) {
    this.metadataChanges = builder.metadataChanges;
    this.source = builder.source;
  }

  @NonNull
  public MetadataChanges getMetadataChanges() {
    return metadataChanges;
  }

  @NonNull
  public ListenSource getSource() {
    return source;
  }

  public static class Builder {
    private MetadataChanges metadataChanges = MetadataChanges.EXCLUDE;
    private ListenSource source = ListenSource.DEFAULT;

    public Builder() {}

    @NonNull
    public Builder setMetadataChanges(@NonNull MetadataChanges metadataChanges) {
      this.metadataChanges = metadataChanges;
      return this;
    }

    @NonNull
    public Builder setSource(@NonNull ListenSource source) {
      this.source = source;
      return this;
    }

    @NonNull
    public SnapshotListenOptions build() {
      return new SnapshotListenOptions(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SnapshotListenOptions that = (SnapshotListenOptions) o;
    return metadataChanges == that.metadataChanges && source == that.source;
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadataChanges, source);
  }

  @Override
  public String toString() {
    return "SnapshotListenOptions{"
        + "metadataChanges="
        + metadataChanges
        + ", source="
        + source
        + '}';
  }
}
