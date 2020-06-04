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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;

public class BatteryStateTest extends CrashlyticsTestCase {
  // Tolerance for float comparisons.
  static final float EPSILON = 0.0001f;

  private Intent makeIntent(int status, int level, int scale) {
    final Intent intent = new Intent();
    intent.putExtra(BatteryManager.EXTRA_STATUS, status);
    intent.putExtra(BatteryManager.EXTRA_LEVEL, level);
    intent.putExtra(BatteryManager.EXTRA_SCALE, scale);
    return intent;
  }

  public void testGetBatteryLevel() {
    Context mockContext = mock(Context.class);
    when(mockContext.registerReceiver(isNull(), any())).thenReturn(makeIntent(0, 50, 200));

    BatteryState state = BatteryState.get(mockContext);

    final Float batteryLevel = state.getBatteryLevel();
    assertNotNull(batteryLevel);
    assertEquals(0.25f, batteryLevel, EPSILON);
  }

  public void testNullIntent() {
    final Context mockContext = mock(Context.class);
    when(mockContext.registerReceiver(isNull(), any())).thenReturn(null);

    BatteryState state = BatteryState.get(mockContext);

    assertNull(state.getBatteryLevel());
    assertFalse(state.isPowerConnected());
    assertEquals(1, state.getBatteryVelocity());
  }

  public void testEmptyIntent() {
    final Context mockContext = mock(Context.class);
    when(mockContext.registerReceiver(isNull(), any())).thenReturn(new Intent());

    BatteryState state = BatteryState.get(mockContext);

    assertNull(state.getBatteryLevel());
    assertFalse(state.isPowerConnected());
    assertEquals(1, state.getBatteryVelocity());
  }

  private void doVelocityTest(int velocity, Intent intent) {
    final Context mockContext = mock(Context.class);
    when(mockContext.registerReceiver(isNull(), any())).thenReturn(intent);
    BatteryState state = BatteryState.get(mockContext);
    assertEquals(velocity, state.getBatteryVelocity());
  }

  public void testVelocity() {
    doVelocityTest(
        BatteryState.VELOCITY_FULL, makeIntent(BatteryManager.BATTERY_STATUS_CHARGING, 1, 1));
    doVelocityTest(
        BatteryState.VELOCITY_CHARGING, makeIntent(BatteryManager.BATTERY_STATUS_CHARGING, 1, 2));

    doVelocityTest(
        BatteryState.VELOCITY_UNPLUGGED,
        makeIntent(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1, 1));
    doVelocityTest(
        BatteryState.VELOCITY_UNPLUGGED,
        makeIntent(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 1, 2));
  }
}
