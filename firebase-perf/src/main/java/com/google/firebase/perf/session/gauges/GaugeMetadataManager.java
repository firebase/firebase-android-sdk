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

package com.google.firebase.perf.session.gauges;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.StorageUnit;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.GaugeMetadata;

/**
 * The {@code GaugeMetadataManager} class is responsible for collecting {@link GaugeMetadata}
 * information.
 */
class GaugeMetadataManager {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final Runtime runtime;
  private final ActivityManager activityManager;
  private final MemoryInfo memoryInfo;

  GaugeMetadataManager(Context appContext) {
    this(Runtime.getRuntime(), appContext);
  }

  @VisibleForTesting
  GaugeMetadataManager(Runtime runtime, Context appContext) {
    this.runtime = runtime;
    this.activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
    memoryInfo = new ActivityManager.MemoryInfo();
    activityManager.getMemoryInfo(memoryInfo);
  }

  /**
   * Returns the maximum amount of memory (in kilobytes) that app can use before an
   * OutOfMemoryException is triggered.
   */
  public int getMaxAppJavaHeapMemoryKb() {
    return Utils.saturatedIntCast(StorageUnit.BYTES.toKilobytes(runtime.maxMemory()));
  }

  /**
   * Returns the maximum amount of memory (in kilobytes) the app is encouraged to use to be properly
   * respectful of the limits of the client device.
   */
  public int getMaxEncouragedAppJavaHeapMemoryKb() {
    return Utils.saturatedIntCast(
        StorageUnit.MEGABYTES.toKilobytes(activityManager.getMemoryClass()));
  }

  /** Returns the total memory (in kilobytes) accessible by the kernel (called the RAM size). */
  public int getDeviceRamSizeKb() {
    return Utils.saturatedIntCast(StorageUnit.BYTES.toKilobytes(memoryInfo.totalMem));
  }
}
