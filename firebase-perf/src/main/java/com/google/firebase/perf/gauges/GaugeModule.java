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

import static android.system.Os.sysconf;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Build;
import android.system.OsConstants;
import com.google.firebase.perf.injection.qualifiers.ClockTicksPerSecond;
import com.google.firebase.perf.injection.qualifiers.ProcFileName;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provider for {@link com.google.firebase.perf.gauges}.
 *
 * @hide
 */
@Module
public class GaugeModule {

  public static final long INVALID_CPU_COLLECTION_FREQUENCY = -1;

  @Provides
  ScheduledExecutorService providesScheduledExecutorService() {
    return Executors.newSingleThreadScheduledExecutor();
  }

  @Provides
  @ProcFileName
  String providesProcFileName() {
    int pid = android.os.Process.myPid();
    String procFileName = String.format("/proc/%d/stat", pid);
    return procFileName;
  }

  @Provides
  @ClockTicksPerSecond
  long providesClockTicksPerSecond() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return sysconf(OsConstants._SC_CLK_TCK);
    } else {
      // TODO(b/110779408): Figure out how to collect this info for Android API 20 and below.
      return INVALID_CPU_COLLECTION_FREQUENCY;
    }
  }

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
