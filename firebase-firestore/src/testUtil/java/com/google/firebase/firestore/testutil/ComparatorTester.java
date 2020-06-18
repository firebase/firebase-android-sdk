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

package com.google.firebase.firestore.testutil;

// Copyright 2009 Google Inc. All Rights Reserved.

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.firebase.firestore.util.Preconditions;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Tests that a given {@link Comparator} (or the implementation of {@link Comparable}) is correct.
 * To use, repeatedly call {@link #addEqualityGroup(Object...)} with sets of objects that should be
 * equal. The calls to {@link #addEqualityGroup(Object...)} must be made in sorted order. Then call
 * {@link #testCompare()} to test the comparison. For example:
 *
 * <pre>{@code
 * new ComparatorTester()
 *     .addEqualityGroup(1)
 *     .addEqualityGroup(2)
 *     .addEqualityGroup(3)
 *     .testCompare();
 * }</pre>
 *
 * <p>By default, a {@code Comparator} is not tested for compatibility with {@link
 * Object#equals(Object)}. If that is desired, use the {link #requireConsistencyWithEquals()} to
 * explicitly activate the check. For example:
 *
 * <pre>{@code
 * new ComparatorTester(Comparator.naturalOrder())
 *     .requireConsistencyWithEquals()
 *     .addEqualityGroup(1)
 *     .addEqualityGroup(2)
 *     .addEqualityGroup(3)
 *     .testCompare();
 * }</pre>
 *
 * <p>If for some reason you need to suppress the compatibility check when testing a {@code
 * Comparable}, use the {link #permitInconsistencyWithEquals()} to explicitly deactivate the check.
 * For example:
 *
 * <pre>{@code
 * new ComparatorTester()
 *     .permitInconsistencyWithEquals()
 *     .addEqualityGroup(1)
 *     .addEqualityGroup(2)
 *     .addEqualityGroup(3)
 *     .testCompare();
 * }</pre>
 *
 * @author bmaurer@google.com (Ben Maurer)
 */
public class ComparatorTester {
  @SuppressWarnings({"rawtypes"})
  private final @Nullable Comparator comparator;

  /** The items that we are checking, stored as a sorted set of equivalence classes. */
  private final List<List<Object>> equalityGroups;

  /** Whether to enforce a.equals(b) == (a.compareTo(b) == 0) */
  private boolean testForEqualsCompatibility;

  /**
   * Creates a new instance that tests the order of objects using the natural order (as defined by
   * {@link Comparable}).
   */
  public ComparatorTester() {
    this(null);
  }

  /**
   * Creates a new instance that tests the order of objects using the given comparator. Or, if the
   * comparator is {@code null}, the natural ordering (as defined by {@link Comparable})
   */
  public ComparatorTester(@Nullable Comparator<?> comparator) {
    this.equalityGroups = Lists.newArrayList();
    this.comparator = comparator;
    this.testForEqualsCompatibility = (this.comparator == null);
  }

  /**
   * Activates enforcement of {@code a.equals(b) == (a.compareTo(b) == 0)}. This is off by default
   * when testing {@link Comparator}s, but can be turned on if required.
   */
  public ComparatorTester requireConsistencyWithEquals() {
    testForEqualsCompatibility = true;
    return this;
  }

  /**
   * Deactivates enforcement of {@code a.equals(b) == (a.compareTo(b) == 0)}. This is on by default
   * when testing {@link Comparable}s, but can be turned off if required.
   */
  public ComparatorTester permitInconsistencyWithEquals() {
    testForEqualsCompatibility = false;
    return this;
  }

  /**
   * Adds a set of objects to the test which should all compare as equal. All of the elements in
   * {@code objects} must be greater than any element of {@code objects} in a previous call to
   * {@link #addEqualityGroup(Object...)}.
   *
   * @return {@code this} (to allow chaining of calls)
   */
  public ComparatorTester addEqualityGroup(Object... objects) {
    Preconditions.checkNotNull(objects);
    Preconditions.checkArgument(objects.length > 0, "Array must not be empty");
    equalityGroups.add(ImmutableList.copyOf(objects));
    return this;
  }

  @SuppressWarnings({"unchecked"})
  private int compare(Object a, Object b) {
    int compareValue;
    if (comparator == null) {
      compareValue = ((Comparable<Object>) a).compareTo(b);
    } else {
      compareValue = comparator.compare(a, b);
    }
    return compareValue;
  }

  public final void testCompare() {
    doTestEquivalanceGroupOrdering();
    if (testForEqualsCompatibility) {
      doTestEqualsCompatibility();
    }
  }

  private void doTestEquivalanceGroupOrdering() {
    for (int referenceIndex = 0; referenceIndex < equalityGroups.size(); referenceIndex++) {
      for (Object reference : equalityGroups.get(referenceIndex)) {
        testNullCompare(reference);
        testClassCast(reference);
        for (int otherIndex = 0; otherIndex < equalityGroups.size(); otherIndex++) {
          for (Object other : equalityGroups.get(otherIndex)) {
            assertWithMessage("compare(%s, %s)", reference, other)
                .that(Integer.signum(compare(reference, other)))
                .isEqualTo(Integer.signum(Ints.compare(referenceIndex, otherIndex)));
          }
        }
      }
    }
  }

  private void doTestEqualsCompatibility() {
    for (List<Object> referenceGroup : equalityGroups) {
      for (Object reference : referenceGroup) {
        for (List<Object> otherGroup : equalityGroups) {
          for (Object other : otherGroup) {
            assertWithMessage(
                    "Testing equals() for compatibility with compare()/compareTo(), "
                        + "add a call to doNotRequireEqualsCompatibility() if this is not required")
                .withMessage("%s.equals(%s)", reference, other)
                .that(reference.equals(other))
                .isEqualTo(compare(reference, other) == 0);
          }
        }
      }
    }
  }

  private void testNullCompare(Object obj) {
    // Comparator does not require any specific behavior for null.
    if (comparator == null) {
      try {
        compare(obj, null);
        assert_().fail("Expected NullPointerException in %s.compare(null)", obj);
      } catch (NullPointerException expected) {
        // TODO(cpovirk): Consider accepting JavaScriptException under GWT
      }
    }
  }

  private void testClassCast(Object obj) {
    if (comparator == null) {
      try {
        compare(obj, ICanNotBeCompared.INSTANCE);
        assert_().fail("Expected ClassCastException in %s.compareTo(otherObject)", obj);
      } catch (ClassCastException expected) {
      }
    }
  }

  private static final class ICanNotBeCompared {
    static final ComparatorTester.ICanNotBeCompared INSTANCE = new ICanNotBeCompared();
  }
}
