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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import com.google.firebase.messaging.FirelogAnalyticsEvent.FirelogAnalyticsEventWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirelogAnalyticsEventRoboTest {

  private static final String TEST_EVENT_TYPE = "MESSAGE";
  private final Intent testIntent = new Intent();

  @Test
  public void testFirelogAnalyticsEvent_rejectNullEventTypeInstantiation() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FirelogAnalyticsEvent(/*eventType= */ null, testIntent));
  }

  @Test
  public void estFirelogAnalyticsEvent_rejectNullIntentInstantiation() {
    assertThrows(
        NullPointerException.class,
        () -> new FirelogAnalyticsEvent(TEST_EVENT_TYPE, /*intent= */ null));
  }

  @Test
  public void testFirelogAnalyticsEventCreation_instantiationSuccess() {
    new FirelogAnalyticsEvent(TEST_EVENT_TYPE, testIntent);
    // no exception is thrown, instance created successfully
  }

  @Test
  public void testFirelogAnalyticsEvent_gettersWorkProperly() {
    FirelogAnalyticsEvent firelogAnalyticsEvent =
        new FirelogAnalyticsEvent(TEST_EVENT_TYPE, testIntent);

    assertThat(firelogAnalyticsEvent.getEventType()).isEqualTo(TEST_EVENT_TYPE);
    assertThat(firelogAnalyticsEvent.getIntent()).isEqualTo(testIntent);
  }

  @Test
  public void testFirelogAnalyticsEventWrapper_instantiationSuccess() {
    FirelogAnalyticsEvent firelogAnalyticsEvent =
        new FirelogAnalyticsEvent(TEST_EVENT_TYPE, testIntent);

    new FirelogAnalyticsEventWrapper(firelogAnalyticsEvent);
    // no exception is thrown, instance created successfully
  }

  @Test
  public void testFirelogAnalyticsEventWrapper_getterWorkProperly() {
    FirelogAnalyticsEvent firelogAnalyticsEvent =
        new FirelogAnalyticsEvent(TEST_EVENT_TYPE, testIntent);
    FirelogAnalyticsEventWrapper firelogAnalyticsEventWrapper =
        new FirelogAnalyticsEventWrapper(firelogAnalyticsEvent);

    assertThat(firelogAnalyticsEventWrapper.getFirelogAnalyticsEvent().getEventType())
        .isEqualTo(TEST_EVENT_TYPE);
    assertThat(firelogAnalyticsEventWrapper.getFirelogAnalyticsEvent().getIntent())
        .isEqualTo(testIntent);
  }
}
