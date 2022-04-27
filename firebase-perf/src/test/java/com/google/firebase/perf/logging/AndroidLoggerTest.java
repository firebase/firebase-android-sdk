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

package com.google.firebase.perf.logging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link AndroidLogger}. */
@RunWith(RobolectricTestRunner.class)
public class AndroidLoggerTest {

  AndroidLogger testAndroidLogger;

  @Mock private LogWrapper mockLogWrapper;

  @Before
  public void setUp() {
    initMocks(this);
    testAndroidLogger = new AndroidLogger(mockLogWrapper);
  }

  @Test
  public void log_defaultLogcatConfig_disabledLogging() {
    testAndroidLogger.debug("debug log");
    testAndroidLogger.info("info log");
    testAndroidLogger.verbose("verbose log");
    testAndroidLogger.error("error log");
    testAndroidLogger.warn("warn log");

    verify(mockLogWrapper, never()).d(anyString());
    verify(mockLogWrapper, never()).i(anyString());
    verify(mockLogWrapper, never()).v(anyString());
    verify(mockLogWrapper, never()).e(anyString());
    verify(mockLogWrapper, never()).w(anyString());
  }

  @Test
  public void log_logcatEnabled_loggedCorrectly() {
    testAndroidLogger.setLogcatEnabled(true);
    testAndroidLogger.debug("debug log");
    testAndroidLogger.info("info log");
    testAndroidLogger.verbose("verbose log");
    testAndroidLogger.error("error log");
    testAndroidLogger.warn("warn log");

    verify(mockLogWrapper, times(1)).d("debug log");
    verify(mockLogWrapper, times(1)).i("info log");
    verify(mockLogWrapper, times(1)).v("verbose log");
    verify(mockLogWrapper, times(1)).e("error log");
    verify(mockLogWrapper, times(1)).w("warn log");
  }

  @Test
  public void log_logcatDisabled_disabledLogging() {
    testAndroidLogger.setLogcatEnabled(false);
    testAndroidLogger.debug("debug log");
    testAndroidLogger.info("info log");
    testAndroidLogger.verbose("verbose log");
    testAndroidLogger.error("error log");
    testAndroidLogger.warn("warn log");

    verify(mockLogWrapper, never()).d(anyString());
    verify(mockLogWrapper, never()).i(anyString());
    verify(mockLogWrapper, never()).v(anyString());
    verify(mockLogWrapper, never()).e(anyString());
    verify(mockLogWrapper, never()).w(anyString());
  }

  @Test
  public void log_withFormatString_loggedCorrectly() {
    testAndroidLogger.setLogcatEnabled(true);

    String testString = "TestString";
    int testInt = 10;
    float testFloat = 10.00f;

    testAndroidLogger.debug(
        "Debug log with support for 'String: %s', 'int: %d', 'float: %f', etc",
        testString, testInt, testFloat);
    testAndroidLogger.info(
        "Info log with support for 'String: %s', 'int: %d', 'float: %f', etc",
        testString, testInt, testFloat);
    testAndroidLogger.verbose(
        "Verbose log with support for 'String: %s', 'int: %d', 'float: %f', etc",
        testString, testInt, testFloat);
    testAndroidLogger.error(
        "Error log with support for 'String: %s', 'int: %d', 'float: %f', etc",
        testString, testInt, testFloat);
    testAndroidLogger.warn(
        "Warn log with support for 'String: %s', 'int: %d', 'float: %f', etc",
        testString, testInt, testFloat);

    verify(mockLogWrapper, times(1))
        .d(
            String.format(
                Locale.ENGLISH,
                "Debug log with support for 'String: %s', 'int: %d', 'float: %f', etc",
                testString,
                testInt,
                testFloat));
    verify(mockLogWrapper, times(1))
        .i(
            String.format(
                Locale.ENGLISH,
                "Info log with support for 'String: %s', 'int: %d', 'float: %f', etc",
                testString,
                testInt,
                testFloat));
    verify(mockLogWrapper, times(1))
        .v(
            String.format(
                Locale.ENGLISH,
                "Verbose log with support for 'String: %s', 'int: %d', 'float: %f', etc",
                testString,
                testInt,
                testFloat));
    verify(mockLogWrapper, times(1))
        .e(
            String.format(
                Locale.ENGLISH,
                "Error log with support for 'String: %s', 'int: %d', 'float: %f', etc",
                testString,
                testInt,
                testFloat));
    verify(mockLogWrapper, times(1))
        .w(
            String.format(
                Locale.ENGLISH,
                "Warn log with support for 'String: %s', 'int: %d', 'float: %f', etc",
                testString,
                testInt,
                testFloat));
  }

  @Test
  public void log_withPreformattedString_loggedCorrectly() {
    testAndroidLogger.setLogcatEnabled(true);
    testAndroidLogger.debug("Debug log with formatting tokens: %s %d %f");
    testAndroidLogger.info("Info log with formatting tokens: %s %d %f");
    testAndroidLogger.verbose("Verbose log with formatting tokens: %s %d %f");
    testAndroidLogger.error("Error log with formatting tokens: %s %d %f");
    testAndroidLogger.warn("Warn log with formatting tokens: %s %d %f");

    verify(mockLogWrapper, times(1)).d("Debug log with formatting tokens: %s %d %f");
    verify(mockLogWrapper, times(1)).i("Info log with formatting tokens: %s %d %f");
    verify(mockLogWrapper, times(1)).v("Verbose log with formatting tokens: %s %d %f");
    verify(mockLogWrapper, times(1)).e("Error log with formatting tokens: %s %d %f");
    verify(mockLogWrapper, times(1)).w("Warn log with formatting tokens: %s %d %f");
  }
}
