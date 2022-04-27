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

package com.googletest.firebase.perf.testapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static com.google.common.truth.Truth.assertThat;

import android.util.Log;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** FirebasePerf UI tests */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class FirebasePerfUITest {

  private static String TAG = "PerfTestActivityTest";
  private static final String LOGCAT_CMD =
      "logcat -d -v raw FirebasePerformance:D FirebasePerfTestApp:D *:S";

  @Rule
  public ActivityTestRule<PerfTestActivity> activityRule =
      new ActivityTestRule<>(PerfTestActivity.class);

  @Test
  public void perfEnableDisableTest() {
    onView(withId(R.id.enable)).perform(scrollTo(), click());
    verifyLogs(LOGCAT_CMD, new String[] {"Firebase Performance is Enabled"}, 5000);

    onView(withId(R.id.disable)).perform(scrollTo(), click());
    verifyLogs(LOGCAT_CMD, new String[] {"Firebase Performance is Disabled"}, 5000);

    onView(withId(R.id.isPerfEnabled)).perform(scrollTo(), click());
    verifyLogs(LOGCAT_CMD, new String[] {"isEnabled: false"}, 5000);

    onView(withId(R.id.enable)).perform(scrollTo(), click());
    verifyLogs(LOGCAT_CMD, new String[] {"Firebase Performance is Enabled"}, 5000);
  }

  private void verifyLogs(
      final String logcatCmd, final String[] textToVerify, long timeoutInMillis) {
    ArrayList<String> logsToVerify = new ArrayList<>(Arrays.asList(textToVerify));
    String logs;

    try {
      while (timeoutInMillis >= 0 && !logsToVerify.isEmpty()) {
        logs = dumpLogcat(logcatCmd);

        for (String text : textToVerify) {
          if (logs.contains(text)) {
            logsToVerify.remove(text);
          }
        }

        Thread.sleep(Math.min(1000, timeoutInMillis));
        timeoutInMillis -= 1000;
      }
    } catch (IOException | InterruptedException e) {
      Log.e(TAG, e.toString());
    }

    assertThat(logsToVerify).isEmpty();
  }

  private String dumpLogcat(String logcatCmd) throws IOException {
    Process process = Runtime.getRuntime().exec(logcatCmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    StringBuilder logs = new StringBuilder();
    String line;

    while ((line = reader.readLine()) != null) {
      logs.append(line).append("\n");
    }

    return logs.toString();
  }
}
