// Copyright 2026 Google LLC
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
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Process;
import androidx.test.core.app.ApplicationProvider;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivityManager;

/** Unit tests for {@link AppStartCause}. */
@RunWith(RobolectricTestRunner.class)
public class AppStartCauseTest {

  private final Context appContext = ApplicationProvider.getApplicationContext();

  /** Seeds {@code getMyMemoryState}'s reply with the desired importance for this process. */
  private void setProcessImportance(int importance) {
    ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
    ShadowActivityManager shadow = shadowOf(am);
    RunningAppProcessInfo info = new RunningAppProcessInfo();
    info.pid = Process.myPid();
    info.importance = importance;
    shadow.setProcesses(Collections.singletonList(info));
  }

  @Test
  public void capture_nullContext_returnsUnknown() {
    AppStartCause cause = AppStartCause.capture(null);

    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.UNKNOWN);
    assertThat(cause.importance).isEqualTo(-1);
  }

  @Test
  @Config(sdk = 33)
  public void capture_preApi34_returnsUnknownButRecordsImportance() {
    setProcessImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

    AppStartCause cause = AppStartCause.capture(appContext);

    // Pre-API-34: classification is owned by the legacy AppStartTrace path.
    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.UNKNOWN);
    assertThat(cause.apiLevel).isEqualTo(33);
    assertThat(cause.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
  }

  @Test
  @Config(sdk = 34)
  public void capture_api34_foregroundImportance_classifiesForeground() {
    setProcessImportance(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

    AppStartCause cause = AppStartCause.capture(appContext);

    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.FOREGROUND);
    assertThat(cause.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    assertThat(cause.apiLevel).isEqualTo(34);
  }

  @Test
  @Config(sdk = 34)
  public void capture_api34_serviceImportance_classifiesUnknown() {
    setProcessImportance(RunningAppProcessInfo.IMPORTANCE_SERVICE);

    AppStartCause cause = AppStartCause.capture(appContext);

    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.UNKNOWN);
    assertThat(cause.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_SERVICE);
  }

  @Test
  @Config(sdk = 34)
  public void capture_api34_cachedImportance_classifiesUnknown() {
    setProcessImportance(RunningAppProcessInfo.IMPORTANCE_CACHED);

    AppStartCause cause = AppStartCause.capture(appContext);

    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.UNKNOWN);
    assertThat(cause.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_CACHED);
  }

  @Test
  public void constructor_visibleForTesting_preservesAllFields() {
    AppStartCause cause =
        new AppStartCause(
            AppStartCause.Cause.FOREGROUND, RunningAppProcessInfo.IMPORTANCE_FOREGROUND, 35);

    assertThat(cause.cause).isEqualTo(AppStartCause.Cause.FOREGROUND);
    assertThat(cause.importance).isEqualTo(RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    assertThat(cause.apiLevel).isEqualTo(35);
  }
}
