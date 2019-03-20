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
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Factory that produces instances of {@link CreationContext}. */
@Singleton
final class CreationContextFactory {
  private final Clock wallClock;
  private final Clock monotonicClock;

  @Inject
  CreationContextFactory(@WallTime Clock wallClock, @Monotonic Clock monotonicClock) {
    this.wallClock = wallClock;
    this.monotonicClock = monotonicClock;
  }

  public CreationContext create(String backendName) {
    return CreationContext.create(backendName, wallClock, monotonicClock);
  }
}
