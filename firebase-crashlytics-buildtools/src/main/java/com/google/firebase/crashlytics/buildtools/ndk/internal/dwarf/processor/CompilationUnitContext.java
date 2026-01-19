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

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRange;
import java.util.List;
import java.util.Map;

/**
 * Represents contextual data applicable for the current compilation unit being processed.
 * See DWARF 4 spec section 3.1 for more information.
 */
public class CompilationUnitContext {

  public final FileContext fileContext;
  public final Header header;

  /**
   * Table for storing names to be looked up by Debugging Information Entries which have
   * DW_AT_specification attributes.
   * See DWARF 4 spec section 2.13.2 for more information.
   */
  public final Map<Long, String> specificationMap;

  /**
   * Table for storing names to be looked up by Debugging Information Entries which have
   * DW_AT_abstract_origin attributes.
   */
  public final Map<Long, String> abstractOriginMap;

  /**
   * The list of named ranges read and processed from the current compilation unit.
   */
  public final List<NamedRange> namedRanges = Lists.newLinkedList();

  private final Long debugLineOffset;
  private final Long lowPc;

  public CompilationUnitContext(
      FileContext fileContext,
      Header header,
      Map<Long, String> specificationMap,
      Map<Long, String> abstractOriginMap) {
    this(fileContext, header, specificationMap, abstractOriginMap, null, null);
  }

  public CompilationUnitContext(
      FileContext fileContext,
      Header header,
      Map<Long, String> specificationMap,
      Map<Long, String> abstractOriginMap,
      EntryData entryData) {
    this(
        fileContext,
        header,
        specificationMap,
        abstractOriginMap,
        entryData.lowPc,
        entryData.stmtList);
  }

  private CompilationUnitContext(
      FileContext fileContext,
      Header header,
      Map<Long, String> specificationMap,
      Map<Long, String> abstractOriginMap,
      Long lowPc,
      Long debugLineOffset) {
    this.fileContext = fileContext;
    this.header = header;
    this.specificationMap = specificationMap;
    this.abstractOriginMap = abstractOriginMap;
    this.lowPc = lowPc;
    this.debugLineOffset = debugLineOffset;
  }

  public Optional<Long> getDebugLineOffset() {
    return Optional.of(debugLineOffset);
  }

  public long getLowPc() {
    return Optional.of(lowPc).or(0L);
  }

  /**
   * Represents the header data for the current compilation unit.
   * See DWARF 4 spec section 7.5.1.1 for more information.
   */
  public static class Header {
    public final long offset;
    public final long length;
    public final int version;
    public final long abbrevOffset;
    public final int addressSize;
    public final int referenceSize;

    public Header(
        long offset,
        long length,
        int version,
        long abbrevOffset,
        int addressSize,
        int referenceSize) {
      this.offset = offset;
      this.length = length;
      this.version = version;
      this.abbrevOffset = abbrevOffset;
      this.addressSize = addressSize;
      this.referenceSize = referenceSize;
    }
  }

  public static class EntryData {
    final Long lowPc;
    final Long stmtList;

    public EntryData(Long lowPc, Long stmtList) {
      this.lowPc = lowPc;
      this.stmtList = stmtList;
    }
  }
}
