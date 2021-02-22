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

package com.google.firebase.perf.metrics;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link Counter}. */
@RunWith(RobolectricTestRunner.class)
public class CounterTest {
  private static final String COUNTER_1 = "counter_1";

  @Test
  public void testCounter() {
    Counter counter1 = new Counter(COUNTER_1);
    assertThat(counter1.getName()).isEqualTo(COUNTER_1);
    assertThat(counter1.getCount()).isEqualTo(0);

    counter1.increment(1);
    assertThat(counter1.getCount()).isEqualTo(1);
    counter1.increment(10);
    assertThat(counter1.getCount()).isEqualTo(11);
  }

  @Test
  public void testGetCounterAfterSettingIt() {
    Counter counter1 = new Counter(COUNTER_1);
    counter1.setCount(100);
    assertThat(counter1.getCount()).isEqualTo(100);
  }

  @Test
  public void testGetCounterAfterSettingNegativeValue() {
    Counter counter1 = new Counter(COUNTER_1);
    counter1.setCount(-100);
    assertThat(counter1.getCount()).isEqualTo(-100);
  }

  @Test
  public void testIncrementCounterWorksAfterSetCounter() {
    Counter counter1 = new Counter(COUNTER_1);
    counter1.setCount(100);
    counter1.increment(1);
    assertThat(counter1.getCount()).isEqualTo(101);
  }

  @Test
  public void incrementCounterValueByZero() {
    Counter counter1 = new Counter(COUNTER_1);
    assertThat(counter1.getName()).isEqualTo(COUNTER_1);
    assertThat(counter1.getCount()).isEqualTo(0);

    counter1.increment(0);
    assertThat(counter1.getCount()).isEqualTo(0);

    counter1.increment(3);
    assertThat(counter1.getCount()).isEqualTo(3);

    counter1.increment(0);
    assertThat(counter1.getCount()).isEqualTo(3);
  }

  @Test
  public void testParcel() {
    Counter counter1 = new Counter(COUNTER_1);
    counter1.increment(1);
    counter1.increment(10);
    assertThat(counter1.getCount()).isEqualTo(11);

    Parcel p1 = Parcel.obtain();
    counter1.writeToParcel(p1, 0);
    byte[] bytes = p1.marshall();

    Parcel p2 = Parcel.obtain();
    p2.unmarshall(bytes, 0, bytes.length);
    p2.setDataPosition(0);

    Counter counter2 = Counter.CREATOR.createFromParcel(p2);
    assertThat(counter2.getName()).isEqualTo(counter1.getName());
    assertThat(counter2.getCount()).isEqualTo(counter1.getCount());

    p1.recycle();
    p2.recycle();
  }
}
