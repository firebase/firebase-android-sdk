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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.Util;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * BasePath represents a path sequence in the Firestore database. It is composed of an ordered
 * sequence of string segments.
 */
public abstract class BasePath<B extends BasePath<B>> implements Comparable<B> {
  final List<String> segments;

  BasePath(List<String> segments) {
    this.segments = segments;
  }

  public String getSegment(int index) {
    return segments.get(index);
  }

  /**
   * Returns a new path whose segments are the current path plus the passed in path
   *
   * @param segment the segment to add.
   * @return A new path with this path's segment plus the new one.
   */
  public B append(String segment) {
    List<String> newPath = new ArrayList<>(segments);
    newPath.add(segment);
    return createPathWithSegments(newPath);
  }

  /**
   * Returns a new path whose segments are the current path plus another's
   *
   * @param path the path whose segments to concatenate to the current path.
   * @return A new path with this segments path plus the new one
   */
  public B append(B path) {
    List<String> newPath = new ArrayList<>(segments);
    newPath.addAll(path.segments);
    return createPathWithSegments(newPath);
  }

  /** @return Returns a new path with the current path's first segment removed. */
  public B popFirst() {
    return popFirst(1);
  }

  /** Returns a new path with the current path's first {@code count} segments removed. */
  public B popFirst(int count) {
    int length = length();
    hardAssert(
        length >= count, "Can't call popFirst with count > length() (%d > %d)", count, length);
    return createPathWithSegments(segments.subList(count, length));
  }

  /** @return Returns a new path with the current path's last segment removed. */
  public B popLast() {
    return createPathWithSegments(segments.subList(0, length() - 1));
  }

  /** @return Returns a new path made up of the first count segments of the current path. */
  public B keepFirst(int count) {
    return createPathWithSegments(segments.subList(0, count));
  }

  /**
   * Compare the current path against another Path object. Paths are compared segment by segment,
   * prioritizing numeric IDs (e.g., "__id123__") in numeric ascending order, followed by string
   * segments in lexicographical order.
   */
  @Override
  public int compareTo(@NonNull B o) {
    if (o == this) {
      return 0;
    }

    NumericIdBySegmentIndexInfo myNumericIdBySegmentIndexInfo =
        getOrCreateNumericIdBySegmentIndexInfo();
    NumericIdBySegmentIndexInfo theirNumericIdBySegmentIndexInfo =
        o.getOrCreateNumericIdBySegmentIndexInfo();

    int myLength = length();
    int theirLength = o.length();
    for (int i = 0; i < myLength && i < theirLength; i++) {
      boolean myIsNumericId = myNumericIdBySegmentIndexInfo.isBySegmentIndex(i);
      boolean theirIsNumericId = theirNumericIdBySegmentIndexInfo.isBySegmentIndex(i);
      if (myIsNumericId != theirIsNumericId) {
        return myIsNumericId ? -1 : 1;
      }

      if (myIsNumericId) {
        long myNumericId = myNumericIdBySegmentIndexInfo.valueBySegmentIndex(i);
        long theirNumericId = theirNumericIdBySegmentIndexInfo.valueBySegmentIndex(i);
        int numericIdCompareResult = Long.compare(myNumericId, theirNumericId);
        if (numericIdCompareResult != 0) {
          return numericIdCompareResult;
        }
      } else {
        int utf8CompareResult = Util.compareUtf8Strings(getSegment(i), o.getSegment(i));
        if (utf8CompareResult != 0) {
          return utf8CompareResult;
        }
      }
    }
    return Integer.compare(myLength, theirLength);
  }

  @Nullable private volatile NumericIdBySegmentIndexInfo numericIdBySegmentIndexInfo;

  NumericIdBySegmentIndexInfo getOrCreateNumericIdBySegmentIndexInfo() {
    if (numericIdBySegmentIndexInfo != null) {
      return numericIdBySegmentIndexInfo;
    }

    BitSet isBySegmentIndex = null;
    long[] valueBySegmentIndex = null;

    for (int i = segments.size() - 1; i >= 0; --i) {
      String segment = segments.get(i);
      if (!isNumericId(segment)) {
        continue;
      }
      if (isBySegmentIndex == null) {
        isBySegmentIndex = new BitSet(segments.size());
        valueBySegmentIndex = new long[segments.size()];
      }
      isBySegmentIndex.set(i);
      valueBySegmentIndex[i] = extractNumericId(segment);
    }

    numericIdBySegmentIndexInfo =
        new NumericIdBySegmentIndexInfo(isBySegmentIndex, valueBySegmentIndex);
    return numericIdBySegmentIndexInfo;
  }

  private static final class NumericIdBySegmentIndexInfo {
    private final BitSet isBySegmentIndex;
    private long[] valueBySegmentIndex;

    NumericIdBySegmentIndexInfo(BitSet isBySegmentIndex, long[] valueBySegmentIndex) {
      this.isBySegmentIndex = isBySegmentIndex;
      this.valueBySegmentIndex = valueBySegmentIndex;
    }

    boolean isBySegmentIndex(int index) {
      return isBySegmentIndex != null && isBySegmentIndex.get(index);
    }

    long valueBySegmentIndex(int index) {
      return valueBySegmentIndex[index];
    }
  }

  /** Checks if a segment is a numeric ID (starts with "__id" and ends with "__"). */
  private static boolean isNumericId(String segment) {
    return segment.startsWith("__id") && segment.endsWith("__");
  }

  private static long extractNumericId(String segment) {
    return Long.parseLong(segment, 4, segment.length() - 2, 10);
  }

  /** @return Returns the last segment of the path */
  public String getLastSegment() {
    return segments.get(length() - 1);
  }

  /** @return Returns the first segment of the path */
  public String getFirstSegment() {
    return segments.get(0);
  }

  public boolean isEmpty() {
    return length() == 0;
  }

  /**
   * Checks to see if this path is a prefix of (or equals) another path.
   *
   * @param path the path to check against
   * @return true if current path is a prefix of the other path.
   */
  public boolean isPrefixOf(B path) {
    if (length() > path.length()) {
      return false;
    }
    for (int i = 0; i < length(); i++) {
      if (!getSegment(i).equals(path.getSegment(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the given argument is a direct child of this path.
   *
   * <p>Empty path is a parent of any path that consists of a single segment.
   */
  public boolean isImmediateParentOf(B potentialChild) {
    if (length() + 1 != potentialChild.length()) {
      return false;
    }
    for (int i = 0; i < length(); i++) {
      if (!getSegment(i).equals(potentialChild.getSegment(i))) {
        return false;
      }
    }
    return true;
  }

  public abstract String canonicalString();

  @Override
  public String toString() {
    return canonicalString();
  }

  abstract B createPathWithSegments(List<String> segments);

  public int length() {
    return segments.size();
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return (o instanceof BasePath) && compareTo((B) o) == 0;
  }

  private volatile boolean memoizedHashCodeValid;
  private volatile int memoizedHashCode;

  @Override
  public int hashCode() {
    if (memoizedHashCodeValid) {
      return memoizedHashCode;
    }

    memoizedHashCode = Objects.hash(getClass(), segments);
    memoizedHashCodeValid = true;

    return memoizedHashCode;
  }
}
