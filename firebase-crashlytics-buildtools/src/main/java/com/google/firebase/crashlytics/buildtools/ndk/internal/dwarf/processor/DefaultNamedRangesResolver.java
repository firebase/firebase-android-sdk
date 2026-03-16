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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRange;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Handles reading entries from the .debug_ranges table, given an offset into that table, returning
 * a list of named ranges; one for each entry read from the table.
 * See DWARF 4 spec section 2.17.3 for more information.
 */
public class DefaultNamedRangesResolver implements NamedRangesResolver {

  private final ByteReader byteReader;
  private final int addressSize;
  private final long rangesSectionOffset;

  public DefaultNamedRangesResolver(
      ByteReader byteReader, int addressSize, long rangesSectionOffset) {
    this.byteReader = byteReader;
    this.addressSize = addressSize;
    this.rangesSectionOffset = rangesSectionOffset;
  }

  /**
   * Read the range list from the .debug_ranges section at the given offset, and return a list of
   * NamedRange objects with the given name; one for each range in the list.
   * @param offset the offset into the .debug_ranges section to begin reading range entries.
   * @param name the name to apply to each range read from the range list.
   * @param baseAddress The initial "applicable base address of the compilation unit referencing
   * this range list". The applicable base address of a range list entry is determined by the
   * closest preceding base address selection entry in the same range list. If there is no such
   * selection entry, then the applicable base address defaults to the base address of the
   * compilation unit.
   * @return a list of named ranges with the provided name for each range read from the list
   */
  @Override
  public List<NamedRange> resolveNamedRanges(long offset, String name, long baseAddress) {
    final List<NamedRange> namedRanges = new LinkedList<NamedRange>();

    try {
      final long originalOffset = byteReader.getCurrentOffset();
      byteReader.seek(rangesSectionOffset + offset);

      long currentBaseAddress = baseAddress;

      // Read from the current offset until we reach an end of list entry.
      while (true) {
        long beginAddress = byteReader.readLong(addressSize);
        long endAddress = byteReader.readLong(addressSize);

        if (beginAddress == 0L && endAddress == 0L) {
          // End of list entry.
          break;
        }

        // A base selection entry's begin address is "the value of the largest representable address
        // offset (for example, 0xffffffff when the size of an address is 32 bits)", which as a
        // signed long value is -1.
        if (beginAddress == -1) {
          // A base address selection entry's end address defines the appropriate base address for
          // use in interpreting the beginning and ending address offsets of subsequent entries of
          // the range list.
          currentBaseAddress = endAddress;
          continue;
        }

        // Invalid entry.
        // DWARF 4 specification section 2.17.3:
        // "A range list entry (but not a base address selection or end of list entry) whose
        // beginning and ending addresses are equal has no effect because the size of the range
        // covered by such an entry is zero."
        if (beginAddress >= endAddress) {
          continue;
        }

        // Adjust the addresses by the applicable base address.
        beginAddress += currentBaseAddress;
        endAddress += currentBaseAddress;

        namedRanges.add(new NamedRange(name, beginAddress, endAddress));
      }

      byteReader.seek(originalOffset);
    } catch (IOException e) {
      Buildtools.logE("Could not properly resolve range entries", e);
      return Collections.emptyList();
    }

    return namedRanges;
  }
}
