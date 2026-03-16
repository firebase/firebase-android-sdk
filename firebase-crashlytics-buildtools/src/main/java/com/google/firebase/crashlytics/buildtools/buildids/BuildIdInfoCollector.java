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

package com.google.firebase.crashlytics.buildtools.buildids;

import static com.google.firebase.crashlytics.buildtools.Buildtools.getLogger;

import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DebugLineEntry;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRanges;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.EMachine;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfDataParser;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfFileHeader;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfSectionHeaders;
import com.google.firebase.crashlytics.buildtools.ndk.internal.elf.ElfSymbol;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BuildIdInfoCollector {

  public List<BuildIdInfo> collect(File mergedNativeLibsPath) {
    List<BuildIdInfo> buildIdInfoList = new ArrayList<>();
    for (File file : FileUtils.listFiles(mergedNativeLibsPath)) {
      Optional<BuildIdInfo> buildIdInfo = getBuildIdInfo(file);
      buildIdInfo.ifPresent(buildIdInfoList::add);
    }
    return buildIdInfoList;
  }

  private Optional<BuildIdInfo> getBuildIdInfo(File file) {
    BuildIdInfoContentHandler contentHandler = new BuildIdInfoContentHandler(file.getName());
    try {
      ElfDataParser.parse(file, contentHandler, false);
    } catch (IOException | NegativeArraySizeException | ArithmeticException ex) {
      // TODO(b/289053263): Make build tools support Go binaries smoother.
      // Ignore any file that doesn't parse, or has unexpected opcodeBase, to avoid breaking builds.
      getLogger().logD("Unable to parse binary: " + file.getPath() + " - " + ex.getMessage());
    }

    return contentHandler.optionalBuildInfo();
  }

  class BuildIdInfoContentHandler implements ElfDataParser.ContentHandler {
    private String _libraryName;
    private String _arch;
    private String _buildId;

    BuildIdInfoContentHandler(String libraryName) {
      _libraryName = libraryName;
    }

    @Override
    public void processElfHeader(ElfFileHeader fileHeader) {
      _arch = EMachine.getMachineName(fileHeader.eMachine);
    }

    @Override
    public void processBuildId(byte[] buildId) {
      _buildId = getBuildIdBytesToString(buildId);
    }

    public Optional<BuildIdInfo> optionalBuildInfo() {
      return _buildId != null
          ? Optional.of(new BuildIdInfo(_libraryName, _arch, _buildId))
          : Optional.empty();
    }

    // No need to handle these elements for BuildIdInfo
    @Override
    public void processSectionHeaders(ElfSectionHeaders sectionHeaders) {}

    @Override
    public void processArmVersion(String armVersion) {}

    @Override
    public void startProcessingSymbols() {}

    @Override
    public void processElfSymbols(List<ElfSymbol> elfSymbols) {}

    @Override
    public void processDebugInfoCompilationUnit(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries) {}

    @Override
    public void endProcessingSymbols() {}
  }

  private static String getBuildIdBytesToString(byte[] buildIdBytes) {
    final StringBuilder sb = new StringBuilder();
    for (byte b : buildIdBytes) {
      sb.append(String.format("%02x", b & 0xff));
    }
    return sb.toString();
  }
}
