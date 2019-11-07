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

import android.util.Base64;
import androidx.annotation.Nullable;
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

  @Nullable
  public abstract byte[] getExtras();

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

  @Override
  public final String toString() {
    return String.format(
        "TransportContext(%s, %s, %s)",
        getBackendName(),
        getPriority(),
        getExtras() == null ? "" : Base64.encodeToString(getExtras(), Base64.NO_WRAP));
  }

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
    return builder()
        .setBackendName(getBackendName())
        .setPriority(priority)
        .setExtras(getExtras())
        .build();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setBackendName(String name);

    public abstract Builder setExtras(@Nullable byte[] extras);

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public abstract Builder setPriority(Priority priority);

    public abstract TransportContext build();
  }
}
