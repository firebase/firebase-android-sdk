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

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.util.UUID;

public class MiddleOutStrategyTest extends CrashlyticsTestCase {

  private MiddleOutStrategy middleOutStrategy;

  public void testStackTraceRetainsTopAndBottom() {
    final int trimmedSize = 10;
    middleOutStrategy = new MiddleOutStrategy(trimmedSize);

    final StackTraceElement[] mockStackTrace = mockStackTrace(30);
    final StackTraceElement[] trimmed = middleOutStrategy.getTrimmedStackTrace(mockStackTrace);

    assertEquals(trimmedSize, trimmed.length);

    assertTrue(rangesMatch(mockStackTrace, 0, trimmed, 0, 5));
    assertTrue(rangesMatch(mockStackTrace, mockStackTrace.length - 5, trimmed, 5, 5));
  }

  public void testStackTraceRetainsExtraFrameOnTop_whenTrimmedSizeIsOdd() {
    final int trimmedSize = 11;
    middleOutStrategy = new MiddleOutStrategy(trimmedSize);

    final StackTraceElement[] mockStackTrace = mockStackTrace(30);
    final StackTraceElement[] trimmed = middleOutStrategy.getTrimmedStackTrace(mockStackTrace);

    assertEquals(trimmedSize, trimmed.length);

    assertTrue(rangesMatch(mockStackTrace, 0, trimmed, 0, 6));
    assertTrue(rangesMatch(mockStackTrace, mockStackTrace.length - 5, trimmed, 6, 5));
  }

  public void testStackTraceIsNotModified_whenSmallEnough() {
    final int trimmedSize = 10;
    middleOutStrategy = new MiddleOutStrategy(trimmedSize);

    final StackTraceElement[] mockStackTrace = mockStackTrace(5);
    final StackTraceElement[] trimmed = middleOutStrategy.getTrimmedStackTrace(mockStackTrace);

    assertEquals(mockStackTrace, trimmed);
  }

  private boolean rangesMatch(
      StackTraceElement[] expected,
      int expectedOffset,
      StackTraceElement[] actual,
      int actualOffset,
      int length) {
    for (int i = 0; i < length; ++i) {
      if (!expected[i + expectedOffset].equals(actual[i + actualOffset])) {
        return false;
      }
    }
    return true;
  }

  private StackTraceElement[] mockStackTrace(int size) {
    final String id = UUID.randomUUID().toString();
    final StackTraceElement[] stacktrace = new StackTraceElement[size];
    for (int i = 0; i < size; ++i) {
      stacktrace[i] =
          new StackTraceElement("TestClass" + id, "method" + id + i, "TestClass" + id + ".java", i);
    }
    return stacktrace;
  }
}
