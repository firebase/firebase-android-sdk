// Copyright 2018 Google LLC
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

package com.google.firebase.database.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogWrapper {

  private static String exceptionStacktrace(Throwable e) {
    StringWriter writer = new StringWriter();
    PrintWriter printWriter = new PrintWriter(writer);
    e.printStackTrace(printWriter);
    return writer.toString();
  }

  private final Logger logger;
  private final String component;
  private final String prefix;

  public LogWrapper(Logger logger, String component) {
    this(logger, component, null);
  }

  public LogWrapper(Logger logger, String component, String prefix) {
    this.logger = logger;
    this.component = component;
    this.prefix = prefix;
  }

  public void error(String message, Throwable e) {
    String logMsg = toLog(message) + "\n" + exceptionStacktrace(e);
    logger.onLogMessage(Logger.Level.ERROR, component, logMsg, now());
  }

  public void warn(String message) {
    warn(message, null);
  }

  public void warn(String message, Throwable e) {
    String logMsg = toLog(message);
    if (e != null) {
      logMsg = logMsg + "\n" + exceptionStacktrace(e);
    }
    logger.onLogMessage(Logger.Level.WARN, component, logMsg, now());
  }

  public void info(String message) {
    logger.onLogMessage(Logger.Level.INFO, component, toLog(message), now());
  }

  public void debug(String message, Object... args) {
    this.debug(message, null, args);
  }

  public boolean logsDebug() {
    return this.logger.getLogLevel().ordinal() <= Logger.Level.DEBUG.ordinal();
  }

  /** Log a non-fatal exception. Typically something like an IO error on a failed connection */
  public void debug(String message, Throwable e, Object... args) {
    if (this.logsDebug()) {
      String logMsg = toLog(message, args);
      if (e != null) {
        logMsg = logMsg + "\n" + exceptionStacktrace(e);
      }
      logger.onLogMessage(Logger.Level.DEBUG, component, logMsg, now());
    }
  }

  private long now() {
    return System.currentTimeMillis();
  }

  private String toLog(String message, Object... args) {
    String formatted = (args.length > 0) ? String.format(message, args) : message;
    return prefix == null ? formatted : prefix + " - " + formatted;
  }
}
