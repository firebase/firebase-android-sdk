// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.time;

import java.util.concurrent.atomic.AtomicLong;

public class TestClock implements Clock {
  private final AtomicLong timestamp;

  public TestClock(long initialTimestamp) {
    this.timestamp = new AtomicLong(initialTimestamp);
  }

  @Override
  public long getTime() {
    return timestamp.get();
  }

  public void tick() {
    advance(1);
  }

  public void advance(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("cannot advance time backwards.");
    }
    timestamp.addAndGet(value);
  }
}
