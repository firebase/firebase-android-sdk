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

package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link Rate}. */
@RunWith(RobolectricTestRunner.class)
public class RateTest {

  @Test
  public void ratePerMinute() throws Exception {
    Rate twoTokensPerMinute = new Rate(2, 1, TimeUnit.MINUTES);
    Rate twentyTokensPerTenMinutes = new Rate(5, 10, TimeUnit.MINUTES);
    Rate twoTokensPerTenMinutes = new Rate(2, 10, TimeUnit.MINUTES);

    assertThat(twoTokensPerMinute.getTokensPerSeconds()).isEqualTo(2.0 / 60);
    assertThat(twentyTokensPerTenMinutes.getTokensPerSeconds()).isEqualTo((5.0 / 10) / 60);
    assertThat(twoTokensPerTenMinutes.getTokensPerSeconds()).isEqualTo((2.0 / 10) / 60);
  }

  @Test
  public void ratePerSecond() throws Exception {
    Rate twoTokensPerSecond = new Rate(2, 1, TimeUnit.SECONDS);
    Rate twoTokensPerThirtySeconds = new Rate(2, 30, TimeUnit.SECONDS);
    Rate seventyTokensPerThirtySeconds = new Rate(70, 30, TimeUnit.SECONDS);

    assertThat(twoTokensPerSecond.getTokensPerSeconds()).isEqualTo(2.0);
    assertThat(twoTokensPerThirtySeconds.getTokensPerSeconds()).isEqualTo(2.0 / 30);
    assertThat(seventyTokensPerThirtySeconds.getTokensPerSeconds()).isEqualTo(70.0 / 30);
  }

  @Test
  public void ratePerMillisecond() throws Exception {
    Rate twoTokensPerMillisecond = new Rate(2, 1, TimeUnit.MILLISECONDS);
    Rate sevenTokensPerTenMillisecond = new Rate(7, 10, TimeUnit.MILLISECONDS);
    Rate thirtyOneTokensPerTenMillisecond = new Rate(31, 10, TimeUnit.MILLISECONDS);

    assertThat(twoTokensPerMillisecond.getTokensPerSeconds()).isEqualTo(2.0 * 1000);
    assertThat(sevenTokensPerTenMillisecond.getTokensPerSeconds()).isEqualTo((7.0 / 10) * 1000);
    assertThat(thirtyOneTokensPerTenMillisecond.getTokensPerSeconds())
        .isEqualTo((31.0 / 10) * 1000);
  }

  @Test
  public void ratePerMicrosecond() throws Exception {
    Rate twoTokensPerMicrosecond = new Rate(2, 1, TimeUnit.MICROSECONDS);
    Rate sevenTokensPerTenMicroseconds = new Rate(7, 10, TimeUnit.MICROSECONDS);
    Rate thirtyOneTokensPerTenMicroseconds = new Rate(31, 10, TimeUnit.MICROSECONDS);

    assertThat(twoTokensPerMicrosecond.getTokensPerSeconds()).isEqualTo(2.0 * 1000 * 1000);
    assertThat(sevenTokensPerTenMicroseconds.getTokensPerSeconds())
        .isEqualTo((7.0 / 10) * 1000 * 1000);
    assertThat(thirtyOneTokensPerTenMicroseconds.getTokensPerSeconds())
        .isEqualTo((31.0 / 10) * 1000 * 1000);
  }

  @Test
  public void ratePerNanosecond() throws Exception {
    Rate twoTokensPerNanosecond = new Rate(2, 1, TimeUnit.NANOSECONDS);
    Rate sevenTokensPerTenNanoseconds = new Rate(7, 10, TimeUnit.NANOSECONDS);
    Rate thirtyOneTokensPerTenNanoseconds = new Rate(31, 10, TimeUnit.NANOSECONDS);

    assertThat(twoTokensPerNanosecond.getTokensPerSeconds()).isEqualTo(2.0 * 1000 * 1000 * 1000);
    assertThat(sevenTokensPerTenNanoseconds.getTokensPerSeconds())
        .isEqualTo((7.0 / 10) * 1000 * 1000 * 1000);
    assertThat(thirtyOneTokensPerTenNanoseconds.getTokensPerSeconds())
        .isEqualTo((31.0 / 10) * 1000 * 1000 * 1000);
  }
}
