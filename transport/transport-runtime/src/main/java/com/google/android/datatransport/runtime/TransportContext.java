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

package com.google.android.datatransport.runtime;

import androidx.annotation.RestrictTo;
import com.google.android.datatransport.Priority;
import com.google.auto.value.AutoValue;

/**
 * Represents the context which {@link com.google.android.datatransport.Event}s are associated with.
 */
@AutoValue
public abstract class TransportContext {
  /** Backend events are sent to. */
  public abstract String getBackendName();

  /**
   * Priority of the event.
   *
   * <p>For internal use by the library and backend implementations. Not for use by users of the
   * library.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public abstract Priority getPriority();

  /** Returns a new builder for {@link TransportContext}. */
  public static Builder builder() {
    return new AutoValue_TransportContext.Builder().setPriority(Priority.DEFAULT);
  }

  /**
   * Returns a copy of the context with modified {@link Priority}.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public TransportContext withPriority(Priority priority) {
    return builder().setBackendName(getBackendName()).setPriority(priority).build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    private static final Priority[] ALL_PRIORITIES = Priority.values();

    public abstract Builder setBackendName(String name);

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public abstract Builder setPriority(Priority priority);

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public Builder setPriority(int value) {
      if (value < 0 || value >= ALL_PRIORITIES.length) {
        throw new IllegalArgumentException("Unknown Priority for value " + value);
      }
      setPriority(ALL_PRIORITIES[value]);
      return this;
    }

    public abstract TransportContext build();
  }
}
