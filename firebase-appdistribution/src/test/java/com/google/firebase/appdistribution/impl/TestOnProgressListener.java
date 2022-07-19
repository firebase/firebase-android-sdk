// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

import androidx.annotation.NonNull;
import com.google.firebase.appdistribution.OnProgressListener;
import com.google.firebase.appdistribution.UpdateProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper listener that awaits a specific number of progress events on an {@code UpdateTask}.
 *
 * <p>This works around a limitation of the Tasks API where await() cannot be called on the main
 * thread. This listener works around it by running itself on a different thread, thus allowing the
 * main thread to be woken up when the Tasks complete.
 *
 * <p>Note: Calling {@link #await()} from a Robolectric test does block the main thread, since those
 * tests are executed on the main thread.
 */
class TestOnProgressListener implements OnProgressListener {
  private static final long TIMEOUT_MS = 5000;
  private final int expectedProgressCount;
  private final CountDownLatch latch;
  private final List<UpdateProgress> progressUpdates = new ArrayList<>();

  private TestOnProgressListener(int expectedProgressCount) {
    this.expectedProgressCount = expectedProgressCount;
    this.latch = new CountDownLatch(expectedProgressCount);
  }

  static TestOnProgressListener withExpectedCount(int expectedCount) {
    return new TestOnProgressListener(expectedCount);
  }

  @Override
  public void onProgressUpdate(@NonNull UpdateProgress updateProgress) {
    progressUpdates.add(updateProgress);
    latch.countDown();
  }

  /** Blocks until the {@link #onProgressUpdate} is called the expected number of times. */
  List<UpdateProgress> await() throws InterruptedException {
    if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      throw new InterruptedException(
          String.format(
              "Timed out waiting for progress events (expected = %d, actual = %d)",
              expectedProgressCount, progressUpdates.size()));
    }
    return progressUpdates;
  }
}
