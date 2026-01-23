/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.log;

import java.io.PrintStream;

/** Logs warnings and errors to STDERR; info, and debug to STDOUT **/
public class ConsoleLogger implements CrashlyticsLogger {

  private Level level;

  public ConsoleLogger() {
    this(Level.INFO);
  }

  public ConsoleLogger(Level level) {
    this.level = level;
  }

  @Override
  public void setLevel(Level level) {
    this.level = level;
  }

  @Override
  public synchronized void logV(String msg) {
    log(Level.VERBOSE, msg, System.out);
  }

  @Override
  public synchronized void logD(String msg) {
    log(Level.DEBUG, msg, System.out);
  }

  @Override
  public synchronized void logI(String msg) {
    log(Level.INFO, msg, System.out);
  }

  @Override
  public synchronized void logW(String msg, Throwable t) {
    log(Level.WARNING, msg, System.err);
    logThrowable(t);
  }

  @Override
  public synchronized void logE(String msg, Throwable t) {
    log(Level.ERROR, msg, System.err);
    logThrowable(t);
  }

  private void log(Level l, String msg, PrintStream stream) {
    if (level.logsFor(l)) {
      stream.println("[CRASHLYTICS LOG " + l.toString() + "] " + msg);
    }
  }

  /**
   * Log the throwable message and stacktrace as DEBUG messages.
   */
  private void logThrowable(Throwable t) {
    if (t != null && level.logsFor(Level.DEBUG)) {
      t.printStackTrace(System.out);
    }
  }
}
