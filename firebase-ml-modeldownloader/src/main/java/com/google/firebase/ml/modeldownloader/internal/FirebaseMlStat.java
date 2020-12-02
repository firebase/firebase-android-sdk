// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader.internal;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import com.google.firebase.encoders.annotations.Encodable;

/** All values should match internal FirebaseMlLogEvent */
@Encodable
@AutoValue
public abstract class FirebaseMlStat {

  @IntDef({EventName.MODEL_DOWNLOAD, EventName.MODEL_UPDATE, EventName.UNKNOWN_EVENT})
  public @interface EventName {
    int UNKNOWN_EVENT = 0;

    int MODEL_DOWNLOAD = 100;
    int MODEL_UPDATE = 101;
  }

  @EventName
  @NonNull
  public abstract int getEventName();

  public byte[] getBytes() {
    return String.valueOf(getEventName()).getBytes();
  }

  static Builder builder() {
    return new AutoValue_FirebaseMlStat.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setEventName(@EventName int value);

    abstract FirebaseMlStat build();
  }
}
