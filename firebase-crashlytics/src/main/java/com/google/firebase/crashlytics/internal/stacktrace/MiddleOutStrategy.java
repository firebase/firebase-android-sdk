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
 * Strategy for trimming a given stack trace by removing the middle portion.
 *
 * <p>The most important details in a stack are the beginning and end - i.e. the point at which it
 * was generated and where it originated. This strategy takes advantage of this notion and always
 * generates a trimmed stack of a given size by removing the middle of the stack, returning a
 * trimmed stack with half of the trimmed size from the beginning of the input stack, and half from
 * the end.
 */
public class MiddleOutStrategy implements StackTraceTrimmingStrategy {
  private final int trimmedSize;

  public MiddleOutStrategy(int trimmedSize) {
    this.trimmedSize = trimmedSize;
  }

  @Override
  public StackTraceElement[] getTrimmedStackTrace(StackTraceElement[] stacktrace) {
    if (stacktrace.length <= trimmedSize) {
      return stacktrace;
    }

    // If trimmedSize is odd, add the extra frame on the front section.
    final int backHalf = trimmedSize / 2;
    final int frontHalf = trimmedSize - backHalf;

    final StackTraceElement[] trimmed = new StackTraceElement[trimmedSize];
    System.arraycopy(stacktrace, 0, trimmed, 0, frontHalf);
    System.arraycopy(stacktrace, stacktrace.length - backHalf, trimmed, frontHalf, backHalf);
    return trimmed;
  }
}
