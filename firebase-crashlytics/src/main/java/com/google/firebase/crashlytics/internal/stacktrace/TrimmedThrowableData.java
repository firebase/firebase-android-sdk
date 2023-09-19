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

package com.google.firebase.crashlytics.internal.stacktrace;

import androidx.annotation.Nullable;
import java.util.Stack;

/**
 * Decorator class that exposes the appropriate APIs for Crashlytics to write crash data, but which
 * pre-processes the stack trace to remove unnecessary frames based on the passed-in
 * StackTraceTrimmingStrategy to make crash reporting more efficient.
 */
public class TrimmedThrowableData {
  public final String localizedMessage;
  public final String className;
  public final StackTraceElement[] stacktrace;
  @Nullable public final TrimmedThrowableData cause;

  private TrimmedThrowableData(
      String localizedMessage,
      String className,
      StackTraceElement[] stacktrace,
      @Nullable TrimmedThrowableData cause) {
    this.localizedMessage = localizedMessage;
    this.className = className;
    this.stacktrace = stacktrace;
    this.cause = cause;
  }

  public static TrimmedThrowableData makeTrimmedThrowableData(
      Throwable ex, StackTraceTrimmingStrategy trimmingStrategy) {
    Stack<Throwable> throwableStack = new Stack<>();
    Throwable exCause = ex;
    while (exCause != null) {
      throwableStack.push(exCause);
      exCause = exCause.getCause();
    }

    TrimmedThrowableData trimmedThrowableData = null;
    while (!throwableStack.isEmpty()) {
      Throwable throwable = throwableStack.pop();
      trimmedThrowableData =
          new TrimmedThrowableData(
              throwable.getLocalizedMessage(),
              throwable.getClass().getName(),
              trimmingStrategy.getTrimmedStackTrace(throwable.getStackTrace()),
              trimmedThrowableData);
    }

    return trimmedThrowableData;
  }
}
