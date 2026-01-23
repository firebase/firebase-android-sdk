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
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * State machine which will read the <code>debug_line</code> statement program to determine the
 * matrix of mappings for memory addresses and their corresponding file and line number information.
 */
public class DebugLineStateMachine {

  private static final int EXTENDED_OPCODE = 0; // Marker for an extended opcode.

  private static final DebugLineOpcode[] STANDARD_OPCODES = {
    null,
    new StandardOpcodeCopy(),
    new StandardOpcodeAdvancePC(),
    new StandardOpcodeAdvanceLine(),
    new StandardOpcodeSetFile(),
    new StandardOpcodeSetColumn(),
    new StandardOpcodeNegateStatement(),
    new StandardOpcodeSetBasicBlock(),
    new StandardOpcodeConstAddPC(),
    new StandardOpcodeFixedAdvancePC(),
    new StandardOpcodeSetPrologueEnd(),
    new StandardOpcodeSetEpilogueBegin(),
    new StandardOpcodeSetIsa()
  };

  private static final DebugLineOpcode[] EXTENDED_OPCODES = {
    null,
    new ExtendedOpcodeEndSequence(),
    new ExtendedOpcodeSetAddress(),
    new ExtendedOpcodeDefineFile(),
    new ExtendedOpcodeSetDiscriminator()
  };

  private boolean _dwarf64 = false;

  /**
   * Run the state machine for a single compilation unit. The state machine expects the provided
   * ByteReader's offset to be set to the beginning of a line number program header, and reads as
   * many bytes as specified in the line number program header's unit length field.
   *
   * @param reader the <code>ByteReader</code> which will provide the <code>debug_line</code> data.
   * @param pointerSize the pointer size of the compilation unit being read.
   * @return the matrix of address/file/line number information.
   * @throws IOException if a problem occurs reading from the <code>ByteReader</code>.
   */
  public List<DebugLineEntry> runFromCurrentOffset(ByteReader reader, int pointerSize)
      throws IOException, DwarfException {
    final long unitLength = readInitialLength(reader);
    final long endOffset = reader.getCurrentOffset() + unitLength;
    DebugLineContext context = configureContext(reader, unitLength, pointerSize);
    return readCompilationUnit(context, reader, endOffset);
  }

  /**
   * Run the state machine for a single compilation unit at the given index. The state machine
   * expects the provided ByteReader's offset to be set to the beginning offset of a .debug_line
   * section, and will attempt to seek to the beginning of the line number program header at the
   * given index before running the line number program at that offset.
   *
   * @param reader the <code>ByteReader</code> which will provide the <code>debug_line</code> data.
   * @param index the index of the line number program to read from the .debug_line section.
   * @param endOffset the address of the end of the .debug_line section.
   * @param pointerSize the pointer size of the compilation unit being read.
   * @return the matrix of address/file/line number information.
   * @throws IOException if a problem occurs reading from the <code>ByteReader</code>.
   * @throws DwarfException if a line number program cannot be found for the given index.
   */
  public List<DebugLineEntry> runForIndex(
      ByteReader reader, int index, long endOffset, int pointerSize)
      throws IOException, DwarfException {
    for (int i = 0; i < index; ++i) {
      if (reader.getCurrentOffset() >= endOffset) {
        throw new DwarfException("Unable to set appropriate line number section offset");
      }
      reader.seek(readInitialLength(reader) + reader.getCurrentOffset());
    }
    return runFromCurrentOffset(reader, pointerSize);
  }

  private DebugLineContext configureContext(ByteReader dataReader, long unitLength, int pointerSize)
      throws IOException {
    int version = dataReader.readInt(2);
    long headerLength = dataReader.readLong(_dwarf64 ? 8 : 4);
    byte minInstructionLength = dataReader.readByte();
    byte maxOperationsPerInstruction = (version >= 4) ? dataReader.readByte() : 1;
    boolean defaultIsStatement = (dataReader.readByte() != 0);
    byte lineBase = dataReader.readByte();
    byte lineRange = dataReader.readByte();
    byte opcodeBase = dataReader.readByte();

    // 1 through opcodeBase-1 are standard opcodes.
    byte[] standardOpcodeLengths = new byte[opcodeBase];
    for (int i = 1; i < opcodeBase; ++i) {
      standardOpcodeLengths[i] = dataReader.readByte();
    }

    DebugLineHeader header =
        new DebugLineHeader(
            unitLength,
            version,
            headerLength,
            minInstructionLength,
            maxOperationsPerInstruction,
            defaultIsStatement,
            lineBase,
            lineRange,
            opcodeBase,
            standardOpcodeLengths);

    DebugLineRegisters registers = new DebugLineRegisters(defaultIsStatement);

    DebugLineContext context = new DebugLineContext(header, registers, pointerSize);

    // For directories and files, each full sequence is terminated by a null byte, which equates
    // to an empty string. Therefore, read each sequence until we run into an empty string.

    String directory = dataReader.readNullTerminatedString(Charsets.UTF_8);
    while (directory.length() > 0) {
      context.defineDirectory(directory);
      directory = dataReader.readNullTerminatedString(Charsets.UTF_8);
    }

    String fileName = dataReader.readNullTerminatedString(Charsets.UTF_8);
    while (fileName.length() > 0) {
      int dirIndex = dataReader.readULEB128(); // Directory index
      int modTime = dataReader.readULEB128(); // Modification time.
      int length = dataReader.readULEB128(); // file length.
      context.defineFile(fileName, dirIndex, modTime, length);

      fileName = dataReader.readNullTerminatedString(Charsets.UTF_8);
    }

    return context;
  }

  private static List<DebugLineEntry> readCompilationUnit(
      DebugLineContext context, ByteReader dataReader, long endOffset)
      throws IOException, DwarfException {
    List<DebugLineEntry> data = new LinkedList<DebugLineEntry>();
    while (dataReader.getCurrentOffset() < endOffset) {
      boolean addRow = processOpcode(context, dataReader);
      if (addRow) {
        long address = context.reg.address;
        String file = context.getFileInfo(context.reg.file).name;
        long lineNumber = context.reg.line;
        data.add(new DebugLineEntry(address, file, lineNumber));
      }
      if (context.reg.isEndSequence) {
        context.reg.reset();
      }
    }
    return data;
  }

  /**
   * Read and process the next opcode in the sequence.
   *
   * <p>If the opcode is 0, we have an extended opcode, so read the next unsigned LEB128 value as
   * the opcode length, and then that number of bytes for the extended opcode, running the
   * appropriate extended opcode instructions for that opcode.
   *
   * <p>If the opcode is < opcodeBase, we have a standard opcode, so run the appropriate standard
   * opcode instructions for that opcode.
   *
   * <p>If the opcode is opcodeBase or higher, we have a special opcode, so run the appropriate
   * special opcode instructions for that opcode.
   */
  private static boolean processOpcode(DebugLineContext context, ByteReader dataReader)
      throws IOException, DwarfException {
    DebugLineOpcode debugLineOpcode;
    int opcode = dataReader.readInt(1);
    if (opcode < 0) {
      throw new DwarfException("Could not process opcode " + opcode);
    } else if (opcode >= context.header.opcodeBase) { // Special opcode.
      // Could optimize this by caching the special opcode instances in a 1-255 array.
      debugLineOpcode = new SpecialOpcode(opcode);
    } else if (opcode == EXTENDED_OPCODE) {
      int length = dataReader.readULEB128(); // Length is currently unnecessary for our purposes.
      int exOpcode = dataReader.readInt(1);
      debugLineOpcode = getOpcode(exOpcode, EXTENDED_OPCODES);
    } else {
      debugLineOpcode = getOpcode(opcode, STANDARD_OPCODES);
    }
    return debugLineOpcode.process(context, dataReader);
  }

  /**
   * In the 32-bit DWARF format, an initial length field is an unsigned 32-bit integer (which must
   * be less than <code>0xfffffff0</code>); in the 64-bit DWARF format, an initial length field is
   * 96 bits in size, and has two parts:
   *
   * <ol>
   *   <li>The first 32-bits have the value 0xffffffff.
   *   <li>The following 64-bits contain the actual length represented as an unsigned 64-bit
   *       integer.
   * </ol>
   */
  private long readInitialLength(ByteReader dataReader) throws IOException {
    long initialLength = dataReader.readLong(4);
    if (initialLength == 0xffffffff) {
      // Offset size is 64-bit. Read the next 8 bytes to get the actual length.
      _dwarf64 = true;
      initialLength = dataReader.readLong(8);
    }
    return initialLength;
  }

  private static DebugLineOpcode getOpcode(int opcode, DebugLineOpcode[] opcodes)
      throws DwarfException {
    if (opcode < 0 || opcode >= opcodes.length) {
      throw new DwarfException("Unknown opcode: " + opcode);
    }
    return opcodes[opcode];
  }
}
