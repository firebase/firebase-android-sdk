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

package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Parcel;
import android.os.SystemClock;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowSystemClock;

/**
 * Unit tests for {@link Timer}.
 *
 * <p>IMPORTANT: {@link SystemClock} is paused in tests, and can only be advanced through specific
 * methods such as {@link ShadowSystemClock#advanceBy(Duration)} which are shadowed by Robolectic.
 */
@RunWith(RobolectricTestRunner.class)
public class TimerTest {

  @Before
  public void setUp() {}

  @Test
  public void getDurationMicros_returnsDifferenceBetweenElapsedRealtime() {
    // Robolectric shadows SystemClock, which is paused and can only change via specific methods.
    Timer start = new Timer();
    ShadowSystemClock.advanceBy(Duration.ofMillis(100000));
    Timer end = new Timer();
    assertThat(start.getDurationMicros(end)).isEqualTo(MILLISECONDS.toMicros(100000));
  }

  @Test
  public void ofElapsedRealtime_createsNewTimerWithArgumentElapsedRealtime() {
    // Robolectric shadows SystemClock, which is paused and can only change via specific methods.
    long refElapsedRealtime = SystemClock.elapsedRealtime();
    Timer ref = new Timer();
    Timer past = Timer.ofElapsedRealtime(refElapsedRealtime - 100);
    Timer future = Timer.ofElapsedRealtime(refElapsedRealtime + 100);

    assertThat(past.getDurationMicros(ref)).isEqualTo(MILLISECONDS.toMicros(100));
    assertThat(ref.getDurationMicros(future)).isEqualTo(MILLISECONDS.toMicros(100));
  }

  @Test
  public void ofElapsedRealtime_extrapolatesWallTime() {
    // Robolectric shadows SystemClock, which is paused and can only change via specific methods.
    ShadowSystemClock.advanceBy(Duration.ofMillis(10000000));
    long refElapsedRealtime = SystemClock.elapsedRealtime();
    Timer ref = new Timer();
    Timer past = Timer.ofElapsedRealtime(refElapsedRealtime - 500);
    Timer morePast = Timer.ofElapsedRealtime(refElapsedRealtime - 500000);
    Timer future = Timer.ofElapsedRealtime(refElapsedRealtime + 500);
    Timer moreFuture = Timer.ofElapsedRealtime(refElapsedRealtime + 500000);

    assertThat(past.getMicros()).isLessThan(ref.getMicros());
    assertThat(morePast.getMicros()).isLessThan(past.getMicros());
    assertThat(future.getMicros()).isGreaterThan(ref.getMicros());
    assertThat(moreFuture.getMicros()).isGreaterThan(future.getMicros());
  }

  @Test
  public void testCreate() throws InterruptedException {
    Timer timer = new Timer();
    Thread.sleep(10);
    long currentTimeMicros = MILLISECONDS.toMicros(System.currentTimeMillis());

    assertThat(timer.getMicros()).isNotEqualTo(currentTimeMicros);
    assertThat(timer.getMicros()).isLessThan(currentTimeMicros);
  }

  @Test
  public void testReset() throws InterruptedException {
    Timer timer = new Timer();
    Thread.sleep(10);
    long currentTimeMicros = MILLISECONDS.toMicros(System.currentTimeMillis());

    assertThat(timer.getMicros()).isNotEqualTo(currentTimeMicros);
    timer.reset();

    assertThat(timer.getMicros()).isAtLeast(currentTimeMicros);
  }

  @Test
  public void testGetCurrentTimestampMicros() {
    Timer timer = new Timer(0, 0);
    long currentTimeSmallest = timer.getCurrentTimestampMicros();

    assertThat(timer.getMicros()).isEqualTo(0);
    assertThat(currentTimeSmallest).isAtMost(timer.getDurationMicros());
  }

  @Test
  public void testParcel() {
    Timer timer1 = new Timer(1000, 1000000);

    Parcel p1 = Parcel.obtain();
    timer1.writeToParcel(p1, 0);
    byte[] bytes = p1.marshall();

    Parcel p2 = Parcel.obtain();
    p2.unmarshall(bytes, 0, bytes.length);
    p2.setDataPosition(0);

    Timer timer2 = Timer.CREATOR.createFromParcel(p2);
    Assert.assertEquals(timer1.getMicros(), timer2.getMicros());
    Assert.assertEquals(timer1.getDurationMicros(timer2), 0);

    p1.recycle();
    p2.recycle();
  }

  /** Helper for other tests that returns elapsedRealtimeMicros from a Timer object */
  public static long getElapsedRealtimeMicros(Timer timer) {
    return new Timer(0, 0).getDurationMicros(timer);
  }
}
