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

import java.util.HashMap;
import java.util.Map;

/** Strategy for trimming a given stack trace by removing repeated sets of frames. */
public class RemoveRepeatsStrategy implements StackTraceTrimmingStrategy {

  private final int maxRepetitions;

  public RemoveRepeatsStrategy() {
    this(1);
  }

  /**
   * Constructs a RemoveRepeatsStrategy which will retain up to the given maximum number of sequence
   * repetitions for each repetitive sequence found.
   *
   * @param maxRepetitions the number of repetitions that should be left in the trimmed stack trace
   *     before the rest are removed.
   */
  public RemoveRepeatsStrategy(int maxRepetitions) {
    this.maxRepetitions = maxRepetitions;
  }

  @Override
  public StackTraceElement[] getTrimmedStackTrace(StackTraceElement[] stacktrace) {
    final StackTraceElement[] trimmed = trimRepeats(stacktrace, maxRepetitions);
    if (trimmed.length < stacktrace.length) {
      return trimmed;
    }
    return stacktrace;
  }

  /**
   * Runs a single pass over the input stacktrace, trimming repeated sequences.
   *
   * @param stacktrace the stack trace for which to remove repeated sequences of frames
   * @param maxRepetitions the maximum number of allowed repetitions before discarding. This is
   *     useful to show that repetition is happening, but put a cap on the number of times it's
   *     shown.
   * @return an array of stack frames with any repeated sequences trimmed.
   */
  private static StackTraceElement[] trimRepeats(
      StackTraceElement[] stacktrace, int maxRepetitions) {

    final Map<StackTraceElement, Integer> mostRecentIndices = new HashMap<>();

    // Worse case scenario, no trimming happens and we end up with a copy of the original.
    final StackTraceElement[] buffer = new StackTraceElement[stacktrace.length];

    int trimmedLength = 0;
    int numRepeats = 1;
    for (int i = 0; i < stacktrace.length; ++i) {
      final int currentIndex = i;
      final StackTraceElement currentFrame = stacktrace[i];
      final Integer previousIndex = mostRecentIndices.get(currentFrame);
      if (previousIndex == null || !isRepeatingSequence(stacktrace, previousIndex, i)) {
        // No repeat detected. Keep moving.
        numRepeats = 1;
        buffer[trimmedLength] = stacktrace[i];
        trimmedLength++;
      } else {
        // Repeat detected. Skip it unless we allow more than one repetition.
        final int windowSize = i - previousIndex;
        if (numRepeats < maxRepetitions) {
          System.arraycopy(stacktrace, i, buffer, trimmedLength, windowSize);
          trimmedLength += windowSize;
          numRepeats++;
        }
        i += windowSize - 1;
      }
      mostRecentIndices.put(currentFrame, currentIndex);
    }

    // One last array copy for the final trimmed sizing.
    final StackTraceElement[] trimmed = new StackTraceElement[trimmedLength];
    System.arraycopy(buffer, 0, trimmed, 0, trimmed.length);
    return trimmed;
  }

  private static boolean isRepeatingSequence(
      StackTraceElement[] stacktrace, int prevIndex, int currentIndex) {
    final int windowSize = currentIndex - prevIndex;

    if (currentIndex + windowSize > stacktrace.length) {
      return false;
    }

    for (int i = 0; i < windowSize; ++i) {
      if (!stacktrace[prevIndex + i].equals(stacktrace[currentIndex + i])) {
        return false;
      }
    }
    return true;
  }
}
