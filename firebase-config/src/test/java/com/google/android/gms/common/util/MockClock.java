// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.gms.common.util;

import android.os.SystemClock;
import com.google.android.gms.common.internal.Preconditions;

/**
 * Simple clock implementation that returns a controllable time value.
 *
 * @author tomwilson@google.com (Tom Wilson)
 */
public class MockClock implements Clock {
  private long mCurrentTimeMs;
  private long mCurrentElapsedRealtime;
  private long mNanoTime;

  public MockClock(long currentTimeMs) {
    setCurrentTime(currentTimeMs);
  }

  @Override
  public long currentTimeMillis() {
    return mCurrentTimeMs;
  }

  public void setCurrentTime(long currentTimeMs) {
    Preconditions.checkState(currentTimeMs >= 0);
    mCurrentTimeMs = currentTimeMs;
  }

  @Override
  public long elapsedRealtime() {
    return mCurrentElapsedRealtime;
  }

  public void setElapsedRealtime(long timeInMillis) {
    mCurrentElapsedRealtime = timeInMillis;
  }

  public void advance(long incrementMillis) {
    setCurrentTime(currentTimeMillis() + incrementMillis);
    setElapsedRealtime(elapsedRealtime() + incrementMillis);
  }

  @Override
  public long nanoTime() {
    return mNanoTime;
  }

  public void setNanoTime(long nanoTime) {
    Preconditions.checkState(nanoTime >= 0);
    mNanoTime = nanoTime;
  }

  @SuppressWarnings("StaticOrDefaultInterfaceMethod")
  @Override
  public long currentThreadTimeMillis() {
    return SystemClock.currentThreadTimeMillis();
  }
}
