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

package com.google.firebase.storage.internal;

import com.google.android.gms.common.util.Clock;

/** Helper for mocking the clock for network sends. */
public class MockClockHelper implements Clock {

  private long currentTime = 1;

  public static void install() {
    if (!(ExponentialBackoffSender.clock instanceof MockClockHelper)) {
      install(new MockClockHelper());
    }
  }

  public static void install(MockClockHelper clock) {
    MockSleeperHelper sleeper = new MockSleeperHelper(clock);
    ExponentialBackoffSender.clock = clock;
    ExponentialBackoffSender.sleeper = sleeper;
  }

  public void advance(int millis) {
    currentTime += millis;
  }

  @Override
  public long currentTimeMillis() {
    currentTime++;
    return currentTime;
  }

  @Override
  public long elapsedRealtime() {
    currentTime++;
    return currentTime;
  }

  @Override
  public long nanoTime() {
    currentTime++;
    return currentTime;
  }

  @Override
  public long currentThreadTimeMillis() {
    currentTime++;
    return currentTime;
  }
}
