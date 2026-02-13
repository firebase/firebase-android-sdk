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

package com.google.firebase.crashlytics.buildtools.ndk.internal.elf;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;

public class ElfSectionHeaders {
  public static final String SECTION_DEBUG_INFO = ".debug_info";
  public static final String SECTION_DEBUG_ABBREV = ".debug_abbrev";
  public static final String SECTION_DEBUG_STR = ".debug_str";
  public static final String SECTION_DEBUG_RANGES = ".debug_ranges";
  public static final String SECTION_DEBUG_LINE = ".debug_line";

  private final List<ElfSectionHeader> _sectionHeaders;
  private final Map<String, ElfSectionHeader> _nameIndex;

  public ElfSectionHeaders(List<ElfSectionHeader> headers) {
    _sectionHeaders = headers;
    _nameIndex = indexByName(headers);
  }

  public Optional<ElfSectionHeader> getHeaderByName(String name) {
    return Optional.fromNullable(_nameIndex.get(name));
  }

  public Optional<ElfSectionHeader> getHeaderByIndex(int index) {
    return (index < 0 || index >= _sectionHeaders.size())
        ? Optional.<ElfSectionHeader>absent()
        : Optional.of(_sectionHeaders.get(index));
  }

  public Optional<ElfSectionHeader> findHeader(Predicate<ElfSectionHeader> predicate) {
    return Iterables.tryFind(_sectionHeaders, predicate);
  }

  public List<ElfSectionHeader> getList() {
    return _sectionHeaders;
  }

  public boolean hasDebugInfo() {
    return getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_INFO).isPresent();
  }

  private static Map<String, ElfSectionHeader> indexByName(List<ElfSectionHeader> headers) {
    final Map<String, ElfSectionHeader> index = Maps.newLinkedHashMap();
    for (ElfSectionHeader header : headers) {
      index.put(header.shNameString, header);
    }
    return index;
  }
}
