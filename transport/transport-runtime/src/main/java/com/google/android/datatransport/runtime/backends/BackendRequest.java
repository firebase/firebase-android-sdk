// Copyright 2018 Google LLC
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

package com.google.android.datatransport.runtime.backends;

import androidx.annotation.Nullable;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.auto.value.AutoValue;

/** Encapsulates a send request made to an individual {@link TransportBackend}. */
@AutoValue
public abstract class BackendRequest {
  /** Events to be sent to the backend. */
  public abstract Iterable<EventInternal> getEvents();

  @Nullable
  public abstract byte[] getExtras();

  /** Creates a new instance of the request. */
  public static BackendRequest create(Iterable<EventInternal> events) {
    return BackendRequest.builder().setEvents(events).build();
  }

  public static Builder builder() {
    return new AutoValue_BackendRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setEvents(Iterable<EventInternal> events);

    public abstract Builder setExtras(@Nullable byte[] extras);

    public abstract BackendRequest build();
  }
}
