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

import com.google.common.base.Charsets;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import org.easymock.EasyMock;
import org.easymock.EasyMockRule;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DebugLineOpcodesTest {

  @Rule public EasyMockRule easyMockRule = new EasyMockRule(this);

  private static final byte MIN_INSTRUCTION_LENGTH = 2;
  private static final byte MAX_OPS_PER_INSTRUCTION = 1;
  private static final byte LINE_BASE = 1;
  private static final byte LINE_RANGE = 14;
  private static final byte OPCODE_BASE = 13;

  private static final int ADDRESS_SIZE = 4;

  private static final String CURRENT_DIRECTORY = "";

  private DebugLineContext _context;

  @Mock private ByteReader _mockReader;

  @Before
  public void setUp() throws Exception {
    _context =
        createContext(
            MIN_INSTRUCTION_LENGTH, MAX_OPS_PER_INSTRUCTION, LINE_BASE, LINE_RANGE, OPCODE_BASE);
  }

  @Test
  public void testStandardOpcodeCopy() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeCopy();
    EasyMock.replay(_mockReader);

    Assert.assertTrue(opcode.process(_context, _mockReader));
    Assert.assertFalse(_context.reg.isBasicBlock);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeAdvancePC() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeAdvancePC();

    int pcAdvance = 64 & 0xFF;
    long expectedAddr = _context.reg.address + (pcAdvance * MIN_INSTRUCTION_LENGTH);
    EasyMock.expect(_mockReader.readULEB128()).andReturn(pcAdvance);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals("Unexpected address", expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeAdvanceLine() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeAdvanceLine();

    int lineAdvance = 7 & 0xFF;
    long expectedLine = _context.reg.line + lineAdvance;
    EasyMock.expect(_mockReader.readSLEB128()).andReturn(lineAdvance);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals("Unexpected line", expectedLine, _context.reg.line);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetFile() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetFile();

    int file = 2 & 0xFF;
    EasyMock.expect(_mockReader.readULEB128()).andReturn(file);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals("Unexpected file", file, _context.reg.file);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetColumn() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetColumn();

    int column = 2 & 0xFF;
    EasyMock.expect(_mockReader.readULEB128()).andReturn(column);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals("Unexpected column", column, _context.reg.column);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeNegateStatement() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeNegateStatement();

    boolean expectedStmt = !_context.reg.isStatement;
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedStmt, _context.reg.isStatement);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetBasicBlock() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetBasicBlock();
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertTrue(_context.reg.isBasicBlock);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeConstAddPC() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeConstAddPC();

    long expectedAddr =
        _context.reg.address
            + calculateAddressIncrement(255, OPCODE_BASE, LINE_RANGE, MIN_INSTRUCTION_LENGTH);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeFixedAdvancePC() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeFixedAdvancePC();

    int advance = 25700;
    long expectedAddr = advance + _context.reg.address;
    EasyMock.expect(_mockReader.readInt(2)).andReturn(advance);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetPrologueEnd() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetPrologueEnd();
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertTrue(_context.reg.isPrologueEnd);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetEpilogueBegin() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetEpilogueBegin();
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertTrue(_context.reg.isEpilogueBegin);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testStandardOpcodeSetIsa() throws Exception {
    DebugLineOpcode opcode = new StandardOpcodeSetIsa();

    int expectedIsa = 7;
    EasyMock.expect(_mockReader.readULEB128()).andReturn(expectedIsa);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedIsa, _context.reg.isa);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testExtendedOpcodeEndSequence() throws Exception {
    DebugLineOpcode opcode = new ExtendedOpcodeEndSequence();
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertTrue(_context.reg.isEndSequence);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testExtendedOpcodeSetAddress() throws Exception {
    DebugLineOpcode opcode = new ExtendedOpcodeSetAddress();

    long expectedAddr = 4210752;
    EasyMock.expect(_mockReader.readLong(4)).andReturn(expectedAddr);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testExtendedOpcodeDefineFile() throws Exception {
    DebugLineOpcode opcode = new ExtendedOpcodeDefineFile();

    String expectedFile = "file.ext";
    int expectedDir = 0; // Current directory.
    int expectedModTime = (int) System.currentTimeMillis() / 1000;
    int expectedFileLength = 8192;
    EasyMock.expect(_mockReader.readNullTerminatedString(Charsets.UTF_8)).andReturn(expectedFile);
    EasyMock.expect(_mockReader.readULEB128())
        .andReturn(expectedDir)
        .andReturn(expectedModTime)
        .andReturn(expectedFileLength);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    DebugLineFileInfo fileInfo =
        _context.getFileInfo(1); // Should be the first file in this context.
    Assert.assertEquals(CURRENT_DIRECTORY, fileInfo.directory);
    Assert.assertEquals(expectedModTime, fileInfo.modificationTime);
    Assert.assertEquals(expectedFileLength, fileInfo.length);
    Assert.assertEquals(expectedFile, fileInfo.name); // Should be the first file in this context.
    EasyMock.verify(_mockReader);
  }

  @Test
  public void textExtendedOpcodeSetDiscriminator() throws Exception {
    DebugLineOpcode opcode = new ExtendedOpcodeSetDiscriminator();

    int expectedDiscriminator = 3 & 0xFF;
    EasyMock.expect(_mockReader.readULEB128()).andReturn(expectedDiscriminator);
    EasyMock.replay(_mockReader);

    Assert.assertFalse(opcode.process(_context, _mockReader));
    Assert.assertEquals(expectedDiscriminator, _context.reg.discriminator);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testSpecialOpcode() throws Exception {

    long lineIncrement = 7;
    long addressIncrement = 4;

    long expectedLine = _context.reg.line + lineIncrement;
    long expectedAddr = _context.reg.address + addressIncrement;

    byte opNum =
        calculateSpecialOpcode(
            lineIncrement,
            addressIncrement,
            MIN_INSTRUCTION_LENGTH,
            MAX_OPS_PER_INSTRUCTION,
            LINE_BASE,
            LINE_RANGE,
            OPCODE_BASE);

    EasyMock.replay(_mockReader);

    DebugLineOpcode opcode = new SpecialOpcode(opNum);
    Assert.assertTrue(
        opcode.process(
            _context, _mockReader)); // This opcode does not actually read from the reader.
    Assert.assertEquals("Unexpected line number", expectedLine, _context.reg.line);
    Assert.assertEquals("Unexpected address", expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testSpecialOpcodeDwarf4() throws Exception {
    int maxOpsPerInstruction = 3;
    long lineIncrement = 7;
    long addressIncrement = 4;

    long expectedLine = _context.reg.line + lineIncrement;
    long expectedAddr = (_context.reg.address + addressIncrement) * maxOpsPerInstruction;

    byte opNum =
        calculateSpecialOpcode(
            lineIncrement,
            addressIncrement,
            MIN_INSTRUCTION_LENGTH,
            maxOpsPerInstruction,
            LINE_BASE,
            LINE_RANGE,
            OPCODE_BASE);

    EasyMock.replay(_mockReader);

    DebugLineOpcode opcode = new SpecialOpcode(opNum);
    Assert.assertTrue(
        opcode.process(
            _context, _mockReader)); // This opcode does not actually read from the reader.
    Assert.assertEquals("Unexpected line number", expectedLine, _context.reg.line);
    Assert.assertEquals("Unexpected address", expectedAddr, _context.reg.address);
    EasyMock.verify(_mockReader);
  }

  @Test
  public void testSpecialOpcodeDwarf4Matrix() throws Exception {
    // Tests some samples from DWARF Debugging Information Format, Version 4 §6.2.5.1 example
    // matrix.
    validateDwarf4SpecialOpcode(112, 0, 8);
    validateDwarf4SpecialOpcode(211, 3, 16);
    validateDwarf4SpecialOpcode(13, -3, 0);
    validateDwarf4SpecialOpcode(255, -1, 20);
  }

  private void validateDwarf4SpecialOpcode(
      int opNum, int expectedLineAdvance, int expectedOperationAdvance) throws Exception {
    byte minInstructionLength = 1;
    byte maxOpsPerInstruction = 1;
    byte lineBase = -3;
    byte lineRange = 12;
    byte opCodeBase = 13;

    final DebugLineContext context =
        createContext(minInstructionLength, maxOpsPerInstruction, lineBase, lineRange, opCodeBase);

    DebugLineOpcode opcode = new SpecialOpcode(opNum);

    long expectedLine = context.reg.line + expectedLineAdvance;
    long expectedAddr = context.reg.address + expectedOperationAdvance;

    Assert.assertTrue(
        opcode.process(
            context, _mockReader)); // This opcode does not actually read from the reader.
    Assert.assertEquals("Unexpected line number", expectedLine, context.reg.line);
    Assert.assertEquals("Unexpected address", expectedAddr, context.reg.address);
  }

  // Calculate an opcode based on the DWARF spec.
  private byte calculateSpecialOpcode(
      long lineIncrement,
      long addressIncrement,
      int minInstructionLength,
      int maxOpsPerInstruction,
      int lineBase,
      int lineRange,
      int opcodeBase) {
    long operationAdvance = addressIncrement / minInstructionLength;
    operationAdvance *= maxOpsPerInstruction;
    return (byte)
        (((lineIncrement - lineBase) + (lineRange * operationAdvance) + opcodeBase) & 0xff);
  }

  private DebugLineContext createContext(
      byte minInstructionLength,
      byte maxOpsPerInstruction,
      byte lineBase,
      byte lineRange,
      byte opcodeBase) {
    long unitLength = 0;
    int version = 0;
    long headerLength = 0;
    boolean isStmt = true;
    byte[] opcodeLengths = new byte[0];
    DebugLineHeader header =
        new DebugLineHeader(
            unitLength,
            version,
            headerLength,
            minInstructionLength,
            maxOpsPerInstruction,
            isStmt,
            lineBase,
            lineRange,
            opcodeBase,
            opcodeLengths);
    // Set up header information, initialize registers, and set offsetSize to 4 (32-bit).
    return new DebugLineContext(header, new DebugLineRegisters(true), ADDRESS_SIZE);
  }

  private int calculateAddressIncrement(
      int opcode, int opcodeBase, int lineRange, int minInstructionLength) {
    int adjustedOpcode = opcode - OPCODE_BASE;
    return (adjustedOpcode / lineRange) * minInstructionLength;
  }
}
