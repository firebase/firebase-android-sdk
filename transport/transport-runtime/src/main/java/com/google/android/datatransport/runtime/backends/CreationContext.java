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

import com.google.android.datatransport.runtime.time.Clock;
import com.google.auto.value.AutoValue;

/**
 * Values that are provided to {@link BackendFactory}s when a backend is instantiated.
 *
 * <p>Provides access to useful services that are provided by the runtime.
 */
@AutoValue
public abstract class CreationContext {

  /** Returns a {@link Clock} that provides time as known by the device. */
  public abstract Clock getWallClock();

  /** Returns a {@link Clock} that provides a monotonically increasing time since device boot. */
  public abstract Clock getMonotonicClock();

  /** Creates a new instance of {@link CreationContext}. */
  public static CreationContext create(Clock wallClock, Clock monotonicClock) {
    return new AutoValue_CreationContext(wallClock, monotonicClock);
  }
}
