/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner;

import android.app.Application;
import android.os.StrictMode;
import com.google.firebase.FirebaseApp;

/**
 * Initial logic for the App; used to start the {@link FirebaseApp} when the App starts.
 *
 * @author Miraziz Yusupov
 */
public class MainApplication extends Application {
  @Override
  public void onCreate() {
    enableStrictMode();
    super.onCreate();
    FirebaseApp.initializeApp(this);
  }

  private static void enableStrictMode() {
    StrictMode.setThreadPolicy(
        new StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build());
    StrictMode.setVmPolicy(
        new StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .penaltyDeath()
            .build());
  }
}
