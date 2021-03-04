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

package com.google.firebase.perf.gauges;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import dagger.Module;
import dagger.Provides;

/**
 * Provider for {@link com.google.firebase.perf.gauges}.
 *
 * @hide
 */
@Module
public class GaugeModule {

  @Provides
  Runtime providesRuntime() {
    return Runtime.getRuntime();
  }

  @Provides
  ActivityManager providesActivityManager(Context applicationContext) {
    return (ActivityManager) applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
  }

  @Provides
  MemoryInfo providesMemoryInfo(ActivityManager activityManager) {
    MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    activityManager.getMemoryInfo(memoryInfo);

    return memoryInfo;
  }
}
