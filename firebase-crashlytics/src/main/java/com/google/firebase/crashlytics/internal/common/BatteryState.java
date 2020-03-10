// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/** A utility class representing the state of the battery. */
class BatteryState {
  static final int VELOCITY_UNPLUGGED = 1;
  static final int VELOCITY_CHARGING = 2;
  static final int VELOCITY_FULL = 3;

  private final Float level;
  private final boolean powerConnected;

  private BatteryState(Float level, boolean powerConnected) {
    this.powerConnected = powerConnected;
    this.level = level;
  }

  boolean isPowerConnected() {
    return powerConnected;
  }

  /**
   * Returns the battery level in the range of [0, 1], if present. May return null, if Android
   * returns a null or malformed Intent.
   */
  public Float getBatteryLevel() {
    return level;
  }

  /**
   * Converts the battery level and power connectivity to a velocity that mimics the iOS
   * UIDeviceBatteryState.
   *
   * @return an int value that mimics the UIDeviceBatteryState enum.
   */
  public int getBatteryVelocity() {
    if (!powerConnected || level == null) {
      return VELOCITY_UNPLUGGED;
    } else if (level < 0.99) {
      return VELOCITY_CHARGING;
    } else {
      return VELOCITY_FULL;
    }
  }

  /** Creates a new BatteryState using data from the given Context. */
  public static BatteryState get(Context context) {
    boolean powerConnected = false;
    Float level = null;

    final IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    final Intent batteryStatusIntent = context.registerReceiver(null, ifilter);
    if (batteryStatusIntent != null) {
      powerConnected = isPowerConnected(batteryStatusIntent);
      level = getLevel(batteryStatusIntent);
    }

    return new BatteryState(level, powerConnected);
  }

  /**
   * Returns whether the power is connected, according to the given Intent.
   *
   * @param batteryStatusIntent from a call to registerReceiver on ACTION_BATTERY_CHANGED.
   * @return true if plugged in.
   */
  private static boolean isPowerConnected(Intent batteryStatusIntent) {
    final int status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    if (status == -1) {
      return false;
    }

    return status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;
  }

  /**
   * Gets the battery level, based on the given Intent.
   *
   * @param batteryStatusIntent from a call to registerReceiver on ACTION_BATTERY_CHANGED.
   * @return battery level in range [0, 1], if data is present, else null.
   */
  private static Float getLevel(Intent batteryStatusIntent) {
    final int level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    final int scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    if (level == -1 || scale == -1) {
      return null;
    }
    return level / (float) scale;
  }
}
