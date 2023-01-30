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

package com.google.firebase.database.android;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.database.core.EventTarget;

public class AndroidEventTarget implements EventTarget {
  private final Handler handler;

  // TODO(b/258277572): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  public AndroidEventTarget() {
    this.handler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void postEvent(Runnable r) {
    handler.post(r);
  }

  @Override
  public void shutdown() {
    // No-op on android, there's no thread to shutdown, this just posts to the main Looper
  }

  @Override
  public void restart() {
    // No-op
  }
}
