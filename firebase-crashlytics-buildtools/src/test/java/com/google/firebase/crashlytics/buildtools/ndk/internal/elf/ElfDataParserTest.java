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

import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DebugLineEntry;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRanges;
import com.google.firebase.crashlytics.buildtools.test.TestFile;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ElfDataParserTest {

  @Rule
  public ElfDataFileResource _fileResource =
      new ElfDataFileResource(ElfDataParserTest.class.getClassLoader());

  /* All expected values used in this test are taken from
   * running the "readelf" Linux utility on the given file. */

  private static final String ELF_SUBDIR = "testelf";
  private static final String ARCH_X86 = "x86";
  private static final String ARCH_ARM5 = "armeabi";
  private static final String ARCH_ARM7 = "armeabi-v7a";
  private static final String ELF_FILE = "libtestelf.so";
  private static final String EXPECTED_SHA = "daad109b0b69ef03dd9cf6b80503f0ba5fb0cff9";
  private static final String EXPECTED_JNI_SYMBOL =
      "Java_io_crash_crashdroid_NativeCrasher_return42";
  private static final String EXPECTED_NATIVE_SYMBOL = "return42";

  @Test(expected = IllegalArgumentException.class)
  public void testParseInvalidFile() throws URISyntaxException, IOException {
    File file =
        new File(getClass().getClassLoader().getResource("configured_gradle.properties").toURI());
    ElfDataParser.parse(file, new TestContentHandler(), false);
  }

  @Test
  @TestFile(ELF_SUBDIR + "/" + ARCH_X86 + "/" + ELF_FILE)
  public void testParseElfFile() throws Exception {
    File elfFile = _fileResource.getFile();
    assertSHA(elfFile);

    final TestContentHandler testHandler = new TestContentHandler();
    ElfDataParser.parse(elfFile, testHandler, false);

    ElfFileHeader elfFileHeader = testHandler.fileHeader;
    assertElfFileHeader(elfFileHeader);
    Assert.assertEquals(elfFileHeader.eShnum, testHandler.sectionHeaders.getList().size());
    assertSymbols(testHandler.elfSymbols);
    Assert.assertNotNull(testHandler.debugLineEntries);
    Assert.assertFalse(testHandler.debugLineEntries.isEmpty());
  }

  @Test
  @TestFile(ELF_SUBDIR + "/" + ARCH_X86 + "/" + ELF_FILE)
  public void testGetArmVersionNonArm() throws Exception {
    File elfFile = _fileResource.getFile();

    final TestContentHandler testHandler = new TestContentHandler();
    ElfDataParser.parse(elfFile, testHandler, false);

    Assert.assertNull(testHandler.armVersion);
  }

  @Test
  @TestFile(ELF_SUBDIR + "/" + ARCH_X86 + "/" + ELF_FILE)
  public void testGetBuildId() throws Exception {
    File elfFile = _fileResource.getFile();

    final TestContentHandler testHandler = new TestContentHandler();
    ElfDataParser.parse(elfFile, testHandler, false);

    Assert.assertNotNull(testHandler.buildId);
  }

  @Test
  @TestFile(ELF_SUBDIR + "/" + ARCH_ARM5 + "/" + ELF_FILE)
  public void testGetArmVersion5() throws Exception {
    File elfFile = _fileResource.getFile();

    final TestContentHandler testHandler = new TestContentHandler();
    ElfDataParser.parse(elfFile, testHandler, false);

    Assert.assertNotNull(testHandler.armVersion);
    Assert.assertEquals("5TE", testHandler.armVersion);
  }

  @Test
  @TestFile(ELF_SUBDIR + "/" + ARCH_ARM7 + "/" + ELF_FILE)
  public void testGetArmVersion7() throws Exception {
    File elfFile = _fileResource.getFile();

    final TestContentHandler testHandler = new TestContentHandler();
    ElfDataParser.parse(elfFile, testHandler, false);

    Assert.assertNotNull(testHandler.armVersion);
    Assert.assertEquals("7", testHandler.armVersion);
  }

  private void assertSHA(File elfFile) throws IOException {
    // Verify that we're reading the right file.
    InputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(elfFile));
      Assert.assertEquals(EXPECTED_SHA, DigestUtils.shaHex(is));
    } finally {
      if (is != null) {
        is.close();
      }
    }
  }

  private void assertElfFileHeader(ElfFileHeader hdr) {
    ElfFileIdent ident = hdr.getElfFileIdent();
    Assert.assertEquals(ElfFileIdent.ELFCLASS32, ident.getElfClass());
    Assert.assertEquals(ElfFileIdent.ELFDATA2LSB, ident.getDataEncoding());
    Assert.assertEquals(1, ident.getElfVersion());
    Assert.assertEquals(0, ident.getOSABI());
    Assert.assertEquals(0, ident.getABIVersion());

    Assert.assertEquals(3, hdr.eType);
    Assert.assertEquals(3, hdr.eMachine);
    Assert.assertEquals(1, hdr.eVersion);
    Assert.assertEquals(0, hdr.eEntry);
    Assert.assertEquals(52, hdr.ePhoff);
    Assert.assertEquals(24052, hdr.eShoff);
    Assert.assertEquals(0, hdr.eFlags);
    Assert.assertEquals(52, hdr.eEhsize);
    Assert.assertEquals(32, hdr.ePhentsize);
    Assert.assertEquals(7, hdr.ePhnum);
    Assert.assertEquals(40, hdr.eShentsize);
    Assert.assertEquals(31, hdr.eShnum);
    Assert.assertEquals(30, hdr.eShstrndx);
  }

  private void assertSymbols(List<ElfSymbol> symbols) {
    Assert.assertEquals(19, symbols.size());
    Assert.assertEquals(EXPECTED_NATIVE_SYMBOL, symbols.get(14).stNameString);
    Assert.assertEquals(EXPECTED_JNI_SYMBOL, symbols.get(15).stNameString);
  }

  private static final class TestContentHandler implements ElfDataParser.ContentHandler {

    public ElfFileHeader fileHeader;
    public ElfSectionHeaders sectionHeaders;
    public List<ElfSymbol> elfSymbols;
    public List<DebugLineEntry> debugLineEntries;
    public byte[] buildId;
    public String armVersion;

    @Override
    public void processElfHeader(ElfFileHeader fileHeader) {
      this.fileHeader = fileHeader;
    }

    @Override
    public void processSectionHeaders(ElfSectionHeaders sectionHeaders) {
      this.sectionHeaders = sectionHeaders;
    }

    @Override
    public void processBuildId(byte[] buildId) {
      this.buildId = buildId;
    }

    @Override
    public void processArmVersion(String armVersion) {
      this.armVersion = armVersion;
    }

    @Override
    public void startProcessingSymbols() {}

    @Override
    public void processElfSymbols(List<ElfSymbol> elfSymbols) {
      this.elfSymbols = elfSymbols;
    }

    @Override
    public void processDebugInfoCompilationUnit(
        NamedRanges namedRanges, List<DebugLineEntry> debugLineEntries) {
      this.debugLineEntries = debugLineEntries;
    }

    @Override
    public void endProcessingSymbols() {}
  }
}
