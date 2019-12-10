// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class AlarmManagerSchedulerTest {
  private static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;
  private static final long THIRTY_SECONDS = 30 * 1000;
  private static final long INITIAL_TIMESTAMP = 10000000;

  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").build();
  private static final TransportContext UNMETERED_TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").setPriority(Priority.VERY_LOW).build();

  private final Context context = RuntimeEnvironment.application;
  private final EventStore store = new InMemoryEventStore();
  private final AlarmManager alarmManager =
      spy((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
  private final SchedulerConfig config = SchedulerConfig.getDefault(() -> 1);
  private final Clock testClock = new TestClock(INITIAL_TIMESTAMP);
  private final AlarmManagerScheduler scheduler =
      new AlarmManagerScheduler(context, store, alarmManager, testClock, config);

  private Intent getIntent(TransportContext transportContext) {
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(
        AlarmManagerScheduler.BACKEND_NAME, transportContext.getBackendName());
    intentDataBuilder.appendQueryParameter(
        AlarmManagerScheduler.EVENT_PRIORITY,
        String.valueOf(PriorityMapping.toInt(transportContext.getPriority())));

    if (transportContext.getExtras() != null) {
      intentDataBuilder.appendQueryParameter(
          AlarmManagerScheduler.EXTRAS,
          Base64.encodeToString(transportContext.getExtras(), Base64.DEFAULT));
    }

    Intent intent = new Intent(context, AlarmManagerSchedulerBroadcastReceiver.class);
    intent.setData(intentDataBuilder.build());
    return intent;
  }

  @Test
  public void schedule_longWaitTimeFirstAttempt() {
    Intent intent = getIntent(TRANSPORT_CONTEXT);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    verify(alarmManager, times(1))
        .set(eq(AlarmManager.ELAPSED_REALTIME), eq(INITIAL_TIMESTAMP + 999999L), any());
  }

  @Test
  public void schedule_noTimeRecordedForBackend() {
    Intent intent = getIntent(TRANSPORT_CONTEXT);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    verify(alarmManager, times(1))
        .set(
            eq(AlarmManager.ELAPSED_REALTIME),
            eq(INITIAL_TIMESTAMP + THIRTY_SECONDS),
            any()); // 2^0*DELTA
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt() {
    Intent intent = getIntent(TRANSPORT_CONTEXT);
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    verify(alarmManager, times(1))
        .set(
            eq(AlarmManager.ELAPSED_REALTIME),
            eq(INITIAL_TIMESTAMP + THIRTY_SECONDS),
            any()); // 2^0*DELTA
  }

  @Test
  public void schedule_longWaitTimeTenthAttempt() {
    Intent intent = getIntent(TRANSPORT_CONTEXT);
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 10);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    verify(alarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME), gt(1000000L), any());
  }

  @Test
  public void schedule_twoJobs() {
    Intent intent = getIntent(TRANSPORT_CONTEXT);
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 10);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    scheduler.schedule(TRANSPORT_CONTEXT, 11);
    verify(alarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME), gt(1000000L), any());
  }

  @Test
  public void schedule_whenExtrasEvailable_transmitsExtras() {
    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();
    Intent intent = getIntent(transportContext);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(transportContext, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt_multiplePriorities() {
    Intent intent1 = getIntent(TRANSPORT_CONTEXT);
    Intent intent2 = getIntent(UNMETERED_TRANSPORT_CONTEXT);
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    assertThat(scheduler.isJobServiceOn(intent1)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    scheduler.schedule(UNMETERED_TRANSPORT_CONTEXT, 1);

    assertThat(scheduler.isJobServiceOn(intent1)).isTrue();
    verify(alarmManager, times(1))
        .set(
            eq(AlarmManager.ELAPSED_REALTIME),
            eq(INITIAL_TIMESTAMP + THIRTY_SECONDS),
            any()); // 2^0*DELTA

    assertThat(scheduler.isJobServiceOn(intent2)).isTrue();
    verify(alarmManager, times(1))
        .set(
            eq(AlarmManager.ELAPSED_REALTIME),
            eq(INITIAL_TIMESTAMP + TWENTY_FOUR_HOURS),
            any()); // 2^0*DELTA
  }
}
