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
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class AlarmManagerSchedulerTest {
  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").build();

  private final Context context = RuntimeEnvironment.application;
  private final EventStore store = new InMemoryEventStore();
  private final AlarmManager alarmManager =
      spy((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
  private final AlarmManagerScheduler scheduler =
      new AlarmManagerScheduler(context, store, () -> 1, alarmManager);

  private Intent getIntent() {
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(
        SchedulerUtil.BACKEND_NAME, TRANSPORT_CONTEXT.getBackendName());
    assertThat(context.getPackageName()).isEqualTo("org.robolectric.default");
    intentDataBuilder.appendQueryParameter(
        SchedulerUtil.APPLICATION_BUNDLE_ID, context.getPackageName());

    Intent intent = new Intent(context, AlarmManagerSchedulerBroadcastReceiver.class);
    intent.setData(intentDataBuilder.build());
    return intent;
  }

  @Test
  public void schedule_longWaitTimeFirstAttempt() {
    Intent intent = getIntent();
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    verify(alarmManager, times(1)).set(eq(AlarmManager.ELAPSED_REALTIME), eq((long) 999999), any());
  }

  @Test
  public void schedule_noTimeRecordedForBackend() {
    Intent intent = getIntent();
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    verify(alarmManager, times(1))
        .set(eq(AlarmManager.ELAPSED_REALTIME), eq((long) 60000), any()); // 2*DELTA
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt() {
    Intent intent = getIntent();
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    verify(alarmManager, times(1))
        .set(eq(AlarmManager.ELAPSED_REALTIME), eq((long) 60000), any()); // 2*DELTA
  }

  @Test
  public void schedule_longWaitTimeTenthAttempt() {
    Intent intent = getIntent();
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 10);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    verify(alarmManager, times(1))
        .set(eq(AlarmManager.ELAPSED_REALTIME), gt((long) 1000000), any());
  }

  @Test
  public void schedule_twoJobs() {
    Intent intent = getIntent();
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    assertThat(scheduler.isJobServiceOn(intent)).isFalse();
    scheduler.schedule(TRANSPORT_CONTEXT, 10);
    assertThat(scheduler.isJobServiceOn(intent)).isTrue();
    scheduler.schedule(TRANSPORT_CONTEXT, 11);
    verify(alarmManager, times(1))
        .set(eq(AlarmManager.ELAPSED_REALTIME), gt((long) 1000000), any());
  }
}
