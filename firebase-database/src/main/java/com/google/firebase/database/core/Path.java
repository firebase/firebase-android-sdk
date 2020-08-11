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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class Path implements Iterable<ChildKey>, Comparable<Path> {

  public static Path getRelative(Path from, Path to) {
    ChildKey outerFront = from.getFront();
    ChildKey innerFront = to.getFront();
    if (outerFront == null) {
      return to;
    } else if (outerFront.equals(innerFront)) {
      return getRelative(from.popFront(), to.popFront());
    } else {
      throw new DatabaseException("INTERNAL ERROR: " + to + " is not contained in " + from);
    }
  }

  private final ChildKey[] pieces;
  private final int start;
  private final int end;

  private static final Path EMPTY_PATH = new Path("");

  public static Path getEmptyPath() {
    return EMPTY_PATH;
  }

  public Path(ChildKey... segments) {
    this.pieces = Arrays.copyOf(segments, segments.length);
    this.start = 0;
    this.end = segments.length;
    for (ChildKey name : segments) {
      hardAssert(name != null, "Can't construct a path with a null value!");
    }
  }

  public Path(List<String> segments) {
    this.pieces = new ChildKey[segments.size()];
    int i = 0;
    for (String segment : segments) {
      this.pieces[i++] = ChildKey.fromString(segment);
    }
    this.start = 0;
    this.end = segments.size();
  }

  public Path(String pathString) {
    String[] segments = pathString.split("/", -1);
    int count = 0;
    for (String segment : segments) {
      if (segment.length() > 0) {
        count++;
      }
    }
    pieces = new ChildKey[count];
    int j = 0;
    for (String segment : segments) {
      if (segment.length() > 0) {
        pieces[j++] = ChildKey.fromString(segment);
      }
    }
    this.start = 0;
    this.end = pieces.length;
  }

  private Path(ChildKey[] pieces, int start, int end) {
    this.pieces = pieces;
    this.start = start;
    this.end = end;
  }

  public Path child(Path path) {
    int newSize = this.size() + path.size();
    ChildKey[] newPieces = new ChildKey[newSize];
    System.arraycopy(this.pieces, this.start, newPieces, 0, this.size());
    System.arraycopy(path.pieces, path.start, newPieces, this.size(), path.size());
    return new Path(newPieces, 0, newSize);
  }

  public Path child(ChildKey child) {
    int size = this.size();
    ChildKey[] newPieces = new ChildKey[size + 1];
    System.arraycopy(this.pieces, this.start, newPieces, 0, size);
    newPieces[size] = child;
    return new Path(newPieces, 0, size + 1);
  }

  @Override
  public String toString() {
    if (this.isEmpty()) {
      return "/";
    } else {
      StringBuilder builder = new StringBuilder();
      for (int i = this.start; i < this.end; i++) {
        builder.append("/");
        builder.append(pieces[i].asString());
      }
      return builder.toString();
    }
  }

  public String wireFormat() {
    if (this.isEmpty()) {
      return "/";
    } else {
      StringBuilder builder = new StringBuilder();
      for (int i = this.start; i < this.end; i++) {
        if (i > this.start) {
          builder.append("/");
        }
        builder.append(pieces[i].asString());
      }
      return builder.toString();
    }
  }

  public List<String> asList() {
    List<String> result = new ArrayList<String>(this.size());
    for (ChildKey key : this) {
      result.add(key.asString());
    }
    return result;
  }

  public ChildKey getFront() {
    if (this.isEmpty()) {
      return null;
    } else {
      return pieces[this.start];
    }
  }

  public Path popFront() {
    int newStart = this.start;
    if (!this.isEmpty()) {
      newStart++;
    }
    return new Path(pieces, newStart, this.end);
  }

  public Path getParent() {
    if (this.isEmpty()) {
      return null;
    } else {
      return new Path(pieces, start, end - 1);
    }
  }

  public ChildKey getBack() {
    if (!this.isEmpty()) {
      return pieces[end - 1];
    } else {
      return null;
    }
  }

  public boolean isEmpty() {
    return start >= end;
  }

  public int size() {
    return this.end - this.start;
  }

  @Override
  public Iterator<ChildKey> iterator() {
    return new Iterator<ChildKey>() {
      int offset = start;

      @Override
      public boolean hasNext() {
        return offset < end;
      }

      @Override
      public ChildKey next() {
        if (!hasNext()) {
          throw new NoSuchElementException("No more elements.");
        }
        ChildKey child = pieces[offset];
        offset++;
        return child;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Can't remove component from immutable Path!");
      }
    };
  }

  public boolean contains(Path other) {
    if (this.size() > other.size()) {
      return false;
    }

    int i = this.start;
    int j = other.start;
    while (i < this.end) {
      if (!this.pieces[i].equals(other.pieces[j])) {
        return false;
      }
      i++;
      j++;
    }

    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Path)) {
      return false;
    }
    if (this == other) {
      return true;
    }
    Path otherPath = (Path) other;
    if (size() != otherPath.size()) {
      return false;
    }
    for (int i = start, j = otherPath.start; i < end && j < otherPath.end; i++, j++) {
      if (!this.pieces[i].equals(otherPath.pieces[j])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 0;
    for (int i = start; i < end; i++) {
      hashCode = hashCode * 37 + pieces[i].hashCode();
    }
    return hashCode;
  }

  @Override
  public int compareTo(Path other) {
    int i, j;
    for (i = start, j = other.start; i < end && j < other.end; i++, j++) {
      int comp = this.pieces[i].compareTo(other.pieces[j]);
      if (comp != 0) {
        return comp;
      }
    }
    if (i == end && j == other.end) {
      return 0;
    } else if (i == end) {
      return -1;
    } else {
      return 1;
    }
  }
}
