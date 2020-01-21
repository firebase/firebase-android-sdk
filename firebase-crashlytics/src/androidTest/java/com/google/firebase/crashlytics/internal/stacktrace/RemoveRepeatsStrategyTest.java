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
import java.util.Arrays;

public class RemoveRepeatsStrategyTest extends CrashlyticsTestCase {

  private RemoveRepeatsStrategy removeRepeatsStrategy;

  public void testRepeatsAreRemoved() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 5};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testAllRepeatsAreRetained_ifLessThanMaxRepeats() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy(5);

    final int[] values = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 5};

    final StackTraceElement[] fullStack = mockStackTrace(values);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(fullStack, trimmedStack));
  }

  public void testRepeatsAreRetained_upToMaxRepeats() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy(3);

    final int[] values = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 3, 4, 3, 4, 5};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 5};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_repeatsInFront() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 1, 1, 2, 3, 4, 5};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_repeatsInBack() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 5, 6, 5, 6, 5, 6, 5, 6, 5, 6};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5, 6};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_backSmallerThanLoopSize() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 5, 6, 7, 5, 6, 7, 5, 6, 7, 5, 6, 7, 5, 6};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5, 6, 7, 5, 6};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_backSameAsLoopSize() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 4, 5};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_backLargerThanLoopSize() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 4, 5, 6, 7};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5, 6, 7};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testRepeatsAreRemoved_multiplePossibleRepeats() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 2, 3, 2, 3, 2, 3, 4};
    final int[] expectedValues = new int[] {1, 2, 3, 4};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testStackIsNotModified_ifNoRepeatsAreFound() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 5, 6};

    final StackTraceElement[] fullStack = mockStackTrace(values);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(fullStack, trimmedStack));
  }

  public void testStackIsNotModified_ifRepeatsAreNotContiguous() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 1, 2, 4, 1, 2, 5, 6};

    final StackTraceElement[] fullStack = mockStackTrace(values);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(fullStack, trimmedStack));
  }

  public void testMultipleSetsOfRepeatsAreRemoved() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 2, 3, 2, 3, 3, 4, 3, 4};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 2, 3, 3, 4};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testMultipleSetsOfRepeatsAreRemoved_ifRepeatsAreDifferentSizes() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 3, 4, 3, 4, 2, 2, 2, 3, 4, 5, 3, 4, 5, 6};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 2, 3, 4, 5, 6};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testLargeRepeatIsRemoved() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8};
    final int[] expectedValues = new int[] {1, 2, 3, 4, 5, 6, 7, 8};

    final StackTraceElement[] fullStack = mockStackTrace(values);
    final StackTraceElement[] expectedStack = mockStackTrace(expectedValues);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(expectedStack, trimmedStack));
  }

  public void testLargeRepeatIsNotRemoved_ifNotContiguous() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    final StackTraceElement[] fullStack = mockStackTrace(values);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(fullStack, trimmedStack));
  }

  public void testStackIsNotModified_ifStackHasRepetitionButNoLoops() {
    removeRepeatsStrategy = new RemoveRepeatsStrategy();

    final int[] values = new int[] {1, 2, 3, 4, 1, 3, 2, 4, 3, 2, 1, 4, 3, 2, 3, 4};

    final StackTraceElement[] fullStack = mockStackTrace(values);

    final StackTraceElement[] trimmedStack = removeRepeatsStrategy.getTrimmedStackTrace(fullStack);

    assertTrue(
        "Did not find expected stack, instead: " + Arrays.toString(trimmedStack),
        Arrays.equals(fullStack, trimmedStack));
  }

  private StackTraceElement[] mockStackTrace(int[] values) {
    final StackTraceElement[] stacktrace = new StackTraceElement[values.length];
    for (int i = 0; i < stacktrace.length; ++i) {
      stacktrace[i] =
          new StackTraceElement("TestClass", "method" + values[i], "TestClass.java", values[i]);
    }
    return stacktrace;
  }
}
