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

import com.google.firebase.crashlytics.buildtools.TestUtils;
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.CSym.Range;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ElfCSymFactoryTest {

  private static final String RES_ELF_SUBDIR = "testelf";
  private static final String ARCH_ARM5 = "armeabi";
  private static final String ARCH_ARM7 = "armeabi-v7a";
  private static final String ARCH_ARM8 = "arm64-v8a";
  private static final String ARCH_X86 = "x86";
  private static final String ARCH_X86_64 = "x86_64";

  private static final String ELF_FILE = "libtestelf.so";
  private static final String ELF_FILE_STRIPPED_ALL = "libtestelf-stripped-all.so";
  private static final String ELF_FILE_STRIPPED_DEBUG = "libtestelf-stripped-debug.so";
  private static final String ELF_FILE_DWARF4 = "libtestelf-dwarf4.so";
  private static final String DWARF_FILE_WITH_INLINES = "libhello-jni-with-inlines.so";

  private static final String TYPE_ELF = "elf_symtab";
  private static final String TYPE_DWARF = "dwarf_debug";

  private static final String CSYM_ARCH_X86 = ARCH_X86;
  private static final String CSYM_ARCH_X86_64 = ARCH_X86_64;
  private static final String CSYM_ARCH_ARM5 = "armv5TE";
  private static final String CSYM_ARCH_ARM7 = "armv7";
  private static final String CSYM_ARCH_ARM8 = "aarch64";

  private static final File WORKING_DIR = new File(TestUtils.TEST_OUTPUT_DIR, "cSymTests");

  @Before
  public void setUp() throws Exception {
    TestUtils.prepareTestDirectory();
    FileUtils.verifyDirectory(WORKING_DIR);
  }

  @Test
  public void testGenerateCSymFromX86() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86, ELF_FILE);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86, true);
  }

  @Test
  public void testGenerateCSymFromX86_withDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86, true);
  }

  @Test
  public void testGenerateCSymFromX86_withAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86, false);
  }

  @Test
  public void testGenerateCSymFromX86_withDwarf() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86, ELF_FILE);
    assertCSym(cSymArm5, TYPE_DWARF, CSYM_ARCH_X86, true);
  }

  @Test
  public void testGenerateCSymFromX86_withDwarf4() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86, ELF_FILE_DWARF4);
    assertCSym(cSymArm5, TYPE_DWARF, CSYM_ARCH_X86, true);
  }

  @Test
  public void testGenerateCSymFromX86_withDwarfAndDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86, true);
  }

  @Test
  public void testGenerateCSymFromX86_withDwarfAndAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86, false);
  }

  @Test
  public void testGenerateCSymFromX8664() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86_64, ELF_FILE);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86_64, true);
  }

  @Test
  public void testGenerateCSymFromX8664_withDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86_64, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86_64, true);
  }

  @Test
  public void testGenerateCSymFromX8664_withAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_X86_64, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86_64, false);
  }

  @Test
  public void testGenerateCSymFromX8664_withDwarf() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86_64, ELF_FILE);
    assertCSym(cSymArm5, TYPE_DWARF, CSYM_ARCH_X86_64, true);
  }

  @Test
  public void testGenerateCSymFromX8664_withDwarf4() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86_64, ELF_FILE_DWARF4);
    assertCSym(cSymArm5, TYPE_DWARF, CSYM_ARCH_X86_64, true);
  }

  @Test
  public void testGenerateCSymFromX8664_withDwarfAndDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86_64, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86_64, true);
  }

  @Test
  public void testGenerateCSymFromX8664_withDwarfAndAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_X86_64, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_X86_64, false);
  }

  @Test
  public void testGenerateCSymFromArm5() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_ARM5, ELF_FILE);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_ARM5, true);
  }

  @Test
  public void testGenerateCSymFromArm5_withDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_ARM5, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_ARM5, true);
  }

  @Test
  public void testGenerateCSymFromArm5_withAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSym(ARCH_ARM5, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_ARM5, false);
  }

  @Test
  public void testGenerateCSymFromArm5_withDwarf() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_ARM5, ELF_FILE);
    assertCSym(cSymArm5, TYPE_DWARF, CSYM_ARCH_ARM5, true);
  }

  @Test
  public void testGenerateCSymFromFromArm5_withDwarfAndDebugStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_ARM5, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_ARM5, true);
  }

  @Test
  public void testGenerateCSymFromArm5_withDwarfAndAllStrippedFile() throws Exception {
    final CSym cSymArm5 = generateCSymWithDwarf(ARCH_ARM5, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm5, TYPE_ELF, CSYM_ARCH_ARM5, false);
  }

  @Test
  public void testGenerateCSymFromArm7() throws Exception {
    final CSym cSymArm7 = generateCSym(ARCH_ARM7, ELF_FILE);
    assertCSym(cSymArm7, TYPE_ELF, CSYM_ARCH_ARM7, true);
  }

  @Test
  public void testGenerateCSymFromArm7_withDebugStrippedFile() throws Exception {
    final CSym cSymArm7 = generateCSym(ARCH_ARM7, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm7, TYPE_ELF, CSYM_ARCH_ARM7, true);
  }

  @Test
  public void testGenerateCSymFromArm7_withAllStrippedFile() throws Exception {
    final CSym cSymArm7 = generateCSym(ARCH_ARM7, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm7, TYPE_ELF, CSYM_ARCH_ARM7, false);
  }

  @Test
  public void testGenerateCSymFromArm7_withDwarf() throws Exception {
    final CSym cSymArm7 = generateCSymWithDwarf(ARCH_ARM7, ELF_FILE);
    assertCSym(cSymArm7, TYPE_DWARF, CSYM_ARCH_ARM7, true);
  }

  @Test
  public void testGenerateCSymFromArm7_withDwarf4() throws Exception {
    final CSym cSymArm7 = generateCSymWithDwarf(ARCH_ARM7, ELF_FILE_DWARF4);
    assertCSym(cSymArm7, TYPE_DWARF, CSYM_ARCH_ARM7, true);
  }

  @Test
  public void testGenerateCSymFromFromArm7_withDwarfAndDebugStrippedFile() throws Exception {
    final CSym cSymArm7 = generateCSymWithDwarf(ARCH_ARM7, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm7, TYPE_ELF, CSYM_ARCH_ARM7, true);
  }

  @Test
  public void testGenerateCSymFromArm7_withDwarfAndAllStrippedFile() throws Exception {
    final CSym cSymArm7 = generateCSymWithDwarf(ARCH_ARM7, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm7, TYPE_ELF, CSYM_ARCH_ARM7, false);
  }

  @Test
  public void testGenerateCSymFromArm8() throws Exception {
    final CSym cSymArm8 = generateCSym(ARCH_ARM8, ELF_FILE);
    assertCSym(cSymArm8, TYPE_ELF, CSYM_ARCH_ARM8, true);
  }

  @Test
  public void testGenerateCSymFromArm8_withDebugStrippedFile() throws Exception {
    final CSym cSymArm8 = generateCSym(ARCH_ARM8, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm8, TYPE_ELF, CSYM_ARCH_ARM8, true);
  }

  @Test
  public void testGenerateCSymFromArm8_withAllStrippedFile() throws Exception {
    final CSym cSymArm8 = generateCSym(ARCH_ARM8, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm8, TYPE_ELF, CSYM_ARCH_ARM8, false);
  }

  @Test
  public void testGenerateCSymFromArm8_withDwarf() throws Exception {
    final CSym cSymArm8 = generateCSymWithDwarf(ARCH_ARM8, ELF_FILE);
    assertCSym(cSymArm8, TYPE_DWARF, CSYM_ARCH_ARM8, true);
  }

  @Test
  public void testGenerateCSymFromArm8_withDwarf4() throws Exception {
    final CSym cSymArm8 = generateCSymWithDwarf(ARCH_ARM8, ELF_FILE_DWARF4);
    assertCSym(cSymArm8, TYPE_DWARF, CSYM_ARCH_ARM8, true);
  }

  @Test
  public void testGenerateCSymFromFromArm8_withDwarfAndDebugStrippedFile() throws Exception {
    final CSym cSymArm8 = generateCSymWithDwarf(ARCH_ARM8, ELF_FILE_STRIPPED_DEBUG);
    assertCSym(cSymArm8, TYPE_ELF, CSYM_ARCH_ARM8, true);
  }

  @Test
  public void testGenerateCSymFromArm8_withDwarfAndAllStrippedFile() throws Exception {
    final CSym cSymArm8 = generateCSymWithDwarf(ARCH_ARM8, ELF_FILE_STRIPPED_ALL);
    assertCSym(cSymArm8, TYPE_ELF, CSYM_ARCH_ARM8, false);
  }

  private CSym generateCSymWithDwarf(String archDir, String file) throws Exception {
    return generateCSym(archDir, file, true);
  }

  private CSym generateCSym(String archDir, String file) throws Exception {
    return generateCSym(archDir, file, false);
  }

  private CSym generateCSym(String archDir, String file, boolean useDebug) throws Exception {
    final File elfFile = new File(WORKING_DIR, file);
    generateElfFileFromResources(archDir, file, elfFile);
    final ElfCSymFactory gen = new ElfCSymFactory(useDebug);
    return gen.createCSymFromFile(elfFile);
  }

  private void assertCSym(
      CSym cSym, String expectedType, String expectedArch, boolean expectedHasSymbols) {
    Assert.assertNotNull(cSym.getUUID());
    Assert.assertEquals(expectedType, cSym.getType());
    Assert.assertEquals(expectedArch, cSym.getArchitecture());
    Assert.assertEquals(expectedHasSymbols, !cSym.getSymbols().isEmpty());
  }

  private void generateElfFileFromResources(String arch, String fileName, File dest)
      throws Exception {
    String res = String.format("%s/%s/%s", RES_ELF_SUBDIR, arch, fileName);
    TestUtils.createFileFromResource(res, dest);
  }

  // --- Tests for specific DWARF features ---

  /*
   * The .so under test was compiled from an AOSP's "Hello-JNI" example source, with
   * additional functions that were explicitly inlined.
   * The source file can be found locally at common/src/test/resources/testelf/hello-jni.c
   *
   * There are 4 functions we care about:
   *   MAIN: the enclosing function that calls inlined methods.
   *   SINGLELINE: A one-line function that is inlined.
   *   MULTILINE: A multiple-line function that is inlined.
   *   NESTED: An inlined function that is called from MAIN, which then calls MULTILINE.
   */

  private static final String SYMBOL_MAIN = "Java_com_example_hellojni_HelloJni_stringFromJNI";

  private static final String SYMBOL_SINGLELINE = "inlinedSingleLineFunction";
  private static final long LINENUMBER_SINGLELINE_START = 63L;
  private static final long LINENUMBER_SINGLELINE_END = LINENUMBER_SINGLELINE_START;
  private static final long LINENUMBER_SINGLELINE_CALL_FROM_MAIN = 95L;

  private static final String SYMBOL_MULTILINE = "inlinedMultiLineFunction";
  private static final long LINENUMBER_MULTILINE_START = 68L;
  private static final long LINENUMBER_MULTILINE_END = 70L;
  private static final long LINENUMBER_MULTILINE_CALL_FROM_MAIN = 97L;

  private static final String SYMBOL_NESTED = "inlinedFunctionWithNestedInlinedCall";
  private static final long LINENUMBER_NESTED_START = 75L;
  private static final long LINENUMBER_NESTED_END = 77L;
  private static final long LINENUMBER_NESTED_CALL_FROM_MAIN = 99L;
  private static final long LINENUMBER_MULTILINE_CALL_FROM_NESTED = 76L;

  /*
   * Tests for inlining.
   * From manual inspection of the .c file, we know the symbol names and line numbers
   * of the inlined functions and where they are called from.
   */

  @Test
  public void testGenerateCSymInlines() throws Exception {
    validateInline(ARCH_X86_64);
    validateInline(ARCH_X86);
    validateInline(ARCH_ARM7);
    validateInline(ARCH_ARM8);
  }

  private void validateInline(final String arch) throws Exception {
    final CSym cSymX8664 = generateCSymWithDwarf(arch, DWARF_FILE_WITH_INLINES);
    List<Range> ranges = cSymX8664.getRanges();

    InlineValidator.Builder validatorBuilder = InlineValidator.Builder.newInstance();
    validatorBuilder
        .setEnclosingSubprogramSymbol(SYMBOL_MAIN)
        .setLineNumberForInlineCall(LINENUMBER_SINGLELINE_CALL_FROM_MAIN)
        .setInlinedSubprogramSymbol(SYMBOL_SINGLELINE)
        .setInlinedSubprogramStartLine(LINENUMBER_SINGLELINE_START)
        .setInlinedSubprogramEndLine(LINENUMBER_SINGLELINE_END);
    validatorBuilder.build().validateRanges(ranges);

    validatorBuilder = InlineValidator.Builder.newInstance();
    validatorBuilder
        .setEnclosingSubprogramSymbol(SYMBOL_MAIN)
        .setLineNumberForInlineCall(LINENUMBER_MULTILINE_CALL_FROM_MAIN)
        .setInlinedSubprogramSymbol(SYMBOL_MULTILINE)
        .setInlinedSubprogramStartLine(LINENUMBER_MULTILINE_START)
        .setInlinedSubprogramEndLine(LINENUMBER_MULTILINE_END);
    validatorBuilder.build().validateRanges(ranges);

    // Nested inlined method -- validate the outer call
    validatorBuilder = InlineValidator.Builder.newInstance();
    validatorBuilder
        .setEnclosingSubprogramSymbol(SYMBOL_MAIN)
        .setLineNumberForInlineCall(LINENUMBER_NESTED_CALL_FROM_MAIN)
        .setInlinedSubprogramSymbol(SYMBOL_NESTED)
        .setInlinedSubprogramStartLine(LINENUMBER_NESTED_START)
        .setInlinedSubprogramEndLine(LINENUMBER_NESTED_END);
    validatorBuilder.build().validateRanges(ranges);

    // Nested inlined method -- validate the inner call to inlinedMultiLineFunction()
    validatorBuilder = InlineValidator.Builder.newInstance();
    validatorBuilder
        .setEnclosingSubprogramSymbol(SYMBOL_NESTED)
        .setLineNumberForInlineCall(LINENUMBER_MULTILINE_CALL_FROM_NESTED)
        .setInlinedSubprogramSymbol(SYMBOL_MULTILINE)
        .setInlinedSubprogramStartLine(LINENUMBER_MULTILINE_START)
        .setInlinedSubprogramEndLine(LINENUMBER_MULTILINE_END);
    validatorBuilder.build().validateRanges(ranges);
  }

  private static class InlineValidator {

    // Symbol of the subprogram that contains the inlined call
    private final String enclosingSubprogramSymbol;

    // Line at which the call to the inlined method occurs
    private final long lineNumberForInlineCall;

    // Name of the function that was inlined
    private final String inlinedSubprogramSymbol;

    // The start & end line numbers in the source code that define the inlined method,
    // not inclusive of signature & brackets.
    private final long inlinedSubprogramStartLine;
    private final long inlinedSubprogramEndLine;

    public InlineValidator(Builder builder) {
      enclosingSubprogramSymbol = builder.enclosingSubprogramSymbol;
      lineNumberForInlineCall = builder.lineNumberForInlineCall;
      inlinedSubprogramSymbol = builder.inlinedSubprogramSymbol;
      inlinedSubprogramStartLine = builder.inlinedSubprogramStartLine;
      inlinedSubprogramEndLine = builder.inlinedSubprogramEndLine;
    }

    public static class Builder {

      private String enclosingSubprogramSymbol;
      private long lineNumberForInlineCall;
      private String inlinedSubprogramSymbol;
      private long inlinedSubprogramStartLine;
      private long inlinedSubprogramEndLine;

      private Builder() {}

      public static Builder newInstance() {
        return new Builder();
      }

      public InlineValidator build() {
        return new InlineValidator(this);
      }

      public Builder setEnclosingSubprogramSymbol(String symbol) {
        this.enclosingSubprogramSymbol = symbol;
        return this;
      }

      public Builder setLineNumberForInlineCall(long line) {
        this.lineNumberForInlineCall = line;
        return this;
      }

      public Builder setInlinedSubprogramSymbol(String symbol) {
        this.inlinedSubprogramSymbol = symbol;
        return this;
      }

      public Builder setInlinedSubprogramStartLine(long line) {
        this.inlinedSubprogramStartLine = line;
        return this;
      }

      public Builder setInlinedSubprogramEndLine(long line) {
        this.inlinedSubprogramEndLine = line;
        return this;
      }
    }

    /*
     * For each inlined function, we care about 4 Ranges:
     *   1) The Range in the calling function that immediately precedes the inlined call.
     *   2) The first Range of the inlined call itself.
     *   3) The last Range of the inlined code (in trivial cases, this is the same Range as #2).
     *   4) The Range that resumes the position in the calling function.
     *
     * The tests validate that the calling subprogram is correctly flattened and split into
     * two ranges (1 & 4), with the inlined Ranges (2 & 3) properly interleaved within the
     * calling Ranges.
     */
    public void validateRanges(List<Range> unorderedRanges) {

      // Sort because we need to traverse the ranges in order, which isn't strictly enforced
      // by the csym generation algorithm.
      ArrayList<Range> ranges = new ArrayList<Range>(unorderedRanges);
      Collections.sort(
          ranges,
          new Comparator<Range>() {
            @Override
            public int compare(Range r1, Range r2) {
              return Long.valueOf(r1.offset).compareTo(r2.offset);
            }
          });

      // Validate that the ranges do NOT overlap:
      for (int i = 1; i < ranges.size(); ++i) {
        Range r1 = ranges.get(i - 1);
        Range r2 = ranges.get(i);

        Assert.assertTrue(r1.offset + r1.size <= r2.offset);
      }

      // Multiple offset ranges can exist for a single line of source. We want the *last*
      // one before the inline call is made, so we iterate backwards from the end.
      Range rangeBeforeInline = null;
      int rangeBeforeInlineNdx = -1;
      // Assumes no whitespace between the source lines:
      long lineNumberBeforeInlineCall = lineNumberForInlineCall - 1;
      for (int i = ranges.size() - 1; i >= 0; --i) {
        Range r = ranges.get(i);
        if (r.lineNumber == lineNumberBeforeInlineCall) {
          rangeBeforeInline = r;
          rangeBeforeInlineNdx = i;
          break;
        }
      }
      Assert.assertNotNull(rangeBeforeInline);
      Assert.assertEquals(enclosingSubprogramSymbol, rangeBeforeInline.symbol);

      // the inlined subprogram should match the expected line numbers / symbol:
      Range firstInline = ranges.get(rangeBeforeInlineNdx + 1);
      Assert.assertEquals(inlinedSubprogramSymbol, firstInline.symbol);
      Assert.assertEquals(inlinedSubprogramStartLine, firstInline.lineNumber);

      // Find where control is returned to the enclosing caller
      Range resumedRange = null;
      int resumedRangeNdx = -1;
      for (int i = rangeBeforeInlineNdx + 1; i < ranges.size(); ++i) {
        Range r = ranges.get(i);
        if (r.symbol.equals(enclosingSubprogramSymbol)) {
          resumedRangeNdx = i;
          resumedRange = r;
          break;
        }
      }
      Assert.assertNotNull(resumedRange);
      Range lastInlined = ranges.get(resumedRangeNdx - 1);
      Assert.assertEquals(inlinedSubprogramEndLine, lastInlined.lineNumber);
      Assert.assertEquals(inlinedSubprogramSymbol, lastInlined.symbol);

      // The program should resume to the next line after the inline call
      Assert.assertEquals(lineNumberForInlineCall + 1, resumedRange.lineNumber);
    }
  }
}
