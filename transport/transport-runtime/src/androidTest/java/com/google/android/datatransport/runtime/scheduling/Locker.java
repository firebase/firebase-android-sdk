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

package com.google.android.datatransport.runtime.scheduling;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Simple locking mechanism that supports results to the waiting/blocked side. */
class Locker<T> {
  private final Semaphore semaphore;
  private T result;

  Locker() {
    semaphore = new Semaphore(1, true);
    acquire();
  }

  T await() {
    acquire();
    T r = result;
    result = null;
    return r;
  }

  T await(long timeoutMs) {
    if (tryAcquire(timeoutMs)) {
      T r = result;
      result = null;
      return r;
    }
    return null;
  }

  void setResult(T result) {
    this.result = result;
    semaphore.release();
  }

  private void acquire() {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private boolean tryAcquire(long timeoutMs) {
    try {
      return semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
