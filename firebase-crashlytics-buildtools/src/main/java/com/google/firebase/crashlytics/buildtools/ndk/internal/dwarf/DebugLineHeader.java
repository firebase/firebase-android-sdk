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

/**
 * The header (or prologue) information read out of the <code>debug_line</code> data
 * for a single compilation unit.
 */
public class DebugLineHeader {

  /**
   * The size in bytes of the statement information for this compilation unit (not including the field itself).
   */
  public final long unitLength;

  /**
   * Version identifier for the statement information format.
   */
  public final int version;

  /**
   * The number of bytes following the this field to the beginning of the first byte of the statement program itself.
   */
  public final long headerLength;

  /**
   * The size in bytes of the smallest target machine instruction. Line number program opcodes
   * that alter the address and op_index registers use this and
   * maximum_operations_per_instruction in their calculations.
   */
  public final byte minInstructionLength;

  /**
   * The maximum number of individual operations that may be encoded in an instruction. Line
   * number program opcodes that alter the address and op_index registers use this and
   * minimum_instruction_length in their calculations.
   */
  public final byte maximumOperationsPerInstruction;

  /**
   * The initial value of the <code>is_stmt</code> register.
   */
  public final boolean defaultIsStatement;

  /**
   * This value affects the meaning of the special opcodes.
   */
  public final byte lineBase;

  /**
   * This value affects the meaning of the special opcodes.
   */
  public final byte lineRange;

  /**
   * The number assigned to the first special opcode.
   */
  public final byte opcodeBase;

  /**
   * This array specifies the number of LEB128 operands for each of the standard opcodes. The
   * first element of the array corresponds to the opcode whose value is 1, and the last element
   * corresponds to the opcode whose value is <code>opcode_base - 1</code>.
   */
  public final byte[] standardOpcodeLengths;

  public DebugLineHeader(
      long unitLength,
      int version,
      long headerLength,
      byte minInstructionLength,
      byte maximumOperationsPerInstruction,
      boolean defaultIsStatement,
      byte lineBase,
      byte lineRange,
      byte opcodeBase,
      byte[] standardOpcodeLengths) {
    this.unitLength = unitLength;
    this.version = version;
    this.headerLength = headerLength;
    this.minInstructionLength = minInstructionLength;
    this.maximumOperationsPerInstruction = maximumOperationsPerInstruction;
    this.defaultIsStatement = defaultIsStatement;
    this.lineBase = lineBase;
    this.lineRange = lineRange;
    this.opcodeBase = opcodeBase;
    this.standardOpcodeLengths = standardOpcodeLengths;
  }
}
