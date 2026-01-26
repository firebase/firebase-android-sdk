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
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompilationUnitContext;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompilationUnitContext.EntryData;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompilationUnitContext.Header;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompileUnitAttributeProcessor;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.DefaultAttributesReader;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.DefaultNamedRangesResolver;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.FileContext;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.MissingSectionNamedRangesResolver;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.NamedRangesAttributeProcessor;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.NamedRangesResolver;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.ReferenceBytesConverter;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.SkipAttributesReader;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.DebugElfSectionHeaders;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfDataParser;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfSectionHeader;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DwarfDataParser {
  private static final NamedRangesResolver MISSING_SECTION_RESOLVER =
      new MissingSectionNamedRangesResolver();

  private static final Set<DWTag> RELEVANT_TAGS =
      Sets.newHashSet(DWTag.SUBPROGRAM, DWTag.INLINED_SUBROUTINE);

  private final ByteReader byteReader;
  private final ByteOrder byteOrder;
  private final DebugElfSectionHeaders debugHeaders;
  private final boolean isDebugFeatureFlagEnabled;

  public DwarfDataParser(
      ByteReader byteReader,
      ByteOrder byteOrder,
      DebugElfSectionHeaders debugHeaders,
      boolean isDebugFeatureFlagEnabled) {
    this.byteReader = byteReader;
    this.byteOrder = byteOrder;
    this.debugHeaders = debugHeaders;
    this.isDebugFeatureFlagEnabled = isDebugFeatureFlagEnabled;
  }

  public void parse(ElfDataParser.ContentHandler handler) throws IOException {
    byteReader.seek(debugHeaders.debugInfo.shOffset);

    final long sectionEnd = debugHeaders.debugInfo.shOffset + debugHeaders.debugInfo.shSize;

    int index = 0;
    final ReferenceBytesConverter referenceBytesConverter = new ReferenceBytesConverter(byteOrder);
    final FileContext fileContext = new FileContext(debugHeaders, referenceBytesConverter);

    // Read the entire .debug_info section, one compilation unit at a time.
    while (byteReader.getCurrentOffset() != sectionEnd) {
      CompilationUnitContext cuContext =
          readCompilationUnit(byteReader, fileContext, isDebugFeatureFlagEnabled);

      // Get the .debug_line data for this compilation unit.
      final int cuAddressSize = cuContext.header.addressSize;
      final NamedRanges namedRanges = new NamedRanges(cuContext.namedRanges);
      final long nextOffset = byteReader.getCurrentOffset();
      List<DebugLineEntry> lineEntries;
      if (isDebugFeatureFlagEnabled) {
        final Optional<Long> debugLineOffset = cuContext.getDebugLineOffset();
        lineEntries =
            (debugLineOffset.isPresent())
                ? readDebugLineData(
                    byteReader,
                    debugHeaders.debugLine.shOffset + debugLineOffset.get(),
                    cuAddressSize)
                : Collections.<DebugLineEntry>emptyList();
      } else {
        lineEntries =
            readDebugLineDataAtIndex(byteReader, index, debugHeaders.debugLine, cuAddressSize);
      }
      handler.processDebugInfoCompilationUnit(namedRanges, lineEntries);
      byteReader.seek(nextOffset);
      index++;
    }
  }

  private static List<DebugLineEntry> readDebugLineData(
      ByteReader fileReader, long debugLineOffset, int pointerSize) throws IOException {
    final DebugLineStateMachine stateMachine = new DebugLineStateMachine();
    try {
      fileReader.seek(debugLineOffset);
      return stateMachine.runFromCurrentOffset(fileReader, pointerSize);
    } catch (DwarfException de) {
      // Debug data is optional, so log and then continue.
      Buildtools.logE("Could not parse debug data.", de);
    }

    return Collections.emptyList();
  }

  private static List<DebugLineEntry> readDebugLineDataAtIndex(
      ByteReader fileReader, int index, ElfSectionHeader debugLineSectionHeader, int pointerSize)
      throws IOException {
    final DebugLineStateMachine stateMachine = new DebugLineStateMachine();
    final long debugLineEndOffset = debugLineSectionHeader.shOffset + debugLineSectionHeader.shSize;
    try {
      fileReader.seek(debugLineSectionHeader.shOffset);
      return stateMachine.runForIndex(fileReader, index, debugLineEndOffset, pointerSize);
    } catch (DwarfException de) {
      // Debug data is optional, so log and then continue.
      Buildtools.logE("Could not parse debug data.", de);
    }

    return Collections.emptyList();
  }

  private static CompilationUnitContext readCompilationUnit(
      ByteReader reader, FileContext fileContext, boolean isDebugFeatureFlagEnabled)
      throws IOException {
    // Read the unit_length field for this compilation unit, which also specifies
    //   the reference size.
    // See DWARF 4 spec section 7.5.1.1 for more information.
    final long debugInfoOffset = fileContext.debugSectionHeaders.debugInfo.shOffset;
    long offset = reader.getCurrentOffset() - debugInfoOffset;
    int referenceSize = 4;

    long length = reader.readLong(4);
    if (length == 0xffffffff) {
      referenceSize = 8;
      length = reader.readLong(8);
    }

    return readCompilationUnit(
        reader, offset, length, referenceSize, fileContext, isDebugFeatureFlagEnabled);
  }

  private static CompilationUnitContext readCompilationUnit(
      ByteReader reader,
      long offset,
      long length,
      int referenceSize,
      FileContext fileContext,
      boolean isDebugFeatureFlagEnabled)
      throws IOException {
    // Read the compilation unit header information
    // See DWARF 4 spec section 7.5.1.1 for more information.
    long endOffset = reader.getCurrentOffset() + length;
    int version = reader.readInt(2);
    long abbrevOffset = reader.readLong(referenceSize);
    int addressSize = reader.readInt(1);
    long entriesOffset = reader.getCurrentOffset();

    final CompilationUnitContext.Header cuHeader =
        new CompilationUnitContext.Header(
            offset, length, version, abbrevOffset, addressSize, referenceSize);

    final Map<Long, String> specificationMap = Maps.newTreeMap();
    final Map<Long, String> abstractOriginMap = Maps.newTreeMap();
    CompilationUnitContext cuContext;
    if (isDebugFeatureFlagEnabled) {
      final long debugAbbrevSectionOffset = fileContext.debugSectionHeaders.debugAbbrev.shOffset;
      final Map<Integer, DebugAbbrevEntry> debugAbbrevEntries =
          readDebugAbbrevEntries(reader, debugAbbrevSectionOffset + abbrevOffset);
      reader.seek(entriesOffset);
      // Process all of the Debugging Information Entries for this compilation unit, storing
      // the processed named ranges in the list stored in the compilation unit context.
      cuContext =
          processCompilationUnit(
              reader,
              fileContext,
              cuHeader,
              specificationMap,
              abstractOriginMap,
              debugAbbrevEntries);
    } else {
      // Debug feature flag is disabled, so just return the context as-is, without processing
      // any of the Debugging Information Entries.
      cuContext =
          new CompilationUnitContext(fileContext, cuHeader, specificationMap, abstractOriginMap);
      reader.seek(endOffset);
    }
    return cuContext;
  }

  private static CompilationUnitContext processCompilationUnit(
      ByteReader reader,
      FileContext fileContext,
      CompilationUnitContext.Header cuHeader,
      Map<Long, String> specificationMap,
      Map<Long, String> abstractOriginMap,
      Map<Integer, DebugAbbrevEntry> abbrevEntries)
      throws IOException {
    // Process the single DW_TAG_compile_unit entry, and then process its children separately.
    final int abbrevCode = reader.readULEB128();
    final DebugAbbrevEntry abbrevEntry = abbrevEntries.get(abbrevCode);
    CompilationUnitContext cuContext =
        processCompilationUnitEntry(
            reader,
            fileContext,
            cuHeader,
            specificationMap,
            abstractOriginMap,
            abbrevEntry.attributes);
    if (abbrevEntry.hasChildren) {
      cuContext.namedRanges.addAll(processChildDebugInfoEntries(reader, cuContext, abbrevEntries));
    }
    return cuContext;
  }

  private static CompilationUnitContext processCompilationUnitEntry(
      ByteReader reader,
      FileContext fileContext,
      Header cuHeader,
      Map<Long, String> specificationMap,
      Map<Long, String> abstractOriginMap,
      List<DebugAbbrevEntry.Attribute> attributes)
      throws IOException {
    final CompileUnitAttributeProcessor attributeProcessor =
        new CompileUnitAttributeProcessor(fileContext.referenceBytesConverter);
    final DefaultAttributesReader<EntryData> attributesReader =
        new DefaultAttributesReader<EntryData>(
            reader,
            cuHeader,
            fileContext.referenceBytesConverter,
            attributeProcessor,
            fileContext.debugSectionHeaders.debugStr.shOffset);
    final EntryData entryData = attributesReader.readAttributes(attributes);
    // With the DW_TAG_compile_unit entry data, we can now build a complete CompilationUnitContext.
    return new CompilationUnitContext(
        fileContext, cuHeader, specificationMap, abstractOriginMap, entryData);
  }

  private static HashMap<Integer, DebugAbbrevEntry> readDebugAbbrevEntries(
      ByteReader reader, long offset) throws IOException {
    reader.seek(offset);

    HashMap<Integer, DebugAbbrevEntry> entries = Maps.newHashMap();

    int number;
    while ((number = reader.readULEB128()) != 0) {
      int tag = reader.readULEB128();
      boolean hasChildren = reader.readByte() != 0;

      entries.put(
          number,
          new DebugAbbrevEntry(number, tag, hasChildren, readDebugAbbrevEntryAttributes(reader)));
    }

    return entries;
  }

  private static List<DebugAbbrevEntry.Attribute> readDebugAbbrevEntryAttributes(ByteReader reader)
      throws IOException {
    List<DebugAbbrevEntry.Attribute> attributes = Lists.newLinkedList();

    for (; ; ) {
      int name = reader.readULEB128();
      int form = reader.readULEB128();

      if (name == 0 && form == 0) {
        break;
      }

      attributes.add(new DebugAbbrevEntry.Attribute(name, form));
    }

    return attributes;
  }

  private static List<NamedRange> processChildDebugInfoEntries(
      ByteReader reader,
      CompilationUnitContext cuContext,
      Map<Integer, DebugAbbrevEntry> abbrevEntries)
      throws IOException {
    // Recursively process all child entries of the DW_TAG_compile_unit Debugging Information Entry.
    final List<NamedRange> allNamedRanges = Lists.newLinkedList();
    final long debugInfoOffset = cuContext.fileContext.debugSectionHeaders.debugInfo.shOffset;
    long entryOffset = reader.getCurrentOffset() - debugInfoOffset;
    int abbrevCode = reader.readULEB128();
    while (abbrevCode > 0) {
      final DebugAbbrevEntry abbrevEntry = abbrevEntries.get(abbrevCode);

      // Process the current Debugging Information Entry, storing any named ranges read.
      List<NamedRange> namedRanges =
          processDebugInfoEntry(
              reader, cuContext, entryOffset, abbrevEntry.tag, abbrevEntry.attributes);

      if (abbrevEntry.hasChildren) {
        // Interleave any named ranges read from child entries into this entry's list of named
        // ranges in order to maintain a flat address space.
        namedRanges =
            interleaveRanges(
                namedRanges, processChildDebugInfoEntries(reader, cuContext, abbrevEntries));
      }

      allNamedRanges.addAll(namedRanges);

      entryOffset = reader.getCurrentOffset() - debugInfoOffset;
      abbrevCode = reader.readULEB128();
    }
    return allNamedRanges;
  }

  private static List<NamedRange> processDebugInfoEntry(
      ByteReader reader,
      CompilationUnitContext cuContext,
      long entryOffset,
      DWTag entryTag,
      List<DebugAbbrevEntry.Attribute> attributes)
      throws IOException {
    // We only care about DW_TAG_subprogram and DW_TAG_inlined_subroutine entries, because they
    // give us the information about which memory addresses in the program map to which function
    // names. Skip all other entries entirely.
    if (!RELEVANT_TAGS.contains(entryTag)) {
      new SkipAttributesReader(reader, cuContext.header).readAttributes(attributes);
      return Collections.emptyList();
    }

    final ElfSectionHeader debugRanges = cuContext.fileContext.debugSectionHeaders.debugRanges;
    final NamedRangesResolver namedRangesResolver =
        (debugRanges != null)
            ? new DefaultNamedRangesResolver(
                reader, cuContext.header.addressSize, debugRanges.shOffset)
            : MISSING_SECTION_RESOLVER;
    final NamedRangesAttributeProcessor attributeProcessor =
        new NamedRangesAttributeProcessor(entryOffset, cuContext, namedRangesResolver);
    return new DefaultAttributesReader<List<NamedRange>>(
            reader,
            cuContext.header,
            cuContext.fileContext.referenceBytesConverter,
            attributeProcessor,
            cuContext.fileContext.debugSectionHeaders.debugStr.shOffset)
        .readAttributes(attributes);
  }

  private static List<NamedRange> interleaveRanges(
      List<NamedRange> context, List<NamedRange> incoming) {
    if (context.isEmpty()) {
      return incoming;
    }

    List<NamedRange> result = Lists.newLinkedList();
    for (final NamedRange contextRange : context) {
      final Collection<NamedRange> children =
          Collections2.filter(incoming, isChildOf(contextRange));
      result.addAll(interleave(contextRange, children));
    }
    return result;
  }

  private static List<NamedRange> interleave(NamedRange parent, Collection<NamedRange> incoming) {
    if (incoming.isEmpty()) {
      return Lists.newArrayList(parent);
    }

    final List<NamedRange> result = Lists.newArrayList();

    long start = parent.start;
    long end = parent.end;

    final List<NamedRange> incomingSortedByAddress =
        Ordering.natural().immutableSortedCopy(incoming);

    for (NamedRange incomingRange : incomingSortedByAddress) {
      end = incomingRange.end;

      if (incomingRange.start > start) {
        result.add(new NamedRange(parent.name, start, incomingRange.start));
      }

      result.add(incomingRange);

      start = incomingRange.end;
    }

    if (end < parent.end) {
      result.add(new NamedRange(parent.name, end, parent.end));
    }

    return result;
  }

  private static Predicate<NamedRange> isChildOf(final NamedRange parent) {
    return new Predicate<NamedRange>() {
      @Override
      public boolean apply(NamedRange incoming) {
        return incoming != null && parent.contains(incoming);
      }
    };
  }
}
