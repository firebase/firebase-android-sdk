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

package com.google.firebase.inappmessaging.model;

import com.google.auto.value.AutoValue;

/**
 * Value object class representing rate limits.
 *
 * @hide
 */
@AutoValue
public abstract class RateLimit {
  public static Builder builder() {
    return new AutoValue_RateLimit.Builder();
  }

  public abstract String limiterKey();

  public abstract long limit();

  public abstract long timeToLiveMillis();

  /** Builder for {@link RateLimit}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLimiterKey(String limiterKey);

    public abstract Builder setLimit(long limit);

    public abstract Builder setTimeToLiveMillis(long timeToLiveMillis);

    public abstract RateLimit build();
  }
}
