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
import static org.mockito.Mockito.mock;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.util.Arrays;
import java.util.UUID;

public class TrimmedThrowableDataTest extends CrashlyticsTestCase {

  private static class TruncateStrategy implements StackTraceTrimmingStrategy {
    private final int size;

    public TruncateStrategy(int size) {
      this.size = size;
    }

    @Override
    public StackTraceElement[] getTrimmedStackTrace(StackTraceElement[] stacktrace) {
      final int trimmedSize = Math.min(size, stacktrace.length);
      final StackTraceElement[] trimmed = new StackTraceElement[trimmedSize];
      System.arraycopy(stacktrace, 0, trimmed, 0, trimmedSize);
      return trimmed;
    }
  }

  private Exception mockException;
  private Exception mockCause;

  public void setUp() throws Exception {
    mockException = mock(Exception.class);
    mockCause = mock(Exception.class);
    doReturn("exception").when(mockException).getLocalizedMessage();
    doReturn("cause").when(mockCause).getLocalizedMessage();
  }

  public void testStackTraceIsTrimmed() {
    doReturn(mockStackTrace(3)).when(mockException).getStackTrace();

    final StackTraceTrimmingStrategy trimmingStrategy = new TruncateStrategy(1);
    final TrimmedThrowableData t = new TrimmedThrowableData(mockException, trimmingStrategy);

    assertEquals(1, t.stacktrace.length);
    assertEquals(mockException.getStackTrace()[0], t.stacktrace[0]);
  }

  public void testCauseStackTraceIsTrimmed() {
    doReturn(mockStackTrace(3)).when(mockException).getStackTrace();
    doReturn(mockStackTrace(3)).when(mockCause).getStackTrace();
    doReturn(mockCause).when(mockException).getCause();

    final StackTraceTrimmingStrategy trimmingStrategy = new TruncateStrategy(1);
    final TrimmedThrowableData t = new TrimmedThrowableData(mockException, trimmingStrategy);

    assertEquals(1, t.stacktrace.length);
    assertEquals(mockException.getStackTrace()[0], t.stacktrace[0]);

    assertEquals(1, t.cause.stacktrace.length);
    assertEquals(mockException.getCause().getStackTrace()[0], t.cause.stacktrace[0]);
  }

  public void testStackTraceIsNotModifiedIfSmallEnough() {
    doReturn(mockStackTrace(3)).when(mockException).getStackTrace();

    final StackTraceTrimmingStrategy trimmingStrategy = new TruncateStrategy(5);
    final TrimmedThrowableData t = new TrimmedThrowableData(mockException, trimmingStrategy);

    assertEquals(3, t.stacktrace.length);
    assertTrue(Arrays.equals(mockException.getStackTrace(), t.stacktrace));
  }

  public void testCauseStackTraceIsNotModifiedIfSmallEnough() {
    doReturn(mockStackTrace(3)).when(mockException).getStackTrace();
    doReturn(mockStackTrace(3)).when(mockCause).getStackTrace();
    doReturn(mockCause).when(mockException).getCause();

    final StackTraceTrimmingStrategy trimmingStrategy = new TruncateStrategy(5);
    final TrimmedThrowableData t = new TrimmedThrowableData(mockException, trimmingStrategy);

    assertEquals(3, t.stacktrace.length);
    assertTrue(Arrays.equals(mockException.getStackTrace(), t.stacktrace));

    assertEquals(3, t.cause.stacktrace.length);
    assertTrue(Arrays.equals(mockException.getCause().getStackTrace(), t.cause.stacktrace));
  }

  public void testOnlyCauseStackTraceIsTrimmed() {
    doReturn(mockStackTrace(3)).when(mockException).getStackTrace();
    doReturn(mockStackTrace(5)).when(mockCause).getStackTrace();
    doReturn(mockCause).when(mockException).getCause();

    final StackTraceTrimmingStrategy trimmingStrategy = new TruncateStrategy(4);
    final TrimmedThrowableData t = new TrimmedThrowableData(mockException, trimmingStrategy);

    assertEquals(3, t.stacktrace.length);
    assertTrue(Arrays.equals(mockException.getStackTrace(), t.stacktrace));

    assertEquals(4, t.cause.stacktrace.length);
    assertFalse(Arrays.equals(mockException.getCause().getStackTrace(), t.cause.stacktrace));
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
