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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BasePath represents a path sequence in the Firestore database. It is composed of an ordered
 * sequence of string segments.
 */
public abstract class BasePath<B extends BasePath<B>> implements Comparable<B> {
  final List<String> segments;

  @Nullable
  private volatile String encodedCanonicalString;

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
   * Compare the current path against another Path object.
   *
   * <p>The comparison is performed by considering each path as a sequence of segments. Each
   * segment is encoded into a string, and the resulting strings are concatenated. The paths are
   * then compared by lexicographically comparing their encoded representations.
   *
   * <p>The encoding is designed to preserve the ordering of path segments. Numeric IDs are encoded
   * in a way that they are sorted numerically. String segments are encoded lexicographically.
   * Numeric IDs are sorted before string segments.
   *
   * <p>The encoded representation is cached in a volatile instance variable to avoid re-computing
   * it on subsequent comparisons.
   */
  @Override
  public int compareTo(@NonNull B o) {
    return getEncodedCanonicalString().compareTo(o.getEncodedCanonicalString());
  }

  protected String getEncodedCanonicalString() {
    String encodedCanonicalString = this.encodedCanonicalString;
    if (encodedCanonicalString == null) {
      encodedCanonicalString = computeEncodedCanonicalString();
      this.encodedCanonicalString = encodedCanonicalString;
    }
    return encodedCanonicalString;
  }

  private String computeEncodedCanonicalString() {
    StringBuilder builder = new StringBuilder();
    for (String segment : segments) {
      if (isNumericId(segment)) {
        builder.append('\1'); // Type: numeric
        long numericValue = extractNumericId(segment);
        encodeSignedLong(numericValue, builder);
      } else {
        builder.append('\2'); // Type: string
        encodeString(segment, builder);
      }
      builder.append('\0').append('\0'); // Separator
    }
    return builder.toString();
  }

  private static void encodeString(String s, StringBuilder builder) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\0') {
        builder.append('\0').append((char) 255);
      } else {
        builder.append(c);
      }
    }
  }

  private static void encodeSignedLong(long value, StringBuilder builder) {
    // This mapping preserves the ordering of signed longs when compared as unsigned.
    long u = value ^ 0x8000000000000000L;

    int numBytes = (Long.SIZE - Long.numberOfLeadingZeros(u) + 7) / 8;
    builder.append((char) numBytes);

    for (int i = numBytes - 1; i >= 0; i--) {
      builder.append((char) ((u >> (i * 8)) & 0xFF));
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
