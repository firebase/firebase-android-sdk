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

package com.google.android.datatransport.runtime.scheduling.persistence;

import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.auto.value.AutoValue;

/** Holds an {@link EventInternal} with additional information. */
@AutoValue
public abstract class PersistedEvent {
  public abstract long getId();

  public abstract TransportContext getTransportContext();

  public abstract EventInternal getEvent();

  public static PersistedEvent create(
      long id, TransportContext transportContext, EventInternal object) {
    return new AutoValue_PersistedEvent(id, transportContext, object);
  }
}
