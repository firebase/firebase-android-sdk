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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;

import android.util.Log;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.junit.rules.ExpectedLogMessagesRule;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLog.LogItem;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigLoggerTest {
  @Rule
  public final ExpectedLogMessagesRule expectedLogMessagesRule = new ExpectedLogMessagesRule();

  private static final String TAG = "FirebaseRemoteConfig";

  private ConfigLogger logger = ConfigLogger.getLogger();

  @Test
  public void i_loggerAtDefaultLogLevel_logs() {
    String message = "Some information";

    logger.i(message);

    expectedLogMessagesRule.expectLogMessage(Log.INFO, TAG, message);
  }

  @Test
  public void e_withThrowable_logsThrowable() {
    String message = "Error message";
    Exception exception = new Exception("This was thrown.");
    LogItem logItem = new LogItem(Log.ERROR, TAG, message, exception);

    logger.e(message, exception);

    List<LogItem> logs = ShadowLog.getLogsForTag(TAG);
    assertThat(logs).contains(logItem);
    expectedLogMessagesRule.expectLogMessage(Log.ERROR, TAG, message);
  }

  @Test
  public void setLogLevel_suppressesLogsBelowLevel() {
    logger.setLogLevel(Log.ERROR);
    logger.w("Warning");

    List<LogItem> logs = ShadowLog.getLogsForTag(TAG);
    assertThat(logs).isEmpty();
  }

  @Test
  public void getLogLevel_getsSetLogLevel() {
    logger.setLogLevel(Log.WARN);

    assertThat(logger.getLogLevel()).isEqualTo(Log.WARN);
  }
}
