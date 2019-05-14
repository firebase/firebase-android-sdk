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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.google.android.datatransport.runtime.DaggerSynchronizationComponent;
import java.util.concurrent.Executors;

/** Base class fore the rpc test service. */
public abstract class TestService extends Service {
  @Override
  public IBinder onBind(Intent intent) {
    Log.i("TransportService", "My Pid: " + android.os.Process.myPid());
    return new RemoteLockRpc(
        Executors.newCachedThreadPool(),
        DaggerSynchronizationComponent.getGuard(getApplicationContext()));
  }

  @Override
  public boolean onUnbind(Intent intent) {
    DaggerSynchronizationComponent.shutdown();
    return false;
  }

  /** Service instance that runs in the main process of the Android application. */
  public static class Local extends TestService {}

  /** Service instance that runs in a dedicated process of the Android application. */
  public static class Remote extends TestService {}
}
