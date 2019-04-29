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

import android.os.Process;
import com.google.android.datatransport.runtime.ITestRemoteLockRpc;
import com.google.android.datatransport.runtime.scheduling.locking.Locker;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link ITestRemoteLockRpc} that uses {@link
 * com.google.android.datatransport.runtime.synchronization.SynchronizationGuard}.
 */
class RemoteLockRpc extends ITestRemoteLockRpc.Stub {
  private final Executor executor;
  private final SynchronizationGuard guard;
  private final Locker<Boolean> acquireReleaseLocker = new Locker<>();

  RemoteLockRpc(Executor executor, SynchronizationGuard guard) {
    this.executor = executor;
    this.guard = guard;
  }

  @Override
  public boolean tryAcquireLock() {
    Locker<Boolean> sectionEnteredLocker = new Locker<>();
    executor.execute(
        () -> {
          try {
            guard.runCriticalSection(
                () -> {
                  sectionEnteredLocker.setResult(true);
                  acquireReleaseLocker.await();
                  return null;
                });
            acquireReleaseLocker.setResult(true);
          } catch (SynchronizationException ex) {
            sectionEnteredLocker.setResult(false);
          }
        });
    Boolean result = sectionEnteredLocker.await();
    return result == null ? false : result;
  }

  @Override
  public void releaseLock() {
    // signal thread to release lock
    acquireReleaseLocker.setResult(true);
    // wait for lock to be released
    acquireReleaseLocker.await();
  }

  @Override
  public long getPid() {
    return Process.myPid();
  }
}
