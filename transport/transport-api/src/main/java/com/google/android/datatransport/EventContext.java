// Copyright 2024 Google LLC
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

package com.google.android.datatransport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class EventContext {
  @Nullable
  public abstract String getPseudonymousId();

  @Nullable
  @SuppressWarnings("mutable")
  public abstract byte[] getExperimentIdsClear();

  @Nullable
  @SuppressWarnings("mutable")
  public abstract byte[] getExperimentIdsEncrypted();

  public static Builder builder() {
    return new AutoValue_EventContext.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setPseudonymousId(String value);

    @NonNull
    public abstract Builder setExperimentIdsClear(byte[] value);

    @NonNull
    public abstract Builder setExperimentIdsEncrypted(byte[] value);

    @NonNull
    public abstract EventContext build();
  }
}
