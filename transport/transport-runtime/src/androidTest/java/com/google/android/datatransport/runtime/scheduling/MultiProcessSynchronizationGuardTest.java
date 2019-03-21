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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import com.google.android.datatransport.runtime.IRemoteLockRpc;
import com.google.android.datatransport.runtime.scheduling.persistence.SQLiteEventStore;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MultiProcessSynchronizationGuardTest {
  private ServiceTestRule rule = new ServiceTestRule();

  private IRemoteLockRpc process1;
  private IRemoteLockRpc process2;

  @Before
  public void setUp() throws TimeoutException, RemoteException {
    process1 = bindTestingRpc(LocalBinderService.class);

    process2 = bindTestingRpc(RemoteBinderService.class);

    assertThat(process1.getPid()).isNotEqualTo(process2.getPid());
  }

  @Test
  public void locking_whenDbConnectionsNotOpenAndLocalProcessLockedFirst_shouldWorkAsExpected()
      throws RemoteException {
    doTest_whenConnectionsNotOpen(process1, process2);
  }

  @Test
  public void locking_whenDbConnectionsNotOpenAndRemoteProcessLockedFirst_shouldWorkAsExpected()
      throws RemoteException {
    doTest_whenConnectionsNotOpen(process2, process1);
  }

  @Test
  public void locking_whenDbConnectionsAreOpenAndLocalProcessLockedFirst_shouldWorkAsExpected()
      throws RemoteException {
    doTest_whenConnectionsAreOpen(process1, process2);
  }

  @Test
  public void locking_whenDbConnectionsAreOpenAndRemoteProcessLockedFirst_shouldWorkAsExpected()
      throws RemoteException {
    doTest_whenConnectionsAreOpen(process2, process1);
  }

  private static void doTest_whenConnectionsNotOpen(
      IRemoteLockRpc process1, IRemoteLockRpc process2) throws RemoteException {
    lockAndRunOrFail(process1, () -> assertCanLock(process2, false));

    assertCanLock(process2, true);
  }

  private static void doTest_whenConnectionsAreOpen(
      IRemoteLockRpc process1, IRemoteLockRpc process2) throws RemoteException {
    // initialize db connections before contending for lock.
    assertCanLock(process1, true);
    assertCanLock(process2, true);

    lockAndRunOrFail(process1, () -> assertCanLock(process2, false));

    assertCanLock(process2, true);
  }

  private static void lockAndRunOrFail(IRemoteLockRpc rpc, ThrowingRunnable runnable)
      throws RemoteException {
    assertThat(rpc.tryAcquireLock(0)).isTrue();
    try {
      runnable.run();
    } finally {
      rpc.releaseLock();
    }
  }

  private static void assertCanLock(IRemoteLockRpc rpc, boolean can) throws RemoteException {
    boolean locked = rpc.tryAcquireLock(0);

    try {
      if (locked) {
        rpc.releaseLock();
      }
    } finally {
      if (locked != can) {
        fail(String.format("Expected %sto be able to acquire the lock.", can ? "" : "not "));
      }
    }
  }

  private interface ThrowingRunnable {
    void run() throws RemoteException;
  }

  private IRemoteLockRpc bindTestingRpc(Class<? extends Service> serviceClass)
      throws TimeoutException {
    IRemoteLockRpc rpc =
        IRemoteLockRpc.Stub.asInterface(
            rule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), serviceClass)));
    return new WaitingRpc(rpc);
  }

  class WaitingRpc implements IRemoteLockRpc {
    private final IRemoteLockRpc delegate;

    WaitingRpc(IRemoteLockRpc delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean tryAcquireLock(long timeout) throws RemoteException {
      boolean result = delegate.tryAcquireLock(timeout);
      if (!result) {
        SystemClock.sleep(SQLiteEventStore.LOCK_RETRY_BACK_OFF * 2);
      }
      return result;
    }

    @Override
    public void releaseLock() throws RemoteException {
      delegate.releaseLock();
    }

    @Override
    public long getPid() throws RemoteException {
      return delegate.getPid();
    }

    @Override
    public IBinder asBinder() {
      return delegate.asBinder();
    }
  }
}
