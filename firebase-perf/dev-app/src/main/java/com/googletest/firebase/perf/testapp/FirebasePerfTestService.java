// Copyright 2020 Google LLC
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

package com.googletest.firebase.perf.testapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * A Service to test the app start in background. To start the app in background with this service
 * adb shell am startservice -n
 * com.googletest.firebase.perf.testapp.prod/com.googletest.firebase.perf.testapp.FirebasePerfTestService
 */
public class FirebasePerfTestService extends Service {

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d("FirebasePerfTestService", "entering onStartCommand()");
    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.d("FirebasePerfTestService", "entering onCreate()");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
