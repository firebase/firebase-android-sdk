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

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DebugLineEntry;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DwarfDataParser;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRanges;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import com.google.firebase.crashlytics.buildtools.utils.io.RandomAccessFileInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ElfDataParser {

  public interface ContentHandler {
    void processElfHeader(ElfFileHeader fileHeader);

    void processSectionHeaders(ElfSectionHeaders sectionHeaders);

    void processBuildId(byte[] buildId);

    void processArmVersion(String armVersion);

    void startProcessingSymbols();

    void processElfSymbols(List<ElfSymbol> elfSymbols);

    void processDebugInfoCompilationUnit(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries);

    void endProcessingSymbols();
  }

  public static void parse(File input, ContentHandler handler, boolean featureUseDebug)
      throws IOException {
    ByteReader reader = null;
    try {
      reader = new ByteReader(new RandomAccessFileInputStream(input));
      new ElfDataParser(reader).parse(handler, featureUseDebug);
    } finally {
      if (reader != null) {
        reader.close();
      }
    }
  }

  private static final String[] ARM_ARCH = {
    "Pre-v4", "4", "4T", "5T", "5TE", "5TEJ", "6", "6KZ", "6T2", "6K", "7", "6-M", "6S-M", "7E-M",
    "8"
  };
  private static final int SHT_ARM_ATTRIBUTES = 0x70000003;
  private static final String ARM_ATTR_PUBLIC_SECTION = "aeabi";
  private static final int ARM_ATTR_TAG_FILE_ATTRIBUTES = 1;
  private static final String ELF_NOTE_GNU = "GNU";
  private static final long NT_GNU_BUILD_ID = 3;

  private final ByteReader _fileReader;
  private ByteOrder _byteOrder;
  private int _wordSize;

  /**
   * Create a new ElfDataParser.
   *
   * @param reader a FileByteReader pointing at an ELF file.
   */
  public ElfDataParser(ByteReader reader) {
    _fileReader = reader;
  }

  /**
   * Get an ElfData object from the data read from this parser's FileByteReader.
   *
   * @return a valid ElfData object containing the data read from the file referenced by this parser's FileByteReader.
   * @throws IOException if there is a problem reading the file.
   */
  public void parse(ContentHandler handler) throws IOException {
    parse(handler, false);
  }

  /**
   * Get an ElfData object from the data read from this parser's FileByteReader.
   *
   * @param featureUseDebug feature switch determining whether or not to include DWARF Debugging Info Entries
   * @return a valid ElfData object containing the data read from the file referenced by this parser's FileByteReader.
   * @throws IOException if there is a problem reading the file.
   */
  public void parse(ContentHandler handler, boolean featureUseDebug) throws IOException {
    final ElfFileIdent elfFileIdent = initializeReader();

    if (handler == null) {
      handler = new NullContentHandler();
    }

    parseElf(elfFileIdent, handler, featureUseDebug);
  }

  private ElfFileIdent initializeReader() throws IOException {
    final ElfFileIdent elfFileIdent = readElfFileIdent(_fileReader);
    if (!elfFileIdent.isElf()) {
      throw new IllegalArgumentException("Input is not a valid ELF file.");
    }

    _byteOrder =
        elfFileIdent.getDataEncoding() == ElfFileIdent.ELFDATA2MSB
            ? ByteOrder.BIG_ENDIAN
            : ByteOrder.LITTLE_ENDIAN;
    _wordSize = (elfFileIdent.getElfClass() == ElfFileIdent.ELFCLASS64) ? 8 : 4;
    _fileReader.setByteOrder(_byteOrder);

    return elfFileIdent;
  }

  private void parseElf(ElfFileIdent ident, ContentHandler handler, boolean featureUseDebug)
      throws IOException {

    final ElfFileHeader header = readElfFileHeader(_fileReader, ident, _wordSize);
    handler.processElfHeader(header);

    final ElfSectionHeaders sectionHeaders = readElfSectionHeaders(_fileReader, header, _wordSize);
    handler.processSectionHeaders(sectionHeaders);

    final Optional<byte[]> buildId = getBuildId(sectionHeaders);
    if (!buildId.isPresent()) {
      // If a build ID cannot be generated, this isn't a valid binary.
      Buildtools.logD("Crashlytics could not find a build ID.");
      return;
    }

    handler.processBuildId(buildId.get());

    // Read the ARM version data from the architecture-specific section if this is an ARM
    // executable.
    final Optional<String> armVersion = readArmVersion(header, sectionHeaders);
    if (armVersion.isPresent()) {
      handler.processArmVersion(armVersion.get());
    }

    handler.startProcessingSymbols();

    final boolean hasDebugInfo = sectionHeaders.hasDebugInfo();
    if (!(featureUseDebug && hasDebugInfo)) {
      final List<ElfSymbol> elfSymbols = readElfSymbols(sectionHeaders, ident.getElfClass());
      handler.processElfSymbols(elfSymbols);
    }

    final Optional<DebugElfSectionHeaders> debugHeaders =
        DebugElfSectionHeaders.from(sectionHeaders);

    if (debugHeaders.isPresent()) {
      new DwarfDataParser(_fileReader, _byteOrder, debugHeaders.get(), featureUseDebug)
          .parse(handler);
    }

    handler.endProcessingSymbols();
  }

  // Gets the build ID used by Breakpad to identify a particular library.
  // If the .note.gnu.build-id section is available, uses that, otherwise falls back to
  // a hash of the .text section. Both of these identifiers remain unchanged when the binary
  // is stripped.
  public Optional<byte[]> getBuildId(ElfSectionHeaders sectionHeaders) throws IOException {

    Optional<byte[]> identifier = getGnuBuildId(sectionHeaders);
    if (!identifier.isPresent()) {
      identifier = getTextSectionHash(sectionHeaders);
    }

    return identifier;
  }

  private Optional<byte[]> getGnuBuildId(ElfSectionHeaders sectionHeaders) throws IOException {
    return readGnuBuildIdNote(sectionHeaders)
        .transform(
            new Function<ElfNote, byte[]>() {
              @Override
              public byte[] apply(ElfNote note) {
                return note.desc;
              }
            });
  }

  private Optional<byte[]> getTextSectionHash(ElfSectionHeaders sectionHeaders) throws IOException {
    final int identifierSize = 16;

    return readTextPage(sectionHeaders, identifierSize)
        .transform(
            new Function<byte[], byte[]>() {
              @Override
              public byte[] apply(byte[] page) {
                final byte[] identifier = new byte[identifierSize];

                for (int i = 0; i < page.length; i++) {
                  identifier[i % identifier.length] ^= page[i];
                }

                return identifier;
              }
            });
  }

  private Optional<byte[]> readTextPage(ElfSectionHeaders sectionHeaders, int identifierSize)
      throws IOException {
    final Optional<ElfSectionHeader> textSectionHeader =
        sectionHeaders.findHeader(
            new Predicate<ElfSectionHeader>() {
              @Override
              public boolean apply(ElfSectionHeader header) {
                return header.shNameString.equals(".text")
                    && header.shType == ElfSectionHeader.SHT_PROGBITS;
              }
            });

    if (textSectionHeader.isPresent()) {
      final ElfSectionHeader hdr = textSectionHeader.get();
      _fileReader.seek(hdr.shOffset);
      // Read all of the .text section or one page, padding the read size to the next multiple of
      // the identifier size.
      final int readSize =
          (((int) Math.min(hdr.shSize, 4096) + identifierSize - 1) / identifierSize)
              * identifierSize;
      return Optional.of(_fileReader.readBytes(readSize));
    }

    return Optional.absent();
  }

  private Optional<ElfNote> readGnuBuildIdNote(ElfSectionHeaders sectionHeaders)
      throws IOException {

    final Optional<ElfSectionHeader> buildIdSectionHeader =
        sectionHeaders.findHeader(
            new Predicate<ElfSectionHeader>() {
              @Override
              public boolean apply(ElfSectionHeader header) {
                return header.shNameString.equals(".note.gnu.build-id")
                    && header.shType == ElfSectionHeader.SHT_NOTE;
              }
            });

    if (buildIdSectionHeader.isPresent()) {
      final ElfSectionHeader hdr = buildIdSectionHeader.get();
      final ElfNote note = readElfNote(hdr.shOffset);
      if (ELF_NOTE_GNU.equals(note.name) && NT_GNU_BUILD_ID == note.type) {
        return Optional.of(note);
      }
    }

    return Optional.absent();
  }

  private ElfNote readElfNote(long offset) throws IOException {
    _fileReader.seek(offset);
    long namesz = _fileReader.readLong(4);
    long descsz = _fileReader.readLong(4);
    long type = _fileReader.readLong(4);
    String name = _fileReader.readNullTerminatedString(Charsets.UTF_8);
    _fileReader.readBytes((int) (notePadding(namesz) - namesz)); // Skip any padding
    byte[] desc = _fileReader.readBytes((int) descsz);
    return new ElfNote(name, type, desc);
  }

  private List<ElfSymbol> readElfSymbols(ElfSectionHeaders sectionHeaders, int alignment)
      throws IOException {
    List<ElfSymbol> symbols = new LinkedList<ElfSymbol>();

    for (ElfSectionHeader sh : sectionHeaders.getList()) {
      if (sh.shType == ElfSectionHeader.SHT_SYMTAB) {
        symbols.addAll(readElfSymbols(sh, sectionHeaders, alignment));
      }
    }

    return symbols;
  }

  private List<ElfSymbol> readElfSymbols(
      final ElfSectionHeader sh, final ElfSectionHeaders sectionHeaders, final int alignment)
      throws IOException {
    Optional<ElfSectionHeader> shStrings = sectionHeaders.getHeaderByIndex(sh.shLink);

    if (!shStrings.isPresent()) {
      return Collections.emptyList();
    }

    int numSymbols = (int) sh.shSize / (int) sh.shEntSize;

    return readSymbolTable(sh.shOffset, numSymbols, shStrings.get().shOffset, alignment);
  }

  private List<ElfSymbol> readSymbolTable(
      long symTabOffset, int numSymbols, long stringsOffset, int alignment) throws IOException {
    // Read symbol metadata.
    _fileReader.seek(symTabOffset);

    List<ElfSymbol> symbols = new ArrayList<ElfSymbol>(numSymbols);

    for (int i = 0; i < numSymbols; ++i) {
      ElfSymbol sym = new ElfSymbol();
      // Alignment is different for 32 vs. 64 bit.
      switch (alignment) {
        case ElfFileIdent.ELFCLASS64:
          sym.stName = _fileReader.readInt(4);
          sym.stInfo = _fileReader.readByte();
          sym.stOther = _fileReader.readByte();
          sym.stShndx = _fileReader.readShort(2);
          sym.stValue = _fileReader.readLong(_wordSize);
          sym.stSize = _fileReader.readLong(_wordSize);
          break;
        case ElfFileIdent.ELFCLASS32:
        case ElfFileIdent.ELFCLASSNONE:
        default:
          sym.stName = _fileReader.readInt(4);
          sym.stValue = _fileReader.readLong(_wordSize);
          sym.stSize = _fileReader.readLong(_wordSize);
          sym.stInfo = _fileReader.readByte();
          sym.stOther = _fileReader.readByte();
          sym.stShndx = _fileReader.readShort(2);
      }
      symbols.add(sym);
    }

    // Read symbol names
    _fileReader.seek(stringsOffset);
    for (ElfSymbol symbol : symbols) {
      _fileReader.seek(stringsOffset + symbol.stName);
      symbol.stNameString = _fileReader.readNullTerminatedString(Charsets.UTF_8);
    }

    return symbols;
  }

  private Optional<String> readArmVersion(ElfFileHeader header, ElfSectionHeaders sectionHeaders)
      throws IOException {
    Optional<String> armVersion = Optional.absent();
    if (header.eMachine == EMachine.EM_ARM) {
      Optional<ElfSectionHeader> armAttrSection =
          sectionHeaders.findHeader(
              new Predicate<ElfSectionHeader>() {
                @Override
                public boolean apply(ElfSectionHeader header) {
                  return header.shType == SHT_ARM_ATTRIBUTES;
                }
              });

      if (armAttrSection.isPresent()) {
        ElfSectionHeader hdr = armAttrSection.get();
        armVersion = readArmVersion(hdr.shOffset, hdr.shSize);
      }
    }
    return armVersion;
  }

  /**
   * Read version data from the .ARM.attributes section of ELF files targeting ARM architectures.
   * Specification for reading the .ARM.attributes section can be found here:
   * http://infocenter.arm.com/help/topic/com.arm.doc.ihi0044e/IHI0044E_aaelf.pdf
   *
   * @param dataOffset the offset within the file of the .ARM.attributes section data.
   * @param dataSize   the size (in bytes) within the file of the .ARM.attributes section.
   * @return An optional value containing the ARM version string if it is available.
   * @throws IOException if there is a problem reading the section data.
   */
  private Optional<String> readArmVersion(long dataOffset, long dataSize) throws IOException {
    _fileReader.seek(dataOffset);

    byte version = _fileReader.readByte();
    if (version != 'A') {
      throw new IllegalArgumentException(
          String.format("Invalid data found at offset %d.", dataOffset));
    }

    long dataRemaining = (dataSize - 1);

    long sectionRemaining;
    while (dataRemaining > 0) {
      sectionRemaining = _fileReader.readInt(4);

      if (sectionRemaining > dataRemaining) {
        String errorString = "Section size %d is greater than remaining data length %d.";
        throw new IOException(String.format(errorString, sectionRemaining, dataRemaining));
      }

      dataRemaining -= sectionRemaining;
      sectionRemaining -= 4;

      String sectionName = _fileReader.readNullTerminatedString(Charsets.UTF_8);
      sectionRemaining -= (sectionName.length() - 1);

      if (sectionName.equals(ARM_ATTR_PUBLIC_SECTION)) {
        return findArmVersionInSection(_fileReader, sectionRemaining);
      } else {
        // Skip to next section
        _fileReader.seek(_fileReader.getCurrentOffset() + sectionRemaining);
      }
    }

    Buildtools.logD("Crashlytics did not find an ARM public attributes subsection.");
    return Optional.absent();
  }

  private Optional<String> findArmVersionInSection(ByteReader dataReader, long sectionRemaining)
      throws IOException {
    while (sectionRemaining > 0) {
      byte tag = dataReader.readByte();
      long subSectionRemaining = dataReader.readInt(4);

      if (subSectionRemaining > sectionRemaining) {
        String errorString = "Subsection size %d is greater than parent section size %d.";
        throw new IOException(String.format(errorString, subSectionRemaining, sectionRemaining));
      }
      sectionRemaining -= subSectionRemaining;
      subSectionRemaining -= 5;
      if (tag == ARM_ATTR_TAG_FILE_ATTRIBUTES) {
        return findArmVersionInSubSection(dataReader, subSectionRemaining);
      } else {
        // Skip section
        dataReader.seek(dataReader.getCurrentOffset() + subSectionRemaining);
      }
    }
    Buildtools.logD("Crashlytics did not find an ARM file attributes subsection.");
    return Optional.absent();
  }

  private Optional<String> findArmVersionInSubSection(
      ByteReader dataReader, long subSectionRemaining) throws IOException {
    long nextSubSection = dataReader.getCurrentOffset() + subSectionRemaining;
    // Tag definitions can be found in section 2.5 of
    // http://infocenter.arm.com/help/topic/com.arm.doc.ihi0045d/IHI0045D_ABI_addenda.pdf
    while (dataReader.getCurrentOffset() < nextSubSection) {
      int attrTag = dataReader.readULEB128();
      int attrVal;
      switch (attrTag) {
        case 4:
        case 5:
        case 32:
        case 65:
        case 67:
          dataReader.readNullTerminatedString(Charsets.UTF_8);
          break;
        case 6: // Tag_CPU_arch
          attrVal = dataReader.readULEB128();
          return Optional.of(ARM_ARCH[attrVal]);
        default:
          dataReader.readULEB128();
      }
    }
    Buildtools.logD("Crashlytics did not find an ARM architecture field.");
    return Optional.absent();
  }

  private static ElfFileIdent readElfFileIdent(ByteReader reader) throws IOException {
    reader.seek(0);
    return new ElfFileIdent(reader.readBytes(ElfFileIdent.EI_NIDENT));
  }

  private static ElfFileHeader readElfFileHeader(
      ByteReader reader, ElfFileIdent identity, int wordSize) throws IOException {
    reader.seek(ElfFileIdent.EI_NIDENT);

    ElfFileHeader header = new ElfFileHeader(identity);
    header.eType = reader.readInt(2);
    header.eMachine = reader.readInt(2);
    header.eVersion = reader.readLong(4);
    header.eEntry = reader.readLong(wordSize);
    header.ePhoff = reader.readLong(wordSize);
    header.eShoff = reader.readLong(wordSize);
    header.eFlags = reader.readLong(4);
    header.eEhsize = reader.readInt(2);
    header.ePhentsize = reader.readInt(2);
    header.ePhnum = reader.readInt(2);
    header.eShentsize = reader.readInt(2);
    header.eShnum = reader.readInt(2);
    header.eShstrndx = reader.readInt(2);
    return header;
  }

  private static ElfSectionHeaders readElfSectionHeaders(
      ByteReader reader, ElfFileHeader header, int wordSize) throws IOException {
    reader.seek(header.eShoff);

    List<ElfSectionHeader> sectionHeaders = Lists.newArrayListWithCapacity(header.eShnum);

    for (int i = 0; i < header.eShnum; ++i) {
      ElfSectionHeader sh = new ElfSectionHeader();
      sh.shName = reader.readInt(4);
      sh.shType = reader.readInt(4);
      sh.shFlags = reader.readLong(wordSize);
      sh.shAddr = reader.readLong(wordSize);
      sh.shOffset = reader.readLong(wordSize);
      sh.shSize = reader.readLong(wordSize);
      sh.shLink = reader.readInt(4);
      sh.shInfo = reader.readInt(4);
      sh.shAddrAlign = reader.readLong(wordSize);
      sh.shEntSize = reader.readLong(wordSize);
      sectionHeaders.add(sh);
    }

    ElfSectionHeader names = sectionHeaders.get(header.eShstrndx);

    reader.seek(names.shOffset);

    for (ElfSectionHeader sectionHeader : sectionHeaders) {
      reader.seek(names.shOffset + sectionHeader.shName);
      sectionHeader.shNameString = reader.readNullTerminatedString(Charsets.UTF_8);
    }

    return new ElfSectionHeaders(sectionHeaders);
  }

  // Note data is 32-bit padded.
  // See Section 2-5 of ELF spec 1.2.
  private static long notePadding(long size) {
    return (size + 3) & ~3;
  }

  private static class ElfNote {
    public final String name;
    public final long type;
    public final byte[] desc;

    public ElfNote(String name, long type, byte[] desc) {
      this.name = name;
      this.type = type;
      this.desc = desc;
    }
  }

  /**
   * No-op content handler for when a null content handler is passed into the parser.
   */
  private static final class NullContentHandler implements ContentHandler {

    @Override
    public void processElfHeader(ElfFileHeader fileHeader) {}

    @Override
    public void processSectionHeaders(ElfSectionHeaders sectionHeaders) {}

    @Override
    public void processBuildId(byte[] buildId) {}

    @Override
    public void startProcessingSymbols() {}

    @Override
    public void processElfSymbols(List<ElfSymbol> elfSymbols) {}

    @Override
    public void processArmVersion(String armVersion) {}

    @Override
    public void processDebugInfoCompilationUnit(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries) {}

    @Override
    public void endProcessingSymbols() {}
  }
}
