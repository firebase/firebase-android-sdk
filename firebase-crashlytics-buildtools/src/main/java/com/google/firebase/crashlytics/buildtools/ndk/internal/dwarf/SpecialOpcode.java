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

import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;

/** Processes a special opcode for the line number state machine. */
public class SpecialOpcode implements DebugLineOpcode {

  private final int _opcode;

  /**
   * Create a new special opcode from the opcode given.
   *
   * @param opcode the opcode value
   */
  public SpecialOpcode(int opcode) {
    _opcode = opcode;
  }

  /**
   * Each 1-byte special opcode has the following effect on the state machine:
   *
   * <ol>
   *   <li>Add a signed integer to the line register.
   *   <li>Multiply an unsigned integer by the {@code minimum_instruction_length} field of the
   *       statement program prologue and add the result to the address register.
   *   <li>Append a row to the matrix using the current values of the state machine registers.
   *   <li>Set the {@code basic_block} register to {@code false}.
   * </ol>
   *
   * <p>All of the special opcodes do those same four things; they differ from one another only in
   * what values they add to the line and address registers.
   *
   * <p>The calculation here is described in <a href="http://dwarfstd.org/doc/DWARF4.pdf">DWARF
   * Debugging Information Format, Version 4 (June 10, 2010)</a> &sect;6.2.5.1 "Special Opcodes".
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    final int adjustedOpcode = _opcode - context.header.opcodeBase;
    final int operationAdvance = adjustedOpcode / context.header.lineRange;
    final int addressIncrement =
        context.header.minInstructionLength
            * ((context.reg.opIndex + operationAdvance)
                / context.header.maximumOperationsPerInstruction);
    final int lineIncrement = context.header.lineBase + (adjustedOpcode % context.header.lineRange);
    final int opIndexIncrement = context.reg.opIndex + operationAdvance;

    context.reg.opIndex = opIndexIncrement % context.header.maximumOperationsPerInstruction;
    context.reg.address += addressIncrement;
    context.reg.line += lineIncrement;
    context.reg.isBasicBlock = false;
    return true;
  }
}
