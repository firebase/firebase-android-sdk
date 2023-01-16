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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.StorageUnit;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.GaugeMetadata;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code GaugeMetadataManager} class is responsible for collecting {@link GaugeMetadata}
 * information.
 */
class GaugeMetadataManager {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final Runtime runtime;
  private final ActivityManager activityManager;
  private final MemoryInfo memoryInfo;
  private final Context appContext;

  GaugeMetadataManager(Context appContext) {
    this(Runtime.getRuntime(), appContext);
  }

  @VisibleForTesting
  GaugeMetadataManager(Runtime runtime, Context appContext) {
    this.runtime = runtime;
    this.appContext = appContext;
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
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      return Utils.saturatedIntCast(StorageUnit.BYTES.toKilobytes(memoryInfo.totalMem));
    }

    return readTotalRAM(/* procFileName= */ "/proc/meminfo");
  }

  /** Returns the total ram size of the device (in kilobytes) by reading the "proc/meminfo" file. */
  @VisibleForTesting
  int readTotalRAM(String procFileName) {
    try (BufferedReader br = new BufferedReader(new FileReader(procFileName))) {
      for (String s = br.readLine(); s != null; s = br.readLine()) {
        if (s.startsWith("MemTotal")) {
          Matcher m = Pattern.compile("\\d+").matcher(s);
          return m.find() ? Integer.parseInt(m.group()) : 0;
        }
      }
    } catch (IOException ioe) {
      logger.warn("Unable to read '" + procFileName + "' file: " + ioe.getMessage());
    } catch (NumberFormatException nfe) {
      logger.warn("Unable to parse '" + procFileName + "' file: " + nfe.getMessage());
    }

    return 0;
  }
}
