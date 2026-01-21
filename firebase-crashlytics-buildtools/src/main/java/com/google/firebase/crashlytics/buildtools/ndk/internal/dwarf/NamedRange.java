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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf;

import com.google.common.base.Objects;

public class NamedRange implements Comparable<NamedRange> {

  public final String name;
  public final Long start;
  public final Long end;

  public NamedRange(String name, Long start, Long end) {
    this.name = name;
    this.start = start;
    this.end = end;
  }

  public boolean contains(NamedRange r) {
    return r.start >= start && r.end <= end;
  }

  public boolean contains(long address) {
    return address >= start && address <= end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NamedRange)) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    NamedRange that = (NamedRange) o;
    return Objects.equal(start, that.start) && Objects.equal(end, that.end);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(start, end);
  }

  @Override
  public int compareTo(NamedRange o) {
    return start.compareTo(o.start);
  }
}
