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

/**
 * Processes the <code>DW_LNS_advance_pc</code> standard opcode for the line number state machine.
 */
public class StandardOpcodeAdvancePC implements DebugLineOpcode {

  /**
   * Takes a single unsigned LEB128 operand, multiplies it by the
   * <code>minimum_instruction_length</code> field of the prologue, and adds the result to the
   * <code>address</code> register of the state machine.
   */
  @Override
  public boolean process(DebugLineContext context, ByteReader dataReader) throws IOException {
    int pcAdv = dataReader.readULEB128() * context.header.minInstructionLength;
    context.reg.address += pcAdv;
    return false;
  }
}
