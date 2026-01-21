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

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.TreeMap;

public class NamedRanges {
  public TreeMap<Long, NamedRange> _byStartAddress = Maps.newTreeMap();

  public NamedRanges(List<NamedRange> namedRanges) {
    for (NamedRange range : namedRanges) {
      _byStartAddress.put(range.start, range);
    }
  }

  public Optional<NamedRange> rangeFor(long address) {
    final NamedRange closest =
        _byStartAddress.containsKey(address)
            ? _byStartAddress.get(address)
            : findClosest(_byStartAddress, address);

    if (closest == null) {
      return Optional.absent();
    }

    return closest.contains(address) ? Optional.of(closest) : Optional.<NamedRange>absent();
  }

  private static NamedRange findClosest(TreeMap<Long, NamedRange> index, long address) {
    Long prevKey = index.lowerKey(address);
    return (prevKey != null) ? index.get(prevKey) : null;
  }
}
