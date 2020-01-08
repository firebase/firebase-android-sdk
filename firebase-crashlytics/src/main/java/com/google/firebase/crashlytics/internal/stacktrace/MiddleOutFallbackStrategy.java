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

/**
 * Strategy for trimming a given stack trace to a given size by first trying the input strategies,
 * and then using a middle out strategy if that fails to trim the stack below the maximum size.
 */
public class MiddleOutFallbackStrategy implements StackTraceTrimmingStrategy {

  private final int maximumStackSize;
  private final StackTraceTrimmingStrategy[] trimmingStrategies;
  private final MiddleOutStrategy middleOutStrategy;

  public MiddleOutFallbackStrategy(int maximumStackSize, StackTraceTrimmingStrategy... strategies) {
    this.maximumStackSize = maximumStackSize;
    this.trimmingStrategies = strategies;
    this.middleOutStrategy = new MiddleOutStrategy(maximumStackSize);
  }

  @Override
  public StackTraceElement[] getTrimmedStackTrace(StackTraceElement[] stacktrace) {
    if (stacktrace.length <= maximumStackSize) {
      return stacktrace;
    }

    StackTraceElement[] trimmed = stacktrace;
    for (StackTraceTrimmingStrategy strategy : trimmingStrategies) {
      if (trimmed.length <= maximumStackSize) {
        break;
      }

      trimmed = strategy.getTrimmedStackTrace(stacktrace);
    }

    if (trimmed.length > maximumStackSize) {
      trimmed = middleOutStrategy.getTrimmedStackTrace(trimmed);
    }

    return trimmed;
  }
}
