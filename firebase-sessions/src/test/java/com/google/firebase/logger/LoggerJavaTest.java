// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.logger;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.logger.Logger.FakeLogger;
import com.google.firebase.logger.Logger.Level;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoggerJavaTest {
  private static final String TAG = "tag";

  private FakeLogger fakeLogger;

  @Before
  public void setUp() {
    fakeLogger = Logger.setupFakeLogger(TAG);
  }

  @Test
  public void logMessage() {
    Logger.getLogger(TAG).info("this is a log");

    assertThat(fakeLogger.hasLogMessage("this is a log")).isTrue();
  }

  @Test
  public void logMessage_setMinDebug() {
    Logger.getLogger(TAG).setMinLevel(Level.DEBUG);

    Logger.getLogger(TAG).debug("these bugs need debugging");

    assertThat(fakeLogger.hasLogMessage("these bugs need debugging")).isTrue();
  }

  @Test
  public void logMessage_setMinVerbose_logsVerboseAndDebug() {
    Logger.getLogger(TAG).setMinLevel(Level.VERBOSE);

    Logger.getLogger(TAG).verbose("this is verbose");
    Logger.getLogger(TAG).debug("this is debug");

    assertThat(fakeLogger.hasLogMessage("this is verbose")).isTrue();
    assertThat(fakeLogger.hasLogMessage("this is debug")).isTrue();
  }

  @Test
  public void logMessage_infoLogsByDefault() {
    // Debug is lower than info, so this will not log.
    Logger.getLogger(TAG).debug("this is a debug log");

    assertThat(fakeLogger.hasLogMessage("this is a debug log")).isFalse();

    // The default min log level is info, so this will log.
    Logger.getLogger(TAG).info("this is an info log");

    assertThat(fakeLogger.hasLogMessage("this is an info log")).isTrue();
  }

  @Test
  public void logMessage_withFormat() {
    Logger.getLogger(TAG).warn("%s %s %d!", "Hello", "World", 2023);

    assertThat(fakeLogger.hasLogMessage("WARN Hello World 2023!")).isTrue();
  }

  @Test
  public void logMessage_withThrowable() {
    Logger.getLogger(TAG).error("Hello!", new IOException("Hi!"));

    assertThat(fakeLogger.hasLogMessage("ERROR Hello! java.io.IOException: Hi!")).isTrue();
  }

  @Test
  public void logMessage_withFormat_withThrowable() {
    // Since Java doesn't have named args, it can't have the throwable parameter after varargs.
    Logger.getLogger(TAG)
        .info("%s %s %d!", new Object[] {"Hello", "World", 2023}, new IOException("Hi"));

    assertThat(fakeLogger.hasLogMessage("INFO Hello World 2023! java.io.IOException: Hi")).isTrue();
  }

  @Test
  public void logMessage_setMinLevel_withFormat_withThrowable() {
    Logger.getLogger(TAG).setMinLevel(Level.VERBOSE);

    // Since Java doesn't have named args, it can't have the throwable parameter after varargs.
    Logger.getLogger(TAG)
        .verbose("%s, %s!", new Object[] {"Hello", "World"}, new IOException("Hi!"));

    assertThat(fakeLogger.hasLogMessage("VERBOSE Hello, World! java.io.IOException: Hi!")).isTrue();
  }

  @Test
  public void logMessage_setMinLevelTooHigh_logTooLow() {
    Logger.getLogger(TAG).setMinLevel(Level.ERROR);

    Logger.getLogger(TAG).warn("Warning!");

    assertThat(fakeLogger.hasLogMessage("Warning")).isFalse();
  }

  @Test
  public void logMessage_setsEnabledFalse_doesNotLog() {
    Logger.getLogger(TAG).setEnabled(false);

    Logger.getLogger(TAG).info("log");

    assertThat(fakeLogger.hasLogMessage("log")).isFalse();
  }

  @Test
  public void getLogger_sameTag_sameInstance() {
    Logger logger1 = Logger.getLogger(TAG);
    Logger logger2 = Logger.getLogger(TAG);

    assertThat(logger1).isSameInstanceAs(logger2);
    // Same as fake logger too since we use the same tag in setUp.
    assertThat(logger1).isSameInstanceAs(fakeLogger);
  }

  @Test
  public void getLogger_diffTags_diffInstances() {
    Logger logger1 = Logger.getLogger("tag1");
    Logger logger2 = Logger.getLogger("tag2");

    assertThat(logger1).isNotSameInstanceAs(logger2);
  }

  @Test
  public void hasLogMessage_substringMatches() {
    Logger.getLogger(TAG).info("I never said I have a cat, I only have dogs.");

    assertThat(fakeLogger.hasLogMessage("I have a cat")).isTrue();
  }

  @Test
  public void hasLogMessage_nonMatch_returnsFalse() {
    Logger.getLogger(TAG).info("This log says nothing useful.");

    assertThat(fakeLogger.hasLogMessage("A meaningful log message")).isFalse();
  }

  @Test
  public void hasLogMessageThat_predicateMatches() {
    Logger.getLogger(TAG).warn("This is a log");

    assertThat(fakeLogger.hasLogMessageThat(logMessage -> logMessage.endsWith("log"))).isTrue();
  }

  @Test
  public void hasLogMessageThat_predicateFails_returnsFalse() {
    Logger.getLogger(TAG).warn("something");

    assertThat(fakeLogger.hasLogMessageThat(String::isEmpty)).isFalse();
  }
}
