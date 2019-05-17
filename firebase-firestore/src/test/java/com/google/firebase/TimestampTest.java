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

package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.Assert.assertThrows;

import android.os.Parcel;
import java.util.Date;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TimestampTest {
  @Test
  public void testFromDate() {
    // Very carefully construct an Date that won't lose precision with milliseconds.
    Date input = new Date(22501);
    Timestamp actual = new Timestamp(input);
    assertThat(actual.getSeconds()).isEqualTo(22);
    assertThat(actual.getNanoseconds()).isEqualTo(501000000);
    Timestamp expected = new Timestamp(22, 501000000);
    assertThat(actual).isEqualTo(expected);

    // And with a negative millis.
    input = new Date(-1250);
    actual = new Timestamp(input);
    assertThat(actual.getSeconds()).isEqualTo(-2);
    assertThat(actual.getNanoseconds()).isEqualTo(750000000);
    expected = new Timestamp(-2, 750000000);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testCompare() {
    Timestamp[] timestamps = {
      new Timestamp(12344, 999999999),
      new Timestamp(12345, 0),
      new Timestamp(12345, 1),
      new Timestamp(12345, 99999999),
      new Timestamp(12345, 100000000),
      new Timestamp(12345, 100000001),
      new Timestamp(12346, 0)
    };
    for (int i = 0; i < timestamps.length - 1; ++i) {
      assertThat(timestamps[i].compareTo(timestamps[i + 1])).isEqualTo(-1);
      assertThat(timestamps[i + 1].compareTo(timestamps[i])).isEqualTo(1);
    }
  }

  @Test
  public void testRejectBadDates() {
    assertThrows(IllegalArgumentException.class, () -> new Timestamp(new Date(-70000000000000L)));
    assertThrows(IllegalArgumentException.class, () -> new Timestamp(new Date(300000000000000L)));
    assertThrows(IllegalArgumentException.class, () -> new Timestamp(0, -1));
    assertThrows(IllegalArgumentException.class, () -> new Timestamp(0, 1000000000));
  }

  @Test
  public void testTimestampParcelable() {
    Timestamp timestamp = new Timestamp(1234L, 4567);

    // Write the Timestamp into the Parcel and then rewind the data position for reading.
    Parcel parcel = Parcel.obtain();
    timestamp.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Timestamp recreated = Timestamp.CREATOR.createFromParcel(parcel);
    assertThat(recreated).isEqualTo(timestamp);

    parcel.recycle();
  }
}
