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

public class DebugElfSectionHeaders {
  public final ElfSectionHeader debugInfo;
  public final ElfSectionHeader debugAbbrev;
  public final ElfSectionHeader debugStr;
  public final ElfSectionHeader debugRanges;
  public final ElfSectionHeader debugLine;

  /**
   * Create a DebugElfSectionHeaders object from the given ElfSectionHeaders
   * @param elfHeaders The ELF headers containing debug sections
   * @return an Optional containing a fully-populated DebugElfSectionHeaders object if all
   * required .debug headers are present, or Optional.absent if one or more required
   * .debug headers are missing.
   */
  public static Optional<DebugElfSectionHeaders> from(ElfSectionHeaders elfHeaders) {
    Optional<ElfSectionHeader> debugInfo =
        elfHeaders.getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_INFO);
    Optional<ElfSectionHeader> debugAbbrev =
        elfHeaders.getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_ABBREV);
    Optional<ElfSectionHeader> debugStr =
        elfHeaders.getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_STR);
    Optional<ElfSectionHeader> debugRanges =
        elfHeaders.getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_RANGES);
    Optional<ElfSectionHeader> debugLine =
        elfHeaders.getHeaderByName(ElfSectionHeaders.SECTION_DEBUG_LINE);

    if (!debugInfo.isPresent()
        || !debugAbbrev.isPresent()
        || !debugStr.isPresent()
        || !debugLine.isPresent()) {
      return Optional.absent();
    }

    return Optional.of(
        new DebugElfSectionHeaders(
            debugInfo.get(),
            debugAbbrev.get(),
            debugStr.get(),
            debugLine.get(),
            debugRanges.orNull()));
  }

  DebugElfSectionHeaders(
      ElfSectionHeader debugInfo,
      ElfSectionHeader debugAbbrev,
      ElfSectionHeader debugStr,
      ElfSectionHeader debugLine,
      ElfSectionHeader debugRanges) {
    this.debugInfo = debugInfo;
    this.debugAbbrev = debugAbbrev;
    this.debugStr = debugStr;
    this.debugRanges = debugRanges;
    this.debugLine = debugLine;
  }
}
