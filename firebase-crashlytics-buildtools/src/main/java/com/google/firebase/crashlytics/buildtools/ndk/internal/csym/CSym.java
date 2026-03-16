/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.ndk.internal.csym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CSym {

  public static final class Range implements Comparable<Range> {
    public final int insertionIndex;
    public final long offset;
    public final long size;
    public final String symbol;
    public final String file;
    public final long lineNumber;

    public Range(
        int insertionIndex, long offset, long size, String symbol, String file, long lineNumber) {
      this.insertionIndex = insertionIndex;
      this.offset = offset;
      this.size = size;
      this.symbol = symbol;
      this.file = file;
      this.lineNumber = lineNumber;
    }

    public Range(long offset, long size, String symbol, String file, long lineNumber) {
      this(-1, offset, size, symbol, file, lineNumber);
    }

    @Override
    public int compareTo(Range other) {
      int value = Long.valueOf(this.offset).compareTo(other.offset);
      // If the offsets are the same, fall back to original insertion ordering.
      return (value != 0) ? value : Integer.valueOf(insertionIndex).compareTo(other.insertionIndex);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Range range = (Range) o;

      if (lineNumber != range.lineNumber) {
        return false;
      }
      if (offset != range.offset) {
        return false;
      }
      if (size != range.size) {
        return false;
      }
      if (file != null ? !file.equals(range.file) : range.file != null) {
        return false;
      }
      if (symbol != null ? !symbol.equals(range.symbol) : range.symbol != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = (int) (offset ^ (offset >>> 32));
      result = 31 * result + (int) (size ^ (size >>> 32));
      result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
      result = 31 * result + (file != null ? file.hashCode() : 0);
      result = 31 * result + (int) (lineNumber ^ (lineNumber >>> 32));
      return result;
    }

    @Override
    public String toString() {
      return "{ file:"
          + file
          + ", symbol:"
          + symbol
          + ", offset:"
          + offset
          + ", size:"
          + size
          + ", line:"
          + lineNumber
          + " }";
    }
  }

  public static final class Builder {

    private final List<Range> _ranges = new ArrayList<Range>();
    private final String _uuid;
    private final String _type;
    private final String _architecture;

    private int insertionIndex = 0;

    public Builder(String uuid, String type, String architecture) {
      _uuid = uuid;
      _type = type;
      _architecture = architecture;
    }

    public Builder addRange(long offset, long size, String symbol) {
      return addRange(offset, size, symbol, null);
    }

    public Builder addRange(long offset, long size, String symbol, String file) {
      return addRange(offset, size, symbol, file, -1);
    }

    public Builder addRange(long offset, long size, String symbol, String file, long lineNumber) {
      _ranges.add(new Range(insertionIndex++, offset, size, symbol, file, lineNumber));
      return this;
    }

    public CSym build() {
      return new CSym(this);
    }
  }

  private final List<Range> _ranges;
  private final List<String> _files;
  private final List<String> _symbols;
  private final String _uuid;
  private final String _type;
  private final String _architecture;

  private CSym(Builder builder) {
    _ranges = new ArrayList<Range>();

    List<Range> ranges = new ArrayList<Range>(builder._ranges);
    Collections.sort(ranges);

    Set<String> fileSet = new HashSet<String>();
    Set<String> symbolSet = new HashSet<String>();

    long prevOffset = -1;
    for (Range range : ranges) {
      if (range.file != null) {
        fileSet.add(range.file);
      }
      if (range.symbol != null) {
        symbolSet.add(range.symbol);
      }
      // If the incoming range offset is the same as the last, replace it.
      if (range.offset == prevOffset) {
        _ranges.set(_ranges.size() - 1, range);
      } else {
        _ranges.add(range);
      }
      prevOffset = range.offset;
    }

    _files = Collections.unmodifiableList(new ArrayList<String>(fileSet));
    _symbols = Collections.unmodifiableList(new ArrayList<String>(symbolSet));

    _uuid = builder._uuid;
    _type = builder._type;
    _architecture = builder._architecture;
  }

  public String getArchitecture() {
    return _architecture;
  }

  public String getUUID() {
    return _uuid;
  }

  public String getType() {
    return _type;
  }

  public List<String> getFiles() {
    return _files;
  }

  public List<String> getSymbols() {
    return _symbols;
  }

  public List<Range> getRanges() {
    return _ranges;
  }
}
