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

import android.os.Parcel;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link Timer}. */
@RunWith(RobolectricTestRunner.class)
public class TimerTest {

  @Before
  public void setUp() {}

  @Test
  public void testCreate() throws InterruptedException {
    Timer timer = new Timer();
    Thread.sleep(10);
    long currentTimeMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

    assertThat(timer.getMicros()).isNotEqualTo(currentTimeMicros);
    assertThat(timer.getMicros()).isLessThan(currentTimeMicros);
  }

  @Test
  public void testReset() throws InterruptedException {
    Timer timer = new Timer();
    Thread.sleep(10);
    long currentTimeMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());

    assertThat(timer.getMicros()).isNotEqualTo(currentTimeMicros);
    timer.reset();

    assertThat(timer.getMicros()).isAtLeast(currentTimeMicros);
  }

  @Test
  public void testGetCurrentTimestampMicros() {
    Timer timer = new Timer(0);
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
    Assert.assertEquals(timer1.getElapsedRealtimeMicros(), timer2.getElapsedRealtimeMicros());

    p1.recycle();
    p2.recycle();
  }
}
