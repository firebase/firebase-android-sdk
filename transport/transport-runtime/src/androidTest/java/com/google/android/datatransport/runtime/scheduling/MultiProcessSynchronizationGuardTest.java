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
import android.os.RemoteException;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.runtime.ITestRemoteLockRpc;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MultiProcessSynchronizationGuardTest {
  private ServiceTestRule rule = new ServiceTestRule();

  private ITestRemoteLockRpc process1;
  private ITestRemoteLockRpc process2;

  @Before
  public void setUp() throws TimeoutException, RemoteException {
    process1 = bindTestingRpc(TestService.Local.class);

    process2 = bindTestingRpc(TestService.Remote.class);

    assertThat(process1.getPid()).isNotEqualTo(process2.getPid());
  }

  @After
  public void after() {
    rule.unbindService();
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
      ITestRemoteLockRpc process1, ITestRemoteLockRpc process2) throws RemoteException {
    lockAndRunOrFail(process1, () -> assertCanLock(process2, false));

    assertCanLock(process2, true);
  }

  private static void doTest_whenConnectionsAreOpen(
      ITestRemoteLockRpc process1, ITestRemoteLockRpc process2) throws RemoteException {
    // initialize db connections before contending for lock.
    assertCanLock(process1, true);
    assertCanLock(process2, true);

    lockAndRunOrFail(process1, () -> assertCanLock(process2, false));

    assertCanLock(process2, true);
  }

  private static void lockAndRunOrFail(ITestRemoteLockRpc rpc, ThrowingRunnable runnable)
      throws RemoteException {
    assertThat(rpc.tryAcquireLock()).isTrue();
    try {
      runnable.run();
    } finally {
      rpc.releaseLock();
    }
  }

  private static void assertCanLock(ITestRemoteLockRpc rpc, boolean can) throws RemoteException {
    boolean locked = rpc.tryAcquireLock();

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

  private ITestRemoteLockRpc bindTestingRpc(Class<? extends Service> serviceClass)
      throws TimeoutException {
    ITestRemoteLockRpc rpc =
        ITestRemoteLockRpc.Stub.asInterface(
            rule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), serviceClass)));
    return rpc;
  }
}
