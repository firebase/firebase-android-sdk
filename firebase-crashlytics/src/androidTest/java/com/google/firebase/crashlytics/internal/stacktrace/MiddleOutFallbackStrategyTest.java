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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import org.mockito.InOrder;

public class MiddleOutFallbackStrategyTest extends CrashlyticsTestCase {

  private MiddleOutFallbackStrategy trimmingStrategy;

  public void testStrategyCallsGivenStrategiesInOrderThenMiddleOut() {
    final int expectedTrimmedSize = 10;
    final StackTraceElement[] stacktrace = mockStackTrace(30);

    final StackTraceTrimmingStrategy strategy1 = mock(StackTraceTrimmingStrategy.class);
    doReturn(stacktrace).when(strategy1).getTrimmedStackTrace(stacktrace);
    final StackTraceTrimmingStrategy strategy2 = mock(StackTraceTrimmingStrategy.class);
    doReturn(stacktrace).when(strategy2).getTrimmedStackTrace(stacktrace);
    final StackTraceTrimmingStrategy strategy3 = mock(StackTraceTrimmingStrategy.class);
    doReturn(stacktrace).when(strategy3).getTrimmedStackTrace(stacktrace);

    trimmingStrategy =
        new MiddleOutFallbackStrategy(expectedTrimmedSize, strategy1, strategy2, strategy3);

    final InOrder inOrder = inOrder(strategy1, strategy2, strategy3);

    final StackTraceElement[] trimmed = trimmingStrategy.getTrimmedStackTrace(stacktrace);

    inOrder.verify(strategy1).getTrimmedStackTrace(stacktrace);
    inOrder.verify(strategy2).getTrimmedStackTrace(stacktrace);
    inOrder.verify(strategy3).getTrimmedStackTrace(stacktrace);
    inOrder.verifyNoMoreInteractions();

    assertEquals(expectedTrimmedSize, trimmed.length);
    assertEquals(stacktrace[0], trimmed[0]);
    assertEquals(stacktrace[stacktrace.length - 1], trimmed[trimmed.length - 1]);
  }

  public void testStrategyFallsBackToMiddleOut_ifNoOtherStrategiesGiven() {
    final int expectedTrimmedSize = 10;
    final StackTraceElement[] stacktrace = mockStackTrace(30);

    trimmingStrategy = new MiddleOutFallbackStrategy(expectedTrimmedSize);

    final StackTraceElement[] trimmed = trimmingStrategy.getTrimmedStackTrace(stacktrace);

    assertEquals(expectedTrimmedSize, trimmed.length);
    assertEquals(stacktrace[0], trimmed[0]);
    assertEquals(stacktrace[stacktrace.length - 1], trimmed[trimmed.length - 1]);
  }

  public void testStrategySkipsMiddleOut_ifOtherStrategiesSatisfy() {
    final int expectedTrimmedSize = 10;
    final StackTraceElement[] stacktrace = mockStackTrace(30);
    final StackTraceElement[] expectedTrimmedTrace = mockStackTrace(expectedTrimmedSize);

    final StackTraceTrimmingStrategy strategy = mock(StackTraceTrimmingStrategy.class);
    doReturn(expectedTrimmedTrace).when(strategy).getTrimmedStackTrace(stacktrace);

    trimmingStrategy = new MiddleOutFallbackStrategy(expectedTrimmedSize, strategy);

    final StackTraceElement[] trimmed = trimmingStrategy.getTrimmedStackTrace(stacktrace);

    assertSame(expectedTrimmedTrace, trimmed);
  }

  public void testStrategySkipsTrimming_ifTraceIsSmallEnough() {
    final StackTraceElement[] stacktrace = mockStackTrace(10);

    final StackTraceTrimmingStrategy strategy = mock(StackTraceTrimmingStrategy.class);
    doReturn(stacktrace).when(strategy).getTrimmedStackTrace(stacktrace);

    trimmingStrategy = new MiddleOutFallbackStrategy(30, strategy);

    final StackTraceElement[] trimmed = trimmingStrategy.getTrimmedStackTrace(stacktrace);

    assertSame(stacktrace, trimmed);
  }

  private StackTraceElement[] mockStackTrace(int size) {
    final StackTraceElement[] stacktrace = new StackTraceElement[size];
    for (int i = 0; i < size; ++i) {
      stacktrace[i] = new StackTraceElement("TestClass", "method" + i, "TestClass.java", i);
    }
    return stacktrace;
  }
}
