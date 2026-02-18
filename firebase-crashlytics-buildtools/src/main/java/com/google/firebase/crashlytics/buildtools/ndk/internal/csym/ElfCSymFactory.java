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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DebugLineEntry;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRange;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRanges;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.EMachine;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfDataParser;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfFileHeader;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfSectionHeaders;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfSymbol;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class ElfCSymFactory implements CSymFactory {

  /**
   * Association between an ELF symbol and the group of .debug_line entries which correspond to it.
   */
  private static class DebugLineGroup {

    // Comparator which will order the .debug_line entry set by address.
    private static final Comparator<DebugLineEntry> ADDRESS_COMPARATOR =
        new Comparator<DebugLineEntry>() {
          @Override
          public int compare(DebugLineEntry lhs, DebugLineEntry rhs) {
            long x = lhs.address;
            long y = rhs.address;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
          }
        };

    // TreeSet maintains ordering given by the above comparator.
    private final TreeSet<DebugLineEntry> _lineEntries;
    public final String symbolName;
    public final long symbolAddr;
    public final long symbolSize;

    public DebugLineGroup(String symbolName, long symbolAddr, long symbolSize) {
      this._lineEntries = new TreeSet<DebugLineEntry>(ADDRESS_COMPARATOR);
      this.symbolName = symbolName;
      this.symbolAddr = symbolAddr;
      this.symbolSize = symbolSize;
    }

    public void add(DebugLineEntry entry) {
      this._lineEntries.add(entry);
    }

    public boolean hasEntries() {
      return !this._lineEntries.isEmpty();
    }

    /**
     * @return a list of the .debug_line entries in this group, ordered by address.
     */
    public List<DebugLineEntry> entryList() {
      return new ArrayList<DebugLineEntry>(this._lineEntries);
    }
  }

  private static final String ELF_CSYM_TYPE = "elf_symtab";
  private static final String DWARF_CSYM_TYPE = "dwarf_debug";

  private final boolean _featureUseDebugInfo;

  public ElfCSymFactory(boolean featureUseDebugInfo) {
    _featureUseDebugInfo = featureUseDebugInfo;
  }

  @Override
  public CSym createCSymFromFile(File unstrippedLib) throws CSymException, IOException {
    if (!unstrippedLib.isFile()) {
      throw new IllegalArgumentException("Invalid object file: " + unstrippedLib);
    }

    final CSymFactoryHandler handler = new CSymFactoryHandler(_featureUseDebugInfo);

    ElfDataParser.parse(unstrippedLib, handler, _featureUseDebugInfo);

    return handler.getBuilder().build();
  }

  private static final class CSymFactoryHandler implements ElfDataParser.ContentHandler {

    private final boolean _featureUseDebugInfo;

    private final Map<Long, DebugLineGroup> _elfSymbolGroups = new HashMap<Long, DebugLineGroup>();
    private final TreeMap<Long, ElfSymbol> _sortedSymbolIndex = new TreeMap<Long, ElfSymbol>();

    private CSym.Builder _builder;
    private int _arch;
    private String _uuid;
    private String _archName;
    private boolean _isArm;
    private boolean _hasDebugInfo;

    public CSymFactoryHandler(boolean useDebugInfo) {
      this._featureUseDebugInfo = useDebugInfo;
    }

    @Override
    public void processElfHeader(ElfFileHeader fileHeader) {
      _arch = fileHeader.eMachine;
      _isArm = (_arch == EMachine.EM_ARM || _arch == EMachine.EM_AARCH64);
      _archName = EMachine.getMachineName(_arch);
    }

    @Override
    public void processSectionHeaders(ElfSectionHeaders sectionHeaders) {
      _hasDebugInfo = sectionHeaders.getHeaderByName(".debug_info").isPresent();
    }

    @Override
    public void processBuildId(byte[] buildId) {
      _uuid = getBuildIdString(buildId);
    }

    @Override
    public void processArmVersion(String armVersion) {
      if (_arch == EMachine.EM_ARM) {
        _archName += String.format("v%s", armVersion);
      }
    }

    @Override
    public void startProcessingSymbols() {
      final String type = _featureUseDebugInfo && _hasDebugInfo ? DWARF_CSYM_TYPE : ELF_CSYM_TYPE;
      _builder = new CSym.Builder(_uuid, type, _archName);

      final String log =
          _hasDebugInfo
              ? (_featureUseDebugInfo
                  ? "Using DWARF data for cSYM generation."
                  : "Using ELF symbols with DWARF line number information for cSYM generation.")
              : "Using ELF data for cSYM generation.";

      Buildtools.logD(log);
    }

    @Override
    public void processElfSymbols(List<ElfSymbol> elfSymbols) {
      if (!_hasDebugInfo) {
        // Go ahead and configure the builder if we do not expect any further debug data.
        populateBuilderWithElfSymbols(_builder, elfSymbols);
        return;
      }

      if (!_featureUseDebugInfo) {
        // If not using all DWARF info, we'll need to index the ELF symbols for later
        // matching with the .debug_line data.
        indexElfSymbols(elfSymbols, _sortedSymbolIndex, _elfSymbolGroups, _isArm);
      }
    }

    @Override
    public void processDebugInfoCompilationUnit(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries) {

      // If the feature switch is disabled, maintain backward-compatible behavior of indexing
      // ELF symbols to cross-reference with .debug_line data.
      if (!_featureUseDebugInfo) {
        populateElfSymbolGroups(debugLineEntries, _sortedSymbolIndex, _elfSymbolGroups, _isArm);
        return;
      }

      final List<DebugLineGroup> debugLineGroups =
          createDwarfDebugLineGroups(namedRanges, debugLineEntries);
      populateBuilderWithDebugLineGroups(_builder, debugLineGroups);
    }

    @Override
    public void endProcessingSymbols() {
      // If the feature switch is disabled, but DWARF data is available, maintain
      // backward-compatible behavior of using .debug_line data with ELF symbols.
      if (!_featureUseDebugInfo && _hasDebugInfo) {
        final List<DebugLineGroup> debugLineGroups = Lists.newArrayList(_elfSymbolGroups.values());
        populateBuilderWithDebugLineGroups(_builder, debugLineGroups);
      }
    }

    public CSym.Builder getBuilder() {
      return _builder;
    }

    /**
     * Filter and associate ELF symbols with their associated .debug_line entries.
     * ARM (specifically THUMB) symbols should be normalized by masking with ~1 according to
     * http://infocenter.arm.com/help/topic/com.arm.doc.ihi0044e/IHI0044E_aaelf.pdf Section 4.5.3
     */
    private static void indexElfSymbols(
        List<ElfSymbol> elfSymbols,
        TreeMap<Long, ElfSymbol> symbolIndex,
        Map<Long, DebugLineGroup> elfSymbolGroups,
        boolean isArm) {
      for (ElfSymbol symbol : elfSymbols) {
        // Pre-filter ARM symbols.
        if (!isArmMappingSymbol(symbol)) {
          long symbolAddr = isArm ? (symbol.stValue & ~1) : symbol.stValue;
          symbolIndex.put(symbolAddr, symbol);
          if (isNecessarySymbol(symbol)) {
            elfSymbolGroups.put(
                symbolAddr, new DebugLineGroup(symbol.stNameString, symbolAddr, symbol.stSize));
          }
        }
      }
    }

    private static void populateElfSymbolGroups(
        List<DebugLineEntry> debugLineEntries,
        TreeMap<Long, ElfSymbol> symbolIndex,
        Map<Long, DebugLineGroup> elfSymbolGroups,
        boolean isArm) {
      for (DebugLineEntry debugLineEntry : debugLineEntries) {
        long address = debugLineEntry.address;

        ElfSymbol foundSymbol =
            symbolIndex.containsKey(address)
                ? symbolIndex.get(address)
                : findEnclosingElfSymbol(symbolIndex, address);

        long foundSymbolAddr = isArm ? (foundSymbol.stValue & ~1) : foundSymbol.stValue;

        DebugLineGroup foundLineGroup = elfSymbolGroups.get(foundSymbolAddr);
        if (foundLineGroup != null) {
          foundLineGroup.add(debugLineEntry);
        }
      }
    }

    private static List<DebugLineGroup> createDwarfDebugLineGroups(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries) {
      final Map<Long, DebugLineGroup> lineGroups = Maps.newHashMap();

      for (DebugLineEntry entry : debugLineEntries) {
        Optional<NamedRange> range = namedRanges.rangeFor(entry.address);
        Optional<DebugLineGroup> groupFromRangeOpt =
            range.transform(
                new Function<NamedRange, DebugLineGroup>() {
                  @Override
                  public DebugLineGroup apply(NamedRange input) {
                    return new DebugLineGroup(input.name, input.start, input.end - input.start);
                  }
                });

        if (!groupFromRangeOpt.isPresent()) {
          continue;
        }

        DebugLineGroup groupFromRange = groupFromRangeOpt.get();
        DebugLineGroup group =
            Optional.fromNullable(lineGroups.get(groupFromRange.symbolAddr)).or(groupFromRange);

        group.add(entry);
        lineGroups.put(group.symbolAddr, group);
      }

      return Lists.newArrayList(lineGroups.values());
    }

    private static void populateBuilderWithElfSymbols(
        CSym.Builder builder, List<ElfSymbol> elfSymbols) {
      for (ElfSymbol symbol : elfSymbols) {
        if (isNecessarySymbol(symbol)) {
          builder.addRange(symbol.stValue, symbol.stSize, symbol.stNameString);
        }
      }
    }

    private static void populateBuilderWithDebugLineGroups(
        CSym.Builder builder, List<DebugLineGroup> symbolGroups) {
      // Because we are not yet using the .debug_info data for true range sizing, we must estimate
      // the size of each
      // range based on the range addresses and the ELF symbol size before adding the data to the
      // CSym builder.
      // This should provide size values that are close enough until we implement full .debug_info
      // reading.
      for (DebugLineGroup lineGroup : symbolGroups) {
        final String symbolName = lineGroup.symbolName;
        final long symbolAddr = lineGroup.symbolAddr;
        final long symbolSize = lineGroup.symbolSize;

        if (lineGroup.hasEntries()) {
          List<DebugLineEntry> entryList = lineGroup.entryList();
          int endIndex = entryList.size() - 1;
          for (int i = 0; i < endIndex; ++i) {
            DebugLineEntry curr = entryList.get(i), next = entryList.get(i + 1);
            long currSize = next.address - curr.address;
            builder.addRange(curr.address, currSize, symbolName, curr.file, curr.lineNumber);
          }
          // The last entry's size should be the rest of the ELF symbol size.
          DebugLineEntry last = entryList.get(endIndex);
          long lastSize = symbolAddr + symbolSize - last.address;
          builder.addRange(last.address, lastSize, symbolName, last.file, last.lineNumber);
        } else {
          builder.addRange(symbolAddr, symbolSize, symbolName);
        }
      }
    }

    // Find the enclosing symbol for addresses that aren't specifically in the symbol index.
    private static ElfSymbol findEnclosingElfSymbol(TreeMap<Long, ElfSymbol> index, long address) {
      Long prevKey = index.lowerKey(address);
      return (prevKey != null) ? index.get(prevKey) : null;
    }

    // Validate whether this symbol is necessary for processing a crash.
    // We only need symbols which specify functions with a defined size.
    private static boolean isNecessarySymbol(ElfSymbol symbol) {
      return (symbol != null)
          && ((symbol.stInfo & 15) == ElfSymbol.STT_FUNC)
          && (symbol.stSize != 0);
    }

    // The ARM architecture inserts extra symbols to identify blocks of compiled code. We need to
    // skip over these.
    private static boolean isArmMappingSymbol(ElfSymbol symbol) {
      return (symbol != null
          && symbol.stNameString != null
          && (symbol.stNameString.startsWith("$a")
              || symbol.stNameString.startsWith("$d")
              || symbol.stNameString.startsWith("$t")));
    }

    private static String getBuildIdString(byte[] identifierBytes) {
      return buildIdBytesToString(identifierBytes);
    }

    private static String buildIdBytesToString(byte[] buildIdBytes) {
      final StringBuilder sb = new StringBuilder();
      for (byte b : buildIdBytes) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    }
  }
}
